(ns app.core
  (:require
    ; ["es-module-shims"]
   ["regenerator-runtime"]
   ["three" :as THREE]
   ["three/examples/jsm/webxr/ARButton.js" :as ARButton]
   ["three/examples/jsm/loaders/GLTFLoader" :refer [GLTFLoader]]
   ["troika-three-text" :refer [Text]]
   [applied-science.js-interop :as j]
   [app.db :as db]))

;; ----------------------------------------------------------------------------

;; Original demo : 
; https://threejs.org/examples/?q=webxr#webxr_ar_cones
; https://github.com/mrdoob/three.js/blob/master/examples/webxr_ar_cones.html

;; WebXR requires to use HTTPS (or localhost). 
;; To run locally on a device, you will need to install a custom SSH certificate 
;; for both the server and the mobile device. See :
; https://dev.to/rberger/set-up-ssltls-for-shadow-cljs-https-server-2np7

;; To Debug, open this on Chrome Desktop while the app is open on Chrome Android !
; chrome://inspect/#devices

(declare webxr-on-select)


;; ----------------------------------------------------------------------------
;; Utils.



;; @desc Logger.
(def dev-log (partial (.-log js/console) "[Dev Log]"))

;; Creates the button to enter AR mode.
(def create-ar-button (partial (j/get-in ARButton [:ARButton :createButton])))

(defn dom-viewport
  "
  @desc Returns the DOM viewport.
  "
  []
  (let [{:keys [innerWidth innerHeight]} (j/lookup js/window)]
    [innerWidth innerHeight]))

(defn dom-create-element
  "
  @desc Creates a tag element in the DOM with an optional id.
  "
  ([tag & {:keys [id]}]
   (when-let [el (j/call js/document :createElement (name tag))]
     (when id
       (j/call el :setAttribute "id" id))
     el)))

;; ----------------------------------------------------------------------------
;; Setup.

(defn- on-window-resize
  "
  @desc Updates the camera and renderer surface when the viewport is resized.
  
  @param camera : Threejs current camera [Camera object].
  @param renderer : Threejs current renderer [Renderer object].
  "
  [^THREE/Camera camera renderer]
  (let [viewport (dom-viewport)
        [w h] viewport
        aspect (/ w h)]
    ;; Update camera properties.
    (doto camera
      (j/assoc! :aspect aspect)
      (j/call :updateProjectionMatrix))
    ;; Resize renderer.
    (j/call renderer :setSize w h)))

(defn- init-engine
  "
  @desc Initializes the dom canvas and webGL renderer. 
  
  @param [pixel-size] : Screen size in pixels of a rendered surface pixel.
  "
  ([& {:keys [pixel-size] :or {pixel-size 1}}]
   (let [renderer (THREE/WebGLRenderer. (clj->js {:antialias true
                                                  :alpha true}))
         scene (THREE/Scene.)
         camera (THREE/PerspectiveCamera. 70 1 0.01 20.0)]

     ;; Renderer configuration.
     (doto renderer
       (j/call :setPixelRatio pixel-size)
       (j/assoc-in! [:xr :enabled] true))

     ;; Update renderer surface on resize event.
     (let [on-resize (partial on-window-resize camera renderer)]
       (j/call js/window :addEventListener "resize" on-resize false)
       (on-resize))

     ;; Scene Background must be empty for AR to work.
     (j/assoc! scene :background nil)

     ;; Update the DOM.
     (let [container (dom-create-element :div :id "canvas")
           canvas (j/get renderer :domElement)
           dom-body (j/get js/document :body)]
       (doto container
         (j/call :appendChild canvas))
       (doto dom-body
         (j/call :appendChild (create-ar-button renderer))
         (j/call :appendChild container)))

     ;; Initalize the engine database.
     (let [xr-controller (j/call-in renderer [:xr :getController] 0)]
       (j/call scene :add xr-controller)

       (db/init {:three {:renderer renderer
                         :scene scene
                         :camera camera
                         :xr-controller xr-controller}})))
   nil))


(defn deg2rad [deg] (* deg (/ js/Math.PI 180)))


(defn distance
  [lat1 lon1 lat2 lon2]
  (let [R 6371
        dLat (deg2rad (- lat2 lat1))
        dLon (deg2rad (- lon2 lon1))
        a (+
           (* (Math/sin (/ dLat 2)) (Math/sin (/ dLat 2)))
           (* (* (* (Math/cos (deg2rad lat1))
                    (Math/cos (deg2rad lat2)))
                 (Math/sin (/ dLon 2)))
              (Math/sin (/ dLon 2))))
        c (* 2
             (Math/atan2
              (Math/sqrt  a)
              (Math/sqrt  (- 1 a))))
        d (* R c)]

    (reset! db/dist {:d d})))


(defn print-pos
  "
  @desc Show the actual position of the device
  "
  []
  (aset (.getElementById js/document "startLat") "textContent" (@db/start :lat))
  (aset (.getElementById js/document "startLong") "textContent" (@db/start :long))

  (aset (.getElementById js/document "endLat") "textContent" (@db/end :lat))
  (aset (.getElementById js/document "endLong") "textContent" (@db/end :long))

  (aset (.getElementById js/document "distanceLeft") "textContent" (/ (Math/round (* (@db/dist :d) 1000)) 1000))
  (js/alert "Actual location changed"))

