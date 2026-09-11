(ns freighthandling.store
  "SSoT for the ISCO-08 9333 independent freight handling practice
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section; README's 'Robotics premise' — a pallet-handling robot
  performs freight loading, unloading and weight-verification tasks
  under this advisor/governor pair, which never dispatches hardware
  itself). Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client   — a registered organization (:client-id, :name)
    shipment — a registered shipment {:shipment-id :client-id :name
               :manifest-weight-kg number :weight-tolerance-kg number
               :assigned-docks #{dock-str}}.
               `:manifest-weight-kg` ± `:weight-tolerance-kg` is the
               registered band a proposed load's measured weight must
               fall inside — manifest and measured weight must
               reconcile within tolerance, a discrepancy beyond it is
               a documentation failure, not handling judgement;
               `:assigned-docks` is the registered set a proposed
               load's dock must be a member of — loading at an
               unassigned dock is not permitted.
    record   — a committed operating record (approved load) —
               written ONLY via commit-record!.
    ledger   — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (shipment [s shipment-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-shipment! [s s2])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (shipment [_ shipment-id] (get-in @a [:shipments shipment-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-shipment! [s s2]
    (swap! a assoc-in [:shipments (:shipment-id s2)] s2) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :shipments {} :records [] :ledger []}
                                   seed)))))
