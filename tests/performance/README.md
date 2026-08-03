# STP Performance Benchmarks

This directory documents the reproducible JMH suite in `backend/stp-benchmarks`. The suite is not run by normal Maven tests or `verify`; run it explicitly because JMH needs warmup and measurement time to produce useful results.

## Coverage

- `encodeThroughput`: encodes batches of validated STP messages.
- `decodeCompleteFrames`: decodes complete prebuilt frames.
- `decodeIncrementalChunks`: feeds concatenated frames through `IncrementalStpDecoder` using a repeating set of 7, 23, 64, 128, 37, and 256 byte chunks.

Each benchmark runs `ADDS_ONLY`, `LIFECYCLE`, and `BALANCED` message mixes. Results are consumed by JMH `Blackhole` and each benchmark performs 1,024 operations per invocation, preventing dead-code elimination and amortizing harness overhead.

## Environment

Measurements are machine-specific. Record the following alongside any comparison:

```sh
uname -a
sysctl -n machdep.cpu.brand_string 2>/dev/null || true
java -version
./backend/mvnw -v
```

JMH's JSON output also records benchmark parameters, JVM, and JMH version. Do not claim a performance number until a measurement has completed on a recorded environment.

## Commands

Build the benchmark JAR without running benchmarks:

```sh
./backend/mvnw -f backend/pom.xml -pl stp-benchmarks -am package
```

Run a short smoke measurement:

```sh
./scripts/run-stp-benchmarks.sh -wi 0 -i 1 -f 1 -r 1s
```

Run the configured warmup and measurement defaults:

```sh
./scripts/run-stp-benchmarks.sh
```

Each run writes machine-readable JSON to `tests/performance/results/`, which is intentionally gitignored. Pass standard JMH filters or options after the script command, for example `./scripts/run-stp-benchmarks.sh decodeIncrementalChunks`.
