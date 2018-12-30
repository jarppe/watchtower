(ns watchtower.core-test
  (:require [clojure.test :refer :all]
            [testit.core :refer :all]
            [watchtower.core :as w]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (org.apache.commons.io FileUtils)))


;;
;; Test setup:
;;


(def ^:dynamic ^java.io.File *test-dir* nil)
(def ^:dynamic *changes* nil)


(defn with-test-dir [f]
  (let [test-dir (io/file (str "target/test/" (System/currentTimeMillis)))]
    (FileUtils/forceMkdir test-dir)
    (try
      (binding [*test-dir* test-dir]
        (f))
      (finally
        (FileUtils/deleteDirectory test-dir)))))


(defn with-test-files [f]
  (spit (io/file *test-dir* "test.txt") "hello")
  (FileUtils/forceMkdir (io/file *test-dir* "dir"))
  (spit (io/file *test-dir* "dir" "tree.txt") "world")
  (f))


(defn rel-name [^java.io.File f]
  (let [test-path (.getAbsolutePath *test-dir*)
        file-path (.getAbsolutePath f)]
    (when-not (str/starts-with? file-path test-path)
      (throw (ex-info "File is not relative to *test-dir*" {:test-path test-path, :file-path file-path})))
    (subs file-path (count test-path))))


(defn with-watcher [f]
  (let [changes (atom [])
        watcher (w/watcher [(.getAbsolutePath *test-dir*)]
                  (w/file-filter (fn [^java.io.File file]
                                   (-> file .getName (str/ends-with? ".txt"))))
                  (w/on-change (fn [changed-files]
                                 (->> changed-files
                                      (map rel-name)
                                      (set)
                                      (swap! changes conj)))))]
    ; Allow time for watcher to make initial path scan
    (Thread/sleep 100)
    (try
      (binding [*changes* changes]
        (f))
      (future-cancel watcher))))


(use-fixtures :each
  with-test-dir
  with-test-files
  with-watcher)


; The file watch service implementation in Mac uses polling, and it seems like
; it takes up to 5 - 8 sec for it to detect changes. On Linux changes are pretty
; much immediate. For windows, I have no idea.

(def timeout (if (-> (System/getProperty "os.name") (= "Mac OS X"))
               10000
               200))


;;
;; Tests:
;;


(deftest init-test
  (fact {:timeout timeout}
    @*changes* => [(just #{"/test.txt" "/dir/tree.txt"})]))


(deftest change-file-test
  (spit (io/file *test-dir* "test.txt") "world!")
  (fact {:timeout timeout}
    @*changes* => [(just #{"/test.txt" "/dir/tree.txt"})
                   (just #{"/test.txt"})]))


(deftest change-nested-file-test
  (spit (io/file *test-dir* "dir" "tree.txt") "world!")
  (fact {:timeout timeout}
    @*changes* => [(just #{"/test.txt" "/dir/tree.txt"})
                   (just #{"/dir/tree.txt"})]))


(deftest new-file-test
  (spit (io/file *test-dir* "dir" "foo.txt") "bar")
  (fact {:timeout timeout}
    @*changes* => [(just #{"/test.txt" "/dir/tree.txt"})
                   (just #{"/dir/foo.txt"})])
  (println @*changes*))


(deftest new-dir-and-file-test
  ; Yes, we do need to make a pause between creating directories and adding sub-dirs
  ; or files.
  (FileUtils/forceMkdir (io/file *test-dir* "dir2"))
  (Thread/sleep timeout)
  (FileUtils/forceMkdir (io/file *test-dir* "dir2" "dir3"))
  (Thread/sleep timeout)
  (spit (io/file *test-dir* "dir2" "dir3" "foo.txt") "bar")
  (Thread/sleep timeout)
  (fact {:timeout timeout}
    @*changes* => [(just #{"/test.txt" "/dir/tree.txt"})
                   (just #{"/dir2/dir3/foo.txt"})]))
