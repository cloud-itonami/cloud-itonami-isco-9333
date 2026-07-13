(ns freighthandling.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [freighthandling.actor :as actor]
            [freighthandling.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Freight"})
    (store/register-shipment! st {:shipment-id "S-1" :client-id "client-1"
                                  :name "shipment-042"
                                  :manifest-weight-kg 500
                                  :weight-tolerance-kg 10
                                  :assigned-docks #{"dock-3"}})
    st))

(deftest commits-an-in-tolerance-assigned-dock-load
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-load :stake :low
                 :shipment-id "S-1" :measured-weight-kg 505 :dock "dock-3"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-a-weight-discrepant-load
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-load :stake :low
                 :shipment-id "S-1" :measured-weight-kg 900 :dock "dock-3"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-overweight-handling-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-overweight-load-handling :stake :low
                 :shipment-id "S-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
