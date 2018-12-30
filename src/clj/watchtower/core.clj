(ns watchtower.core
  (:import (java.nio.file Path
                          Paths
                          Files
                          FileSystems
                          FileVisitResult
                          SimpleFileVisitor
                          WatchEvent
                          WatchEvent$Kind
                          StandardWatchEventKinds
                          ClosedWatchServiceException
                          LinkOption)))

;;
;; Java interop helpers:
;;

(set! *warn-on-reflection* true)


(def ^:private empty-string-array (into-array String []))
(def ^:private empty-link-options (into-array LinkOption []))
(def ^:private watch-events (into-array WatchEvent$Kind [StandardWatchEventKinds/ENTRY_CREATE
                                                         StandardWatchEventKinds/ENTRY_MODIFY
                                                         StandardWatchEventKinds/ENTRY_DELETE]))


(defn- dir? [^Path path]
  (Files/isDirectory path empty-link-options))


(defn- str->path ^Path [path-name]
  (let [path (Paths/get path-name empty-string-array)]
    (when-not (dir? path)
      (throw (ex-info (str "can't watch path: " path) {:path path})))
    path))


(defn- init-watch-paths! [paths add-watch-path! match? changed]
  (let [init-files   (atom #{})
        file-visitor (proxy [SimpleFileVisitor] []
                       (preVisitDirectory [path _attrs]
                         (add-watch-path! path)
                         FileVisitResult/CONTINUE)
                       (visitFile [^Path path _attrs]
                         (let [file (-> path .toFile)]
                           (when (match? file)
                             (swap! init-files conj file)))
                         FileVisitResult/CONTINUE))]
    (doseq [path paths]
      (Files/walkFileTree path file-visitor))
    (let [files @init-files]
      (when-not (empty? files)
        (changed files)))))


;;*****************************************************
;; Watcher map creation 
;;*****************************************************

(defn watcher*
  "Create a watcher map that can later be passed to (watch)"
  [dirs]
  (let [dirs (if (string? dirs)
               [dirs]
               dirs)]
    {:dirs    dirs
     :filters []}))

(defn file-filter
  "Add a filter to a watcher. A filter is just a function that takes in a
  java.io.File and returns truthy about whether or not it should be included."
  [w filt]
  (update-in w [:filters] conj filt))

(defn rate
  "Set the rate of polling.
  Depreciated, rate has no meaning in this version."
  [w r]
  (println "depreciated, rate has no meaning in this version")
  w)

(defn on-change
  "When files are changed, execute a function that takes in a seq of the changed
  file objects."
  [w func]
  (update-in w [:on-change] conj func))

;;*****************************************************
;; Watcher execution  
;;*****************************************************

(defn changed-fn [funcs]
  (fn [files]
    (doseq [f funcs]
      (f files))))

(defn- compile-watcher [{:keys [filters dirs on-change]}]
  {:dirs    (mapv str->path dirs)
   :match?  (fn [file] (every? #(% file) filters))
   :changed (changed-fn on-change)})

(defn watch
  "Execute a watcher map"
  [w]
  (let [{:keys [dirs match? changed]} (compile-watcher w)
        watcher-service (-> (FileSystems/getDefault)
                            (.newWatchService))
        watch-paths     (atom {})
        add-watch-path! (fn [path]
                          (let [watch-key (.register ^Path path watcher-service watch-events)]
                            (swap! watch-paths assoc watch-key path)))]
    ; Register watcher for given paths:
    (init-watch-paths! dirs add-watch-path! match? changed)
    ; Enter to a loop for polling and handling of file change events:
    (try
      (while true
        (let [key        (.take watcher-service)
              ; This small sleep allow aggregating multiple events into a one report. Without
              ; this we would get separate notifications for file content change and file atime
              ; change.
              events     (do (Thread/sleep 20)
                             (.pollEvents key))
              watch-path (get @watch-paths key)]
          (some->> events
                   ; Map event to a java.io.File, if the event is for creation of a new directory,
                   ; register a watch for that directory too:
                   (map (fn [^WatchEvent event]
                          (let [^Path context (-> event .context)
                                ^Path path    (.resolve ^Path watch-path context)]
                            (when (and (-> event .kind (= StandardWatchEventKinds/ENTRY_CREATE))
                                       (-> path dir?))
                              (add-watch-path! path))
                            (-> path .toFile))))
                   (filter match?)
                   (seq)
                   (set)
                   (changed))
          (.reset key)))
      (catch InterruptedException _expected
        (try
          (.close watcher-service)
          (catch Exception e
            (.println System/err "unexpected error while closing watch-service")
            (.println System/err e)
            (.printStackTrace e))))
      (catch ClosedWatchServiceException _expected))))

(defmacro watcher
  "Create a watcher for the given dirs (either a string or coll of strings), applying
  the given transformations.

  Transformations available: (rate) (file-filter) (on-change)"
  [dirs & body]
  `(let [w# (-> ~dirs
                (watcher*)
                ~@body)]
     (future (watch w#))))

;;*****************************************************
;; file filters
;;*****************************************************

(defn ignore-dotfiles
  "A file-filter that removes any file that starts with a dot."
  [^java.io.File f]
  (not= \. (first (.getName f))))

(defn extensions
  "Create a file-filter for the given extensions."
  [& exts]
  (let [exts-set (set (map name exts))]
    (fn [^java.io.File f]
      (let [fname (.getName f)
            idx   (.lastIndexOf fname ".")
            cur   (if-not (neg? idx) (subs fname (inc idx)))]
        (exts-set cur)))))
