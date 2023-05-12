(ns app.db
  (:require
   [reagent.core :as r]))

;; ----------------------------------------------------------------------------

(def default-db {:three {:renderer nil
                         :scene nil
                         :camera nil
                         :xr-controller nil}})

(def ^:private app-db (r/atom default-db))

(def three (r/cursor app-db [:three]))

(def previous-start
  (r/atom {:lat nil :long nil}))


(def start
  (r/atom {:lat nil :long nil}))

;; University
;; (def end
;;   (r/atom {:lat 48.840351 :long 2.584502}))

;;To define
(def end
  (r/atom {:lat nil :long nil}))


(def dist (r/atom {:d nil}))

;; ----------------------------------------------------------------------------

(defn init
  "
  @brief Intialize the engine database.
  "
  [db]
  (reset! app-db db))

;; ----------------------------------------------------------------------------
