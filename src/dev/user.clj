(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [kaocha.repl :as kaocha]))


;;
;; Reset, start and stop:
;;


(def reset repl/refresh)
(def start (constantly :ok))
(def stop (constantly :ok))


;;
;; Testing:
;;


(defn run-unit-tests []
  (kaocha/run :all))


(defn run-all-tests []
  (kaocha/run-all))
