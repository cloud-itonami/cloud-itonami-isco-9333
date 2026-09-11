(ns freighthandling.governor
  "FreightHandlingGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  the robot-dispensed physical work (freight loading, unloading,
  weight-verification) an advisor may propose. The governor never
  dispatches hardware itself. Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. Load twist: the proposed load's measured
  weight must reconcile with the registered manifest weight within
  the registered tolerance — a discrepancy beyond it is a
  documentation failure, not handling judgement — and the proposed
  dock must be a member of the registered assigned-docks set —
  loading at an unassigned dock is not permitted.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose (the
                           governor never dispatches hardware; it only
                           gates what the robot may execute).
    3. shipment basis       — a load approval must cite a REGISTERED
                           shipment belonging to this client.
    4. weight reconciliation — the proposed measured weight must fall
                           inside the shipment's registered
                           [manifest-weight - tolerance,
                           manifest-weight + tolerance] band (a
                           discrepancy beyond it is a documentation
                           failure, not handling judgement).
    5. dock-assignment membership — the proposed dock must be a
                           member of the shipment's registered
                           :assigned-docks set (loading at an
                           unassigned dock is not permitted).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-loading-dock-proximity (no robot operation near
                           loading docks without the governor gate).
    7. :op :approve-overweight-load-handling (overweight-load handling
                           requires human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [freighthandling.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-loading-dock-proximity
                                     :approve-overweight-load-handling})

(defn- hard-violations [{:keys [request proposal]} client-record s]
  (let [{:keys [op measured-weight-kg dock]} proposal
        load? (= :approve-load op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor はハードウェアを直接起動しない）"})

      (and load? (nil? s))
      (conj {:rule :unknown-shipment :detail "未登録 shipment への荷役承認は不可"})

      (and load? s (not= (:client-id s) (:client-id request)))
      (conj {:rule :shipment-wrong-client :detail "shipment が別 client のもの"})

      (and load? s (number? measured-weight-kg)
           (or (< measured-weight-kg (- (:manifest-weight-kg s) (:weight-tolerance-kg s)))
               (> measured-weight-kg (+ (:manifest-weight-kg s) (:weight-tolerance-kg s)))))
      (conj {:rule :weight-discrepancy
             :detail (str "測定重量 " measured-weight-kg "kg がマニフェスト許容帯 ["
                          (- (:manifest-weight-kg s) (:weight-tolerance-kg s)) ", "
                          (+ (:manifest-weight-kg s) (:weight-tolerance-kg s))
                          "]kg の外（重量差異は書類上の不備であって荷役判断ではない）")})

      (and load? s dock (not (contains? (:assigned-docks s) dock)))
      (conj {:rule :unassigned-dock :detail (str "ドック " dock " は登録済み割当集合の外（未割当ドックでの荷役は許可されない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `freighthandling.store/Store`. Pure — never
  mutates the store, never dispatches the robot."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        s (some->> (:shipment-id proposal) (store/shipment store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record s)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
