# Run PUS

Build (if needed) and run the PUS example with the embedded PUS simulator.

## Usage

`/run-pus` — run the PUS example  
`/run-pus --debug` — run with JVM debug port open (suspends until debugger attaches)  
`/run-pus --rebuild` — force a Maven rebuild before running  

## What this does

The PUS example starts:
- A YAMCS server configured from `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml`
- An embedded PUS simulator (`org.yamcs.simulator.pus.PusSimulator`) on TM port 10015 / TC port 10025
- The MDB loaded from `examples/pus/src/main/yamcs/mdb/` (dt.xml, pus.xml, pus2.xml, pus3.xml, pus5.xml, pus9.xml, pus11.xml, pus17.xml, landing.xml)

## Steps

1. If `--rebuild` is given, run `mvn install -DskipTests` first from the project root.
2. If `--debug` is given, run:
   ```
   mvn -f examples/pus/pom.xml yamcs:debug -Dyamcs.jvm.debug.suspend
   ```
   Otherwise run:
   ```
   mvn -f examples/pus/pom.xml yamcs:run
   ```
3. RUN DIS
4. ```
   mvn -pl examples/pus yamcs:run
   ```
3. Run from `/Users/nikhil/GSS Workspace/yamcs-Pixxel-fork`.
4. The YAMCS web UI will be available at http://localhost:8090 once started.
5. Stream output and watch for startup errors. Report when YAMCS is up or if it fails.