(ns watchtower.core
  (:require [clojure.core.async :as a :refer [<! >! go go-loop]])
  (:import (java.io File)
           (java.nio.file Path Paths)
           (io.methvin.watcher DirectoryWatcher DirectoryChangeListener)
           (io.methvin.watcher.hashing FileHasher)))


(set! *warn-on-reflection* true)


(defn- ->path ^Path [v]
  (cond
    (instance? Path v) v
    (instance? File v) (.toPath ^File v)
    (string? v) (Paths/get v (into-array String []))
    :else (throw (ex-info (str "don't know how to coerce to path from: " v) {}))))


(def ^:private change-type->FileHasher
  {:file-hash     FileHasher/DEFAULT_FILE_HASHER
   :last-modified FileHasher/LAST_MODIFIED_TIME})


(defn- ->listener ^DirectoryChangeListener [on-change-ch]
  (reify DirectoryChangeListener
    (onEvent [_ event]
      (a/>!! on-change-ch (-> event .path .toFile)))))


(defn- make-watcher ^DirectoryWatcher [paths change-type on-change-ch]
  (doto (-> (DirectoryWatcher/builder)
            (.paths (mapv ->path paths))
            (.listener (->listener on-change-ch))
            (.fileHasher (change-type->FileHasher change-type))
            (.build))
    (.watchAsync)))

(defn- debounce [in timeout-ms]
  (let [out (a/chan)]
    (go-loop [acc nil]
      (let [val   (if (nil? acc)
                    [(<! in)]
                    acc)
            timer (a/timeout timeout-ms)
            [new-val ch] (a/alts! [in timer])]
        (condp = ch
          timer (do (>! out val)
                    (recur nil))
          in (if new-val
               (recur (conj val new-val))
               (a/close! out)))))
    out))


(defn- watcher-ch [{:keys [paths file-filter change-type debounce-ms]
                   :or   {change-type :last-modified
                          debounce-ms 10}}]
  {:pre [(->> paths (every? #(instance? Path %)))
         (-> change-type (change-type->FileHasher))
         (-> file-filter (ifn?))
         (-> debounce-ms (integer?))]}
  (let [on-change-ch (a/chan 16 (filter file-filter))
        watcher      (make-watcher paths change-type on-change-ch)
        on-change-ch (debounce on-change-ch debounce-ms)
        on-close-ch  (a/chan)]
    (go (<! on-close-ch)
        (a/close! on-change-ch)
        (.close watcher))
    [on-change-ch on-close-ch]))


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
  (fn [files]
    (doseq [f funcs]
      (f files))))


(defn watch
  "Execute a watcher map"
  [{:keys [filters dirs on-change]}]
  (let [changed (changed-fn on-change)
        opts    {:paths       (mapv ->path dirs)
                 :file-filter (fn [file] (every? #(% file) filters))}
        [on-change-ch on-close-ch] (watcher-ch opts)]
    (go-loop []
      (when-let [v (<! on-change-ch)]
        (changed v)
        (recur)))
    (fn []
      (a/close! on-close-ch))))


(defmacro watcher
  "Create a watcher for the given dirs (either a string or coll of strings), applying
  the given transformations.

  Transformations available: (rate) (file-filter) (on-change)"
  [dirs & body]
  `(let [w# (-> ~dirs
                (watcher*)
                ~@body)]
     (watch w#)))


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
