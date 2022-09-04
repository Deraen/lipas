(ns lipas.backend.gis
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [geo.io :as gio]
   [geo.jts :as jts]
   [geo.spatial :as geo])
  (:import
   [org.locationtech.geowave.analytic GeometryHullTool]
   [org.locationtech.geowave.analytic.distance CoordinateEuclideanDistanceFn]
   [org.locationtech.jts.algorithm ConvexHull]
   [org.locationtech.jts.geom.util GeometryCombiner]
   [org.locationtech.jts.operation.buffer BufferOp]
   [org.locationtech.jts.operation.distance DistanceOp]
   [org.locationtech.jts.simplify DouglasPeuckerSimplifier]))

(def srid 4326) ;; WGS84
(def tm35fin-srid 3067)
(def default-simplify-tolerance 0.001) ; ~111m

(def hull-tool
  (doto (GeometryHullTool.)
    (.setDistanceFnForCoordinate (CoordinateEuclideanDistanceFn.))))

(defn- simplify-geom [m tolerance]
  (update m :geometry #(-> %
                           (DouglasPeuckerSimplifier/simplify tolerance)
                           (jts/set-srid srid))))

(defn- dummy-props [m]
  (assoc m :properties {}))

(defn point? [fcoll]
  (= "Point" (-> fcoll :features first :geometry :type)))

