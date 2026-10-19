# Release Notes — v0.4.1: Test Determinism

## Summary
Version **v0.4.1** is a maintenance release that improves automated test determinism under heavy CPU load. It contains no user-facing changes.

### Changes
- Replaced real `Dispatchers.Default` background test scopes in `PromptsRepositoryTest`, `RelayConnectionTest`, and `MeshRepositoryTest` with `UnconfinedTestDispatcher` and `runTest`.
- Replaced wall-clock polling loops with deterministic flow state awaits, eliminating timeout cancellation flakiness under high CPU contention.
