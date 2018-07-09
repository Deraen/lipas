(ns lipas.seed
  (:require [lipas.backend.system :as backend]
            [lipas.backend.core :as core]
            [taoensso.timbre :as log]))

(def jh-demo
  {:email    "jh@lipas.fi"
   :username "jhdemo"
   :password "jaahalli"
   :user-data
   {:firstname           "Jää"
    :lastname            "Halli"
    :permissions-request "Haluan oikeudet päivittää Jyväskylän
             kilpajäähallin tietoja."}})

(def uh-demo
  {:email    "uh@lipas.fi"
   :username "uhdemo"
   :password "uimahalli"
   :user-data
   {:firstname           "Uima"
    :lastname            "Halli"
    :permissions-request "Haluan oikeudet päivittää Äänekosken
    Vesivelhon tietoja."}})

(defn -main [& args]
  (let [system (backend/start-system!)]
    (try
      (log/info "Seeding demo users 'jhdemo' and 'uhdemo'")
      (core/add-user (:db system) jh-demo)
      (core/add-user (:db system) uh-demo)
      (log/info "Seeding done!")
      (finally (backend/stop-system! system)))))