(defn simplify
  "Returns simplified version of `m` where `m` is a map representing
  GeoJSON FeatureCollection."
  ([m] (simplify m default-simplify-tolerance))
  ([m tolerance]
   (-> m
       (update :features #(map dummy-props %))
       json/encode
       (gio/read-geojson srid)
       (->> (map #(simplify-geom % tolerance)))
       gio/to-geojson-feature-collection
       (json/decode keyword))))

(defn centroid
  "Returns centroids of `m` where `m` is a map representing
  GeoJSON FeatureCollection."
  [m]
  (-> m
      (update :features #(map dummy-props %))
      json/encode
      (gio/read-geojson srid)
      (->> (map :geometry))
      jts/geometry-collection
      jts/centroid
      (jts/set-srid srid)
      gio/to-geojson
      (json/decode keyword)))

(defn wgs84->tm35fin [[lon lat]]
  (let [transformed (jts/transform-geom (jts/point lat lon) srid tm35fin-srid)]
    {:easting (.getX transformed) :northing (.getY transformed)}))

(defn wgs84->tm35fin-no-wrap [[lon lat]]
  (let [transformed (jts/transform-geom (jts/point lat lon) srid tm35fin-srid)]
    [(.getX transformed) (.getY transformed)]))

(defn epsg3067-point->envelope [[e n] delta]
  [[(- e delta) (+ n delta)] [(+ e delta) (- n delta)]])

(defn epsg3067-point->wgs84-envelope [coords delta]
  (let [envelope (epsg3067-point->envelope coords delta)]
    (mapv (fn [[lon lat]]
            (let [transformed (jts/transform-geom (jts/point lat lon) tm35fin-srid srid)]
              [(.getX transformed) (.getY transformed)]))
          envelope)))

(defn ->jts-geom [f]
  (-> f (update :features #(map dummy-props %))
      json/encode
      (gio/read-geojson srid)
      (->> (map :geometry))
      (GeometryCombiner.)
      (.combine)))

(defn wgs84wkt->tm35fin-geom [s]
  (-> s
      gio/read-wkt
      (jts/transform-geom srid tm35fin-srid)))

(defn transform-crs
  ([geom] (transform-crs geom srid tm35fin-srid))
  ([geom from-crs to-crs]
   (jts/transform-geom geom from-crs to-crs)))

(defn shortest-distance [g1 g2]
  (DistanceOp/distance g1 g2))

(def ->point geo/point)

(defn distance-point [p1 p2]
  (geo/distance p1 p2))

(defn nearest-points [g1 g2]
  (DistanceOp/nearestPoints g1 g2))

(defn ->flat-coords [fcoll]
  (->> fcoll
      :features
      (map :geometry)
      (reduce
       (fn [res g]
         (case (:type g)

           "Point"
           (conj res (:coordinates g))

           "LineString"
           (into res (:coordinates g))

           ("Polygon" "MultiLineString")
           (into res (->> g :coordinates (filter seq) (mapcat identity) (filter seq)))

           ("MultiPolygon")
           (into res (->> g :coordinates  (mapcat identity) (mapcat identity) (filter seq)))))
       [])
      (into [] #_(distinct))))

(defn contains-coords? [fcoll]
  (boolean (seq (->flat-coords fcoll))))

(defn ->jts-point [lon lat]
  (geo/jts-point lat lon))

(defn ->jts-multi-point [coords]
  (->> coords
       (map #(geo/jts-point (second %) (first %)))
       (GeometryCombiner.)
       (.combine)))

(defn wkt-point->coords [s]
  (let [coord (-> s
                  gio/read-wkt
                  (.getCoordinate))]
    [(.getX coord) (.getY coord)]))

(defn ->wkt [g]
  (format "POINT (%s %s)" (.getX g) (.getY g)))

(defn calc-buffer [fcoll distance-m]
  (-> fcoll
      (->jts-geom)
      (jts/transform-geom srid tm35fin-srid)
      (BufferOp.)
      (.getResultGeometry distance-m)
      (jts/transform-geom tm35fin-srid srid)
      gio/to-geojson
      (json/decode keyword)))

(defn concave-hull [fcoll]
  (let [points (-> fcoll ->flat-coords ->jts-multi-point)
        convex (-> points ConvexHull. .getConvexHull)]
    (.concaveHull hull-tool convex (into [] (.getCoordinates points)))))

(defn ->single-linestring-coords [fcoll]
  (-> fcoll
      concave-hull
      .getExteriorRing
      .getCoordinates
      (->> (map #(vector (.getX %) (.getY %))))))

(defn ->coord-pair-strs [fcoll]
  (if (point? fcoll)
    [(-> fcoll :features first :geometry :coordinates
         (->> (str/join ",")))]
    (-> fcoll
        ->single-linestring-coords
        (->> (map #(str/join "," %))))))

(defn dedupe-polygon-coords
  [fcoll]
  (update fcoll :features
          (fn [fs]
            (map
             (fn [f]
               (update-in f [:geometry :coordinates] #(map dedupe %)))
             fs))))



(defn ->fcoll [features]
  {:type     "FeatureCollection"
   :features (mapv #(update % :properties (fnil identity {})) features)})

(defn ->feature [geom]
  {:type "Feature" :geometry geom})

(comment

  (wgs84->tm35fin [23.8259457479965 61.4952794263427])
  (wkt-point->coords "POINT (29.1946713328528 63.1707254363858)")

  (def test-point
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [25.720539797408946,
                      62.62057217751676]}}]})

  (def test-point2
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [26.720539797408946,
                      61.62057217751676]}}]})

  (distance-point (-> test-point :features first :geometry)
                  (-> test-point2 :features first :geometry))

  (def buff (calc-buffer test-point 10))

  (json/encode
   {:type "FeatureCollection"
    :features
    [{:type "Feature"
      :geometry buff}]})

  (centroid test-point)

  (def test-route
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type "LineString",
        :coordinates
        [[26.2436753445903, 63.9531598143881],
         [26.4505514903968, 63.9127506671744]]}},
      {:type "Feature"
       :geometry
       {:type "LineString",
        :coordinates
        [[26.2436550567509, 63.9531552213109],
         [25.7583312263512, 63.9746827436437]]}}]})

  (-> test-route
      ->jts-geom
      )

  (->single-linestring-coords test-route)

  (def mp (->> test-route
        ->flat-coords
        ->jts-multi-point
        ))

  (def coords (.getCoordinates mp))

  (into [] coords)

  (def convex (.getConvexHull (ConvexHull. mp)))

  (.getSRID convex)


  (.setDistanceFnForCoordinate hull-tool )
  (.getDistanceFnForCoordinate hull-tool)

  hull-tool

  (def concave (.concaveHull hull-tool convex (into [] coords)))

  concave

  (->> [{:geometry concave :properties {}}]
       gio/to-geojson-feature-collection
       (spit "/Users/tipo/Desktop/concave.json"))

  (->> [{:geometry convex :properties {}}]
       gio/to-geojson-feature-collection
       (spit "/Users/tipo/Desktop/convex.json"))

  (->> test-route
       json/encode
       (spit "/Users/tipo/Desktop/coriginal.json"))

  (def test-polygon
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :properties {}
       :geometry
       {:type "Polygon",
        :coordinates
        [[[26.2436753445903, 63.9531598143881],
          [26.4505514903968, 63.9127506671744]
          [26.2436753445903, 63.9531598143881]]]}},
      {:type "Feature",
       :properties {}
       :geometry
       {:type "Polygon",
        :coordinates
        [[[26.2436550567509, 63.9531552213109],
          [25.7583312263512, 63.9746827436437]
          [26.2436550567509, 63.9531552213109]]]}}]})

  (require '[lipas.backend.osrm :as osrm])

  (osrm/resolve-sources test-polygon)

  (->> test-polygon
       ->jts-geom)

  (->> test-polygon
       concave-hull
       )

  (centroid test-route)

  (def test-point2
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [19.720539797408946,
                      65.62057217751676]}}]})

  (calc-buffer test-point2 100)

  (->fcoll [(->feature (calc-buffer test-point2 100))])

  (point? test-point)
  (point? test-polygon)

  (require '[lipas.backend.osrm :as osrm])
  (map osrm/resolve-sources [test-point test-route test-polygon])

  (shortest-distance test-point test-route)

  (->flat-coords test-polygon)

  (def p100 [25.742855072021484
             62.240251303552284])

  (def p101 (wgs84->tm35fin p100))

  (def envelope
    (epsg3067-point->wgs84-envelope [(:easting p101) (:northing p101)] 125))

  (def test-polygon-empty
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :properties {}
       :geometry
       {:type "Polygon",
        :coordinates
        [[[],
          []
          []]]}},
      {:type "Feature",
       :properties {}
       :geometry
       {:type "Polygon",
        :coordinates
        [[[],
          []
          []]]}}]})

  (->flat-coords test-polygon-empty)

  (contains-coords? test-point)
  (contains-coords? test-route)
  (contains-coords? test-polygon)
  (contains-coords? test-polygon-empty)

  )
