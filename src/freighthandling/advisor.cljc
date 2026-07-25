(ns freighthandling.advisor
  "FreightHandlingAdvisor — the advisor named in this repository's
  README, proposing a shipment operation (approve a load, approve
  loading-dock proximity, approve overweight-load handling) from a
  shipment order, dock schedule and weight manifest. Swappable
  mock/llm; the advisor ONLY proposes — `freighthandling.governor`
  checks weight reconciliation and dock-assignment membership
  independently and always escalates loading-dock-proximity/
  overweight decisions. Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-load|:approve-loading-dock-proximity|:approve-overweight-load-handling
               :effect :propose :shipment-id str
               :measured-weight-kg number :dock str :stake kw
               :confidence n :rationale str}"
  ;; clojure.edn, not clojure.core/read-string: this parses untrusted
  ;; advisor output, and the core reader executes #=(...) at read time.
  (:require [clojure.edn :as edn]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake shipment-id measured-weight-kg dock] :as request}]
  {:op op
   :effect :propose
   :shipment-id shipment-id
   :measured-weight-kg measured-weight-kg
   :dock dock
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a freight-handling advisor. Given a request, propose an
   :op, the :shipment-id, :measured-weight-kg and :dock, an honest
   :confidence and a :stake. Never call a weight-discrepant load or an
   unassigned-dock load conforming — the governor checks both against
   the registered shipment record. Loading-dock-proximity and
   overweight-load decisions always require human sign-off regardless
   of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
