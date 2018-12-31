(ns watchtower.core
  (:require [hawk.core :as hawk]))


(set! *warn-on-reflection* true)


;;*****************************************************
;; Watcher map creation 
;;*****************************************************


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


(defn watcher* [dirs]
  {:dirs (if (coll? dirs)
           dirs
           [dirs])})


(defn- changed-fn [funcs]
  (fn [_ {:keys [file]}]
    (doseq [f funcs]
      (f [file]))
    nil))


(defn watch
  "Execute a watcher map"
  [{:keys [filters dirs on-change]}]
  (hawk/watch! [{:paths dirs
                 :filter (fn [_ {:keys [file]}] (every? #(% file) filters))
                 :handler (changed-fn on-change)}]))


(defmacro watcher
  "Create a watcher for the given dirs (either a string or coll of strings), applying
  the given transformations.

  Transformations available: (rate) (file-filter) (on-change)"
  [dirs & body]
  `(let [w# (-> ~dirs
                (watcher*)
                ~@body)]
     (watch w#)))


(defn stop! [watcher]
  (hawk/stop! watcher))


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
