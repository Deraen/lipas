(ns lipas.ui.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.events :as events]
            [lipas.ui.front-page.views :as front-page]
            [lipas.ui.help.views :as help]
            [lipas.ui.ice-stadiums.views :as ice-stadiums]
            [lipas.ui.login.views :as login]
            [lipas.ui.mui :as mui]
            [lipas.ui.navbar :as nav]
            [lipas.ui.open-data.views :as open-data]
            [lipas.ui.register.views :as register]
            [lipas.ui.sports-sites.views :as sports-sites]
            [lipas.ui.subs :as subs]
            [lipas.ui.swimming-pools.views :as swimming-pools]
            [lipas.ui.user.views :as user]
            [lipas.ui.utils :refer [<== ==>]]))

(defn- panels [panel-name tr logged-in?]
  (case panel-name
    :home-panel      [front-page/main tr]
    :sports-panel    [sports-sites/main tr]
    :ice-panel       [ice-stadiums/main tr logged-in?]
    :swim-panel      [swimming-pools/main tr logged-in?]
    :open-data-panel [open-data/main tr]
    :help-panel      [help/main tr]
    :login-panel     [login/main tr]
    :register-panel  [register/main tr]
    :user-panel      [user/main tr]
    [:div "Unknown page :/"]))

(defn show-panel [panel-name tr logged-in?]
  [panels panel-name tr logged-in?])

(defn main-panel []
  (let [active-panel (<== [::subs/active-panel])
        menu-anchor  (<== [::subs/menu-anchor])
        drawer-open? (<== [::subs/drawer-open?])
        logged-in?   (<== [::subs/logged-in?])
        notification (<== [::subs/active-notification])
        tr           (<== [::subs/translator])]
    [mui/css-baseline
     [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
      [nav/nav tr menu-anchor drawer-open? active-panel logged-in?]]
     [mui/mui-theme-provider {:theme mui/jyu-theme-light}
      [show-panel active-panel tr logged-in?]
      (when notification
        [lui/notification
         {:notification notification
          :on-close #(==> [::events/set-active-notification nil])}])]]))
