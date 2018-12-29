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
                  (w/rate 50)
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


;;
;; Tests:
;;


(deftest init-test
  (fact {:timeout 200}
    @*changes* => [(just #{"/test.txt" "/dir/tree.txt"})]))


(deftest change-file-test
  (spit (io/file *test-dir* "dir" "tree.txt") "world!")
  (fact {:timeout 200}
    @*changes* => [(just #{"/test.txt" "/dir/tree.txt"})
                   (just #{"/dir/tree.txt"})]))


(deftest new-file-test
  (spit (io/file *test-dir* "dir" "foo.txt") "bar")
  (fact {:timeout 200}
    @*changes* => [(just #{"/test.txt" "/dir/tree.txt"})
                   (just #{"/dir/foo.txt"})]))


(deftest new-dir-and-file-test
  (FileUtils/forceMkdir (io/file *test-dir* "dir2/dir3"))
  (spit (io/file *test-dir* "dir2" "dir3" "foo.txt") "bar")
  (fact {:timeout 200}
    @*changes* => [(just #{"/test.txt" "/dir/tree.txt"})
                   (just #{"/dir2/dir3/foo.txt"})]))
