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


(def ^java.io.File test-dir (doto (io/file "target/core-test")
                              (FileUtils/forceMkdir)
                              (FileUtils/cleanDirectory)))


(defn with-clean-test-dir [f]
  (FileUtils/cleanDirectory test-dir)
  (f))


(defn rel-name [^java.io.File f]
  (let [test-path (.getAbsolutePath test-dir)
        file-path (.getAbsolutePath f)]
    (when-not (str/starts-with? file-path test-path)
      (throw (ex-info "File is not relative to *test-dir*" {:test-path test-path, :file-path file-path})))
    (subs file-path (count test-path))))


(def changes (atom #{}))


(defn with-watcher [f]
  (reset! changes #{})
  (let [watcher (w/watcher [(.getAbsolutePath test-dir)]
                  (w/file-filter (w/extensions :txt))
                  (w/on-change (fn [changed-files]
                                 (->> changed-files
                                      (map rel-name)
                                      (swap! changes into)))))]
    (try
      (f)
      (finally
        (w/stop! watcher)))))


(use-fixtures :each
  with-clean-test-dir
  with-watcher)


(def timeout 200)


;;
;; Tests:
;;


(deftest init-test
  (fact {:timeout timeout}
    @changes => empty?))


(deftest change-file-test
  (spit (io/file test-dir "test.txt") "world!")
  (fact {:timeout timeout}
    @changes => (just #{"/test.txt"})))


(deftest change-nested-file-test
  (FileUtils/forceMkdir (io/file test-dir "dir"))
  (spit (io/file test-dir "dir" "tree.txt") "world!")
  (fact {:timeout timeout}
    @changes => (just #{"/dir/tree.txt"})))


(deftest new-file-test
  (FileUtils/forceMkdir (io/file test-dir "dir"))
  (spit (io/file test-dir "dir" "foo.txt") "bar")
  (fact {:timeout timeout}
    @changes => (just #{"/dir/foo.txt"})))


(deftest new-files-test
  (spit (io/file test-dir "foo.txt") "bar")
  (spit (io/file test-dir "bar.txt") "bar")
  (spit (io/file test-dir "boz.txt") "bar")
  (spit (io/file test-dir "not-this.xyz") "bar")
  (fact {:timeout timeout}
    @changes => (just #{"/foo.txt" "/bar.txt" "/boz.txt"}))
  (prn @changes))


(deftest new-dir-and-file-test
  (FileUtils/forceMkdir (io/file test-dir "dir2" "dir3"))
  (spit (io/file test-dir "dir2" "dir3" "foo.txt") "bar")
  (fact {:timeout timeout}
    @changes => (just #{"/dir2/dir3/foo.txt"})))
