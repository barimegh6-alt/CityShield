# data/

* `synthetic/` — 16 CSV files forming the reproducible synthetic data set (50 zones, 114 roads, 1,000 incidents, …). Copies live in `backend/src/main/resources/seed/` and are loaded automatically on first start.
* `tools/` — `spec.py` (single source of truth for tables), `generate_dataset.py` (deterministic data generator, fixed seed), `generate_backend_code.py` (writes `schema.sql`, entities, repositories). Run with Python 3 from the repository root.
* `benchmarks/benchmark_results.csv` — **measured** output of `BenchmarkHarness`; regenerate with the command in `SETUP_WINDOWS.md` §6. The numbers depend on the machine they were run on.

All data is synthetic and for academic use only.
