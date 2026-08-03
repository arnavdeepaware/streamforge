# Backend Build

The backend is a Java 21 Maven multi-module build. It includes immutable market-data value types, STP v1 framing and codecs, and a deterministic tick simulator with a local TCP generator-to-parser path. It does not yet provide pipeline runtime behavior or control-plane services.

## Requirements

- A JDK from 21 through 26. The build compiles with Java 21 release compatibility.
- Internet access the first time Maven Wrapper downloads Maven and dependency artifacts.

## Commands

From the repository root:

```sh
./backend/mvnw -f backend/pom.xml verify
```

From the `backend/` directory:

```sh
./mvnw verify
```

The reactor includes `common-model`, `stp-protocol`, `tick-simulator`, `parser-engine`, `transform-engine`, `pipeline-runtime`, `control-plane`, and `stream-worker`.

## Tick Simulator

Build the simulator and its reactor dependencies, then write a finite binary STP fixture:

```sh
./backend/mvnw -f backend/pom.xml -pl tick-simulator -am package
java -cp backend/tick-simulator/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.ticksimulator.TickSimulatorCli \
  --seed 5 --symbols AAPL,MSFT --count 100 --output ticks.stp
```

Use `--output -` to write binary frames to standard output. Run the final command with `--help` for event-distribution, timestamp, and continuous-mode options. The classpath separator above is for POSIX shells.

## Local TCP Demo

Build the two modules:

```sh
./backend/mvnw -f backend/pom.xml -pl tick-simulator,parser-engine -am package
```

In terminal 1, start a server that exits after serving its first finite client:

```sh
java -cp backend/tick-simulator/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.ticksimulator.TickTcpServerCli \
  --host 127.0.0.1 --port 9010 --seed 5 --symbols AAPL,MSFT --count 10 --rate 0
```

In terminal 2, connect and print parsed events:

```sh
java -cp backend/parser-engine/target/classes:backend/stp-protocol/target/classes:backend/common-model/target/classes \
  io.streamforge.parserengine.StpParserCli \
  --host 127.0.0.1 --port 9010 \
  --report-sequence-integrity --source demo-session
```

Sequence integrity reporting is optional; it prints structured expected, gap, duplicate, and late/out-of-order events for the named logical source/session. The TCP server writes directly to each client socket. TCP flow control blocks generation for a slow client instead of accumulating an unbounded application queue. The classpath separators above are for POSIX shells.