(defn update-actual-pos
  "
  @desc Update the current's device position  
  "
  []
  (.getCurrentPosition
   (.-geolocation js/navigator)
   (fn [location]
     (reset! db/previous-start {:lat (@db/start :lat) :long (@db/start :long)})
     (reset! db/start {:lat (.-latitude (.-coords location)) :long (.-longitude (.-coords location))})))



  (if (and (= (@db/start :lat) (@db/previous-start :lat)) (= (@db/start :long) (@db/previous-start :long)))
    (js/console.log "You didn't move")
    (do
      (distance (@db/start :lat) (@db/start :long) (@db/end :lat) (@db/end :long))
      (print-pos))))



(defn- mainloop
  "
  @desc Launch the renderloop.
        For WebXR we *must* use setAnimationLoop.
  "
  []
  (let [{:keys [renderer scene camera]} @db/three]
    (j/call renderer :setAnimationLoop
            (fn [] (j/call renderer :render scene camera)))))

;; ----------------------------------------------------------------------------

;; (defn webxr-on-select
;;   "
;;   @desc Creates a custom on-select WebXR event callback.
;;   "
;;   [scene xr-controller]
;;   (let [geo (-> (THREE/CylinderGeometry. 0 0.05 0.2 32)
;;                 (j/call :rotateX (/ js/Math.PI 2)))]

;;     (fn []
;;       (let [color (js/parseInt (* 0xffffff (Math/random)))
;;             material (THREE/MeshPhongMaterial. (clj->js {:color color}))
;;             mesh (THREE/Mesh. geo material)
;;             world-matrix (j/get xr-controller :matrixWorld)]
;;         (doto mesh
;;           (j/call-in [:position :set] 0 0 (- 2))
;;           (j/call-in [:position :applyMatrix4] world-matrix)
;;           (j/call-in [:quaternion :setFromRotationMatrix] world-matrix))
;;         (j/call scene :add mesh)))))

(defn webxr-on-select
  "
  @desc Creates a custom on-select WebXR event callback.
  "
  [scene xr-controller]

  (let [mesh (atom nil)
        _ (.load (GLTFLoader.) "./Items/uge2.glb"
                 (fn [gltf]
                   (let [scene (j/get gltf :scene)]
                     (reset! mesh scene)
                     (j/call scene :rotateX (/ js/Math.PI 2)))))]
    (fn []
      (let [obj @mesh
            world-matrix (j/get xr-controller :matrixWorld)]
        (doto obj
          (j/call-in [:position :set] 0 0 (- 2))
          (j/call-in [:position :applyMatrix4] world-matrix)
          (j/call-in [:quaternion :setFromRotationMatrix] world-matrix)

          (js/setTimeout (fn [] (js/requestAnimationFrame (j/call-in obj [:rotateY] (/ js/Math.PI 4)))) 1000))
        (j/call scene :add obj)))))



(defn createText [text, size, pos, color, scene]
  (let [myText (Text.)]
    (j/call scene :add myText)
    (aset myText "text" text)
    (aset myText "fontSize" size)
    (aset (.-position myText) "z" pos)
    (aset myText "color" color)
    (.sync myText)))



(defn- setup-scene
  "
  @desc Creates a default scene for testing purpose.
  "
  []
  (let [{:keys [camera scene xr-controller]} @db/three]
    ;; Add a light to the scene.
    (let [light (THREE/HemisphereLight. 0xffffff 0xbbbbff 1)]
      (j/call-in light [:position :set] 0.5 1.0 0.25)
      (j/call scene :add light)
      ;; (j/call scene :add )


      (createText "Welcome to UGE!" 0.2 (- 2) 1514639 scene))


    ;; Add an event to WebXR
    (j/call xr-controller :addEventListener "select" (webxr-on-select scene xr-controller)))
  nil)



(defn check-activate-ar
  "@desc Checks if the user is within 5m to activate WebXR "
  []

  (if (and (< (@db/dist :d) 0.005) (not= (@db/dist :d) nil))
    (do
      (aset (.-style (.getElementById js/document "ar")) "color" "green")
      (aset (.getElementById js/document "ar") "textContent" "You can activate WebXR")
      (js/console.log "you can activate webXR"))
    (do
      (aset (.-style (.getElementById js/document "ar")) "color" "red")
      (aset (.getElementById js/document "ar") "textContent" "You must be within 5m to activate WebXR.")
      ;; (js/console.log "you can't activate webXR")
      )))


(defn animate
  []

  (update-actual-pos)
  (check-activate-ar)
  (if (and (< (@db/dist :d) 0.005) (not= (@db/dist :d) nil))
    (do
      (init-engine)
      (setup-scene)
      (mainloop))
    (js/setTimeout (fn [] (js/requestAnimationFrame animate)) 5000)))







;; ----------------------------------------------------------------------------

; (defn- main-view []
;  (r/create-class {:reagent-render (fn [] [:<>])}))
;
; (defn- ^:dev/after-load mount-root
;   ""
;   []
;   (rf/clear-subscription-cache!)
;   (let [root-el (.getElementById js/document "root")]
;     (rdom/unmount-component-at-node root-el)
;     (rdom/render [main-view] root-el)))

(defn ^:export main
  "
  @desc Application entry-point.
  "
  []

  (animate))

;; ----------------------------------------------------------------------------
