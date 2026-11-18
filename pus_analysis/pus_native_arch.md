# PUS ground architecture in Yamcs

Reviewed against commit `90d4a3d1c` on 2026-11-18. This guide describes the current
`examples/pus` configuration. Yamcs is the ground MCS; the Java simulator is the test spacecraft.

## Deployment and configuration

```text
Operator / API → Yamcs processor → TC stream → TCP TC link → spacecraft or Java simulator
Operator / API ← Yamcs parameters/events/archive ← TM stream ← TCP TM link ← spacecraft
```

The example runs TCP telemetry on port 10015 and telecommands on port 10025. Transfer-frame
examples have separate configuration; do not apply packet TCP offsets to a complete transfer frame.

| Source | Role |
|---|---|
| [processor.yaml](../examples/pus/src/main/yamcs/etc/processor.yaml) | `StreamTmPacketProvider`, `StreamTcCommandReleaser`, parameter and algorithm services |
| [yamcs.pus.yaml](../examples/pus/src/main/yamcs/etc/yamcs.pus.yaml) | Links, preprocessors/postprocessors, MDB loader list, instance services and streams |
| [pus.xml](../examples/pus/src/main/yamcs/mdb/pus.xml) | Common packet containers, TC base, embedded TC type and verifiers |
| [dt.xml](../examples/pus/src/main/yamcs/mdb/dt.xml) | Shared TM/TC types |
| [PusSimulator](../simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java) | Spacecraft emulation, in the `simulator` module |

There are no `org.yamcs.pus.PusCommandReleaser`, `PusTcHandler`, `Pus5Service`, or
`Pus21RequestSequencingService` classes in this checkout. Do not configure them as processor
services. The service-specific simulator classes are in `org.yamcs.simulator.pus`.

## TC pipeline

The commanding manager builds a command from a MetaCommand, records it in command history,
and queues/releases it through `StreamTcCommandReleaser`. The TC stream feeds the configured
TC link. `PusCommandPostprocessor` finalizes the packet at that link: length, sequence count,
optional checksum, annotated embedded packets, and optional ST[11]/ST[22] wrappers.

`binary`, `ccsds-apid`, and `ccsds-seqcount` identify the postprocessed original command.
Scheduling adds separate `pus11Binary`/`pus11Apid`/`pus11CcsdsSeqCount` or equivalent `pus22*`
attributes. Nested annotated commands publish `ccsds-seqcount:<argument path>`.

See the [postprocessor manual](../docs/server-manual/links/command-postprocessor/pus.rst) and
[current ST[11] configuration](pus11.md). A raw command flag does not by itself suppress
postprocessing; callers supplying finalized packets must consider `disablePostprocessing`.

## Embedded TCs

The shared `/PUS/EmbeddedTcType` is a binary argument type marked with
`AncillaryData name="Yamcs:EmbeddedTc"`. The postprocessor uses recorded argument locations,
including members inside aggregate arrays, to finalize embedded packets before the outer
packet. It fills the CCSDS length and sequence count and, when `embeddedTcCrc` is enabled,
appends the checksum and shifts following bytes.

Supply pre-postprocessing packet bytes without an existing CRC when checksum append is enabled.
Embedded packets must be byte-aligned; an encoding must permit growth to append a checksum.
A fixed-size or another-argument-sized binary fails when checksum growth is requested.
Absolute-position entries after an embedded packet are not shifted.

This is used by ST[19]. ST[21]'s `tc_packet_arg_type` is currently an unannotated binary type,
so ST[21] callers still need to provide finalized embedded bytes. The proposed
`Pus21EmbeddedEntryBuilder` is absent. See [ST[19]](pus19.md) and [ST[21]](pus21.md).

## TM pipeline

`TcpTmDataLink` receives packets and invokes `PusPacketPreprocessor` for checksum handling,
time extraction, sequence checking and time-correlation integration. The resulting TM stream
feeds both `StreamTmPacketProvider`/`XtceTmExtractor` for live parameters and `XtceTmRecorder`
for packet archiving. `PusEventDecoder` independently turns ST[05] event reports into native
Yamcs events using the MDB and YAML/JSON templates.

Injecting a tuple directly into a TM stream bypasses preprocessing. It does not demonstrate
that link checksum checks or time correlation work. `StandardTupleDefinitions.TM` contains
nine columns; a tuple must match the actual definition's column count. The normal configured
link path constructs these tuples; service guides should not fabricate spacecraft telemetry
on the ground as a substitute for receiving it.

## XTCE values and verification

Predefined structures are described by XTCE containers, aggregate types and arrays. Nested TM
arrays can use [aggregate member dimensions](aggregate_member_value.md). Ordinary XML dynamic
references in `DynamicValue` use current-entry lookup when `instance` is omitted, allowing
per-repetition counts and lengths. An explicit non-negative `instance` selects from packet
start. The PUS example MDBs still use repeated containers in several services.

`Pus1Verifier` correlates the first five inputs, in order: sent APID, sent sequence count,
received APID, received sequence count, report subtype. Configure the extra input with the
exact alias `reportType` so non-ST[01] packets are ignored. For compatibility, omitting it
currently logs a warning and skips that guard. `stage` selects acceptance/start/progress/
completion (1/3/5/7). Optional `ackFlags` is an aggregate input for suppressing stages whose
configured boolean flag is false. APID matching uses raw wire values where available.

A report container verifier only proves that its matching container arrived. Without a request
identifier in that report, it cannot independently correlate simultaneous requests. ST[17]'s
empty TM[17,2] is an example of this limitation.

## State and persistence

Yamcs archives received TM, command history, parameters and events through its configured
archive services. `MementoDb` can persist ground-service state, but it does not mirror an
on-board registry automatically. Simulator event definitions, sequences, parameter values,
forwarding tables and packet stores are held by their respective Java services. Do not infer
restart durability from the presence of a Yamcs archive.

## Build and verification

From the repository root:

```bash
mvn -pl simulator,examples/pus -am install -DskipTests
mvn -pl examples/pus yamcs:run
```

The second command starts the example and embedded simulator; the first prepares artifacts and
skips tests. The UI is at `http://localhost:8090`, instance `pus`.

For a new service, verify MDB loading, outgoing application data, received reports, ST[01]
request correlation, zero/multiple entries and error responses separately. Existing tests live
under `yamcs-core/src/test/java/org/yamcs/pus`, `yamcs-core/src/test/java/org/yamcs/mdb`, and
`simulator/src/test/java/org/yamcs/simulator/pus`. A manual walkthrough is a procedure, not a
record of a passing test run.
