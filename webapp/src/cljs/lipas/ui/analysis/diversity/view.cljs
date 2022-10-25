(ns lipas.ui.analysis.diversity.view
  (:require
   [clojure.string :as str]
   [lipas.ui.analysis.diversity.events :as events]
   [lipas.ui.analysis.diversity.subs :as subs]
   #_[lipas.ui.charts :as charts]
   #_[lipas.ui.components :as lui]
   #_[lipas.ui.map.events :as map-events]
   #_[lipas.ui.map.subs :as map-subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.components :as lui]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(def ^js Slider (.-default (.-Slider js/rcslider)))

(def import-formats [".zip" ".kml" ".json" ".geojson"])
(def import-formats-str (str/join " " import-formats))
(def geom-type "Polygon")

(defn helper [{:keys [label tooltip]}]
  [mui/tooltip {:title tooltip}
   [mui/link
    {:style     {:font-family "Lato" :font-size "0.9em" :margin "0.5em"}
     :underline "always"}
    label]])

(defn analysis-area-selector []
  (let [tr         (<== [:lipas.ui.subs/translator])
        candidates (<== [::subs/analysis-candidates-table-rows])
        headers    (<== [::subs/analysis-candidates-table-headers])
        loading?   (<== [::subs/loading?])]

    #_[:> js/materialIcons.FileUpload props]

    [mui/grid {:container true :spacing 16}

     [mui/grid {:item true :xs 12}
      [lui/expansion-panel {:label            "Info"
                            :default-expanded false}
       [mui/paper
        {:style
         {:padding "1em" :background-color "#f5e642"}}
        [mui/typography {:variant "body1" :paragraph true}
         (tr :analysis/diversity-help1)]
        [mui/typography {:variant "body1" :paragraph true}
         (tr :analysis/diversity-help2)]
        [mui/typography {:variant "body1" :paragraph true}
         (tr :analysis/description3)]
        [mui/typography {:variant "body1" :paragraph true}
         (tr :analysis/diversity-help3)]]]]

     [mui/grid {:item true :xs 12}

      [lui/expansion-panel {:label            "Käytä postinumeroalueita"
                            :default-expanded true}
       [lui/city-selector-single
        {:on-change #(==> [::events/fetch-postal-code-areas %])}]]

      [lui/expansion-panel {:label            "Tuo omat alueet"
                            :default-expanded false}
       [:<>
        [mui/grid {:item true :xs 12}
         [mui/typography {:variant "body2"}
          (str (tr :map.import/headline) " (polygon)")]]
        [mui/grid {:item true}
         [:input
          {:type      "file"
           :accept    (str/join "," import-formats)
           :on-change #(==> [::events/load-geoms-from-file
                             (-> % .-target .-files)
                             geom-type])}]]
        [mui/grid {:item true}
         [mui/typography {:inline true} (str (tr :help/headline) ":")]
         [helper {:label "Shapefile" :tooltip (tr :map.import/shapefile)}]
         [helper {:label "GeoJSON" :tooltip (tr :map.import/geoJSON)}]
         [helper {:label "KML" :tooltip (tr :map.import/kml)}]]]]

      (when (seq candidates)
        [:<>
         [mui/grid {:item true :xs 12 :style {:margin-top "0.5em"}}
          [mui/button {:on-click  #(==> [::events/calc-all-diversity-indices])}
           [mui/icon {:style {:margin-right "0.25em"}} "refresh"]
           "Laske kaikki"]]
         [lui/table-v2
          {:headers            headers
           :items              candidates
           :in-progress?       loading?
           :action-icon        "refresh"
           :action-label       "Laske monipuolisuus"
           :on-select          #(==> [::events/calc-diversity-indices %])
           #_#_:allow-editing? (constantly true)
           #_#_:multi-select?  true}]])]]))

(defn seq-indexed [coll]
  (map-indexed vector coll))

(defn category-builder []
  (let [categories (<== [::subs/categories])]
    [mui/table
     [mui/table-head
      [mui/table-row
       [mui/table-cell "Kategoria"]
       [mui/table-cell "Liikuntapaikkatyypit"]]]
     (into [mui/table-body]
           (for [[idx category] (seq-indexed categories)]
             [mui/table-row

              ;; Category name
              [mui/table-cell {:style {:width "40%"}}
               [lui/text-field
                {:full-width  true
                 :value       (:name category)
                 :placeholder "Uusi kategoria"
                 :on-change   #(==> [::events/set-category-name idx %])}]]

              ;; Types + delete
              [mui/table-cell {:style {:width "60%"}}
               [mui/grid {:container true :align-items "center"}

                ;; Type selector
                [mui/grid {:item true :xs 8}
                 [lui/type-selector
                  {:value     (:type-codes category)
                   :on-change #(==> [::events/set-category-type-codes idx %])}]]

                ;; Factor selector
                [mui/grid {:item true :xs 2}
                 [lui/select
                  {:label     "Kerroin"
                   :items     (map (fn [n] {:label n :value n}) [1 2 3 4 5])
                   :on-change #(==> [::events/set-category-factor idx %])
                   :value     (:factor category)}]]

                ;; Delete category button
                [mui/grid {:item true :xs 2}
                 [mui/tooltip {:title "Poista kategoria"}
                  [mui/icon-button
                   {:on-click #(==> [::events/delete-category idx])}
                   [mui/icon "delete"]]]]]]]))]))

(defn overlay-switches []
  (let [tr                   (<== [:lipas.ui.subs/translator])
        show-diversity-area? (<== [:lipas.ui.map.subs/overlay-visible? :diversity-area])
        show-diversity-grid? (<== [:lipas.ui.map.subs/overlay-visible? :diversity-grid])
        show-sports-sites?   (<== [:lipas.ui.map.subs/overlay-visible? :vectors])]
    [mui/grid {:container true :style {:padding "1em"}}

     ;; Sports facilities
     [mui/grid {:item true}
      [lui/switch
       {:label     (tr :sport/headline)
        :value     show-sports-sites?
        :on-change #(==> [:lipas.ui.map.events/set-overlay % :vectors])}]]

     ;; Diversity area
     [mui/grid {:item true}
      [lui/switch
       {:label     (tr :analysis/analysis-areas)
        :value     show-diversity-area?
        :on-change #(==> [:lipas.ui.map.events/set-overlay % :diversity-area])}]]

     ;; Diversity grid
     [mui/grid {:item true}
      [lui/switch
       {:label     (tr :analysis/diversity-grid)
        :value     show-diversity-grid?
        :on-change #(==> [:lipas.ui.map.events/set-overlay % :diversity-grid])}]]]))

(defn settings []
  (let [tr                (<== [:lipas.ui.subs/translator])
        max-distance-m    (<== [::subs/max-distance-m])
        selected-preset   (<== [::subs/selected-category-preset])
        category-presets  (<== [::subs/category-presets])
        summer-enabled?   (<== [::subs/seasonality-enabled? "summer"])
        winter-enabled?   (<== [::subs/seasonality-enabled? "winter"])
        all-year-enabled? (<== [::subs/seasonality-enabled? "all-year"])]
    [:<>

     [mui/grid {:container true}

      ;; What's visible on map
      [mui/grid {:item true :xs 12}
       [lui/expansion-panel
        {:label            (tr :analysis/settings-map)
         :default-expanded false}

        ;; Switches
        [overlay-switches]]]

      ;; Categories
      [mui/grid {:item true :xs 12}
       [lui/expansion-panel
        {:label            (tr :analysis/categories)
         :default-expanded false}
        [mui/grid {:container true :spacing 2}

         ;; Helper text
         [mui/grid {:item true :xs 12 :style {:margin-bottom "0.5em"}}
          [mui/paper {:style {:padding "1em" :background-color "#f5e642"}}
           [mui/typography {:variant "body1" :paragraph false}
            (tr :analysis/categories-help)]]]

         ;; Preset category selector
         [mui/grid {:item true :xs 12 :md 12}
          [mui/form-group {:style {:padding "0.5em" :margin-bottom "0.5em"}}
           [lui/select
            {:value     selected-preset
             :on-change #(==> [::events/select-category-preset %])
             :label     "Valmiit luokittelut"
             :items     category-presets}]]]

         ;; Seasonality switches
         [mui/grid {:item true :xs 12 :md 12}
          [mui/grid {:container true :style {:padding "0.5em"}}

           [mui/grid {:item true :xs 12}
            [mui/typography {:variant "caption"} "Rajaukset"]]

           ;; Summer season filter
           [mui/grid {:item true}
            [lui/switch
             {:label     "Käytössä kesällä"
              :value     summer-enabled?
              :on-change #(==> [::events/toggle-seasonality "summer" %])}]]

           ;; Winter season filter
           [mui/grid {:item true}
            [lui/switch
             {:label     "Käytössä talvella"
              :value winter-enabled?
              :on-change #(==> [::events/toggle-seasonality "winter" %])}]]

           ;; All-year filter
           [mui/grid {:item true}
            [lui/switch
             {:label     "Käytössä vuoden ympäri"
              :value all-year-enabled?
              :on-change #(==> [::events/toggle-seasonality "all-year" %])}]]]]

         ;; Add new category button
         [mui/grid {:item true :xs 12 :md 6}
          [mui/button
           {:variant  "default"
            :on-click #(==> [::events/add-new-category])}
           "Uusi kategoria"]]

         ;; Reset default categories button
         [mui/grid {:item true :xs 12 :md 6}
          [mui/button
           {:variant  "default"
            :on-click #(==> [::events/select-category-preset :default])}
           "Palauta oletuskategoriat"]]

         ;; Category builder
         [mui/grid {:item true :xs 12 :style {:margin-top "2em"}}
          [category-builder]]]]]

      ;; Distances
      [mui/grid {:item true :xs 12}
       [lui/expansion-panel
        {:label            (tr :analysis/distance)
         :default-expanded false}
        [mui/grid {:container true :spacing 2}

         ;; Helper text
         [mui/grid {:item true :xs 12 :style {:margin-bottom "1em"}}
          [mui/paper {:style {:padding "1em" :background-color "#f5e642"}}
           [mui/typography {:variant "body1" :paragraph false}
            "Monipuolisuus lasketaan jokaiselle väestöruudulle siten, että monipuolisuuteen vaikuttavat ne liikuntapaikat, jotka ovat annetun metrimäärän kävelyetäisyydellä tieverkkoa pitkin väestöruudun keskipisteestä."]]]

         ;; Distance zones selector
         [mui/grid {:item true :xs 12 :style {:margin-top    "1em"
                                              :margin-left   "1em"
                                              :margin-right  "1em"
                                              :margin-bottom "5em"}}

          (let [min 500 max 1500 step 100]
            [:> Slider
             {:min       min
              :max       max
              :step      step
              :value     max-distance-m
              :dots      true
              :marks     (into {}
                               (map (juxt identity #(str % "m")))
                               (range min (+ max step) step))
              :style     {:font-family "Lato, sans-serif"}
              :on-change #(==> [::events/set-max-distance-m %])}])]]]]]]))

(defn export []
  (let [selected-format "geojson"]
    [mui/grid {:container true :spacing 16}

     ;; Format selector
     [mui/grid {:item true :xs 12}
      [lui/select
       {:label "Formaatti"
        :items [{:label "GeoJSON" :value "geojson"}]
        :value selected-format}]]

     ;; Export aggregate results
     [mui/grid {:item true :xs 12}

      [mui/button
       {:on-click #(==> [::events/export-aggs selected-format])}
       "Lataa alueet"]]

     ;; Export grid
     [mui/grid {:item true :xs 12}
      [mui/button
       {:on-click #(==> [::events/export-grid selected-format])}
       "Lataa ruudukko"]]]))

(defn view []
  (let [tr           (<== [:lipas.ui.subs/translator])
        selected-tab (<== [::subs/selected-analysis-tab])]
    [:<>
     [mui/grid {:container true :spacing 16}

      #_[mui/grid {:item true :xs 12}
       [overlay-switches]]

      [mui/grid {:item true :xs 12}
       [mui/tabs {:value      selected-tab
                  :on-change  #(==> [::events/select-analysis-tab %2])
                  :style      {:margin-bottom "1em" :margin-top "1em"}
                  :text-color "secondary"}
        [mui/tab {:label "Analyysialueet" :value "analysis-area"}]
        [mui/tab {:label (tr :analysis/settings) :value "settings"}]
        [mui/tab {:label (tr :analysis/results) :value "export"}]]]

      [mui/grid {:item true :xs 12}
       (when (= selected-tab "analysis-area")
         [analysis-area-selector])

       (when (= selected-tab "settings")
         [settings])

       (when (= selected-tab "export")
         [export])]]]))
