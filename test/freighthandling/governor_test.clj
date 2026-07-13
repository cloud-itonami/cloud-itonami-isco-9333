(ns freighthandling.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [freighthandling.store :as store]
            [freighthandling.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Freight"})
    (store/register-shipment! st {:shipment-id "S-1" :client-id "client-1"
                                  :name "shipment-042"
                                  :manifest-weight-kg 500
                                  :weight-tolerance-kg 10
                                  :assigned-docks #{"dock-3" "dock-4"}})
    st))

(defn- load-op [weight dock]
  {:op :approve-load :effect :propose :shipment-id "S-1"
   :measured-weight-kg weight :dock dock :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-tolerance-and-assigned-dock
  (let [st (fresh-store)
        v (governor/check req {} (load-op 505 "dock-3") st)]
    (is (:ok? v))))

(deftest ok-at-exact-tolerance-edges
  (testing "the weight-tolerance band boundaries are inclusive"
    (let [st (fresh-store)]
      (is (:ok? (governor/check req {} (load-op 490 "dock-3") st)))
      (is (:ok? (governor/check req {} (load-op 510 "dock-3") st))))))

(deftest hard-on-weight-discrepancy
  (testing "weight discrepancy is a documentation failure, not handling judgement"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (load-op 600 "dock-3") :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :weight-discrepancy (:rule %)) (:violations v))))))

(deftest hard-on-unassigned-dock
  (testing "loading at an unassigned dock is not permitted"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (load-op 505 "dock-9") :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :unassigned-dock (:rule %)) (:violations v))))))

(deftest hard-on-unknown-shipment
  (let [st (fresh-store)
        v (governor/check req {} (assoc (load-op 505 "dock-3") :shipment-id "S-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-shipment (:rule %)) (:violations v)))))

(deftest hard-on-foreign-shipment
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (load-op 505 "dock-3") st)]
      (is (:hard? v))
      (is (some #(= :shipment-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (load-op 505 "dock-3") st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (load-op 505 "dock-3") :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-loading-dock-proximity-even-at-high-confidence
  (testing "no robot operation near loading docks without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-loading-dock-proximity :effect :propose
                                    :shipment-id "S-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-overweight-load-handling-even-at-high-confidence
  (testing "overweight-load handling requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-overweight-load-handling :effect :propose
                                    :shipment-id "S-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (load-op 505 "dock-3") :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
