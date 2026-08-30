# Kayan X 2.0 Architecture

## Core loop

`Understand -> Next Action -> Policy -> Tool -> Observe -> Verify -> Re-plan`

The LLM proposes only one next action. Python owns execution state and security.

## Filesystem boundary

All filesystem tools receive a shared `PathGuard`. The model can use:
- `downloads:/...`
- `workspace:/...`

Absolute paths are accepted only if they resolve inside an allowed root.

## Verification

The verifier uses deterministic checks for create/write/delete/move/copy. A successful tool return is not automatically treated as proof of task completion.

## Current deliberate constraints

- No unrestricted shell tool.
- No arbitrary path access.
- No automatic deletion.
- No long unbounded file reads.
- One tool action per reasoning cycle.

These constraints are intentional. Privileged shell execution can be added later as a separately isolated subsystem if benchmark evidence shows it is necessary.
