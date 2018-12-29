(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [lucid.core.inject :as inject]
            [kaocha.repl :as kaocha]))


;;
;; Inject dev helpers:
;;


(inject/inject '[.
                 [aprint.core aprint]
                 [clojure.repl doc source dir]
                 [lucid.core.debug dbg-> dbg->> ->prn ->doto ->>doto]])


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
