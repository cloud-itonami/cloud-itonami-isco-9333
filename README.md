# cloud-itonami-isco-9333

Open Occupation Blueprint for **ISCO-08 9333**: Freight Handlers.

This repository designs a forkable OSS business for an independent freight handler: a pallet-handling robot performs loading, unloading and weight-verification tasks under a governor-gated actor, so the operator keeps their own handling and manifest records instead of renting a closed logistics SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a pallet-handling robot performs freight loading, unloading and weight-verification tasks under an actor that proposes
actions and an independent **Freight Handling Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating near loading docks, forklifts or overweight loads) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
shipment order + dock schedule + weight manifest
        |
        v
Freight Handling Advisor -> Freight Handling Governor -> load/unload, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `9333`). Required capabilities:

- :robotics
- :forms
- :telemetry
- :audit-ledger
- :bpmn

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
