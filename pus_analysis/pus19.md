# PUS ST[19] — Event-action service

Reviewed against commit `90d4a3d1c` on 2026-11-18. Yamcs configures event actions through XTCE
TCs and decodes status/definition reports. Event detection, definition state and autonomous
release run in the spacecraft, modeled here by the Java simulator.

## Current sources

- [pus19.xml](../examples/pus/src/main/yamcs/mdb/pus19.xml)
- [Pus19Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus19Service.java)
- [Pus5Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus5Service.java)
- [Pus19ServiceTest](../simulator/src/test/java/org/yamcs/simulator/pus/Pus19ServiceTest.java)
- [Embedded TC postprocessor](pus_native_arch.md#embedded-tcs)

## Packet contract

The event-system identifier is `(app_process_id:u16, event_def_id:u16)`. These are the example's
mission fields; do not replace the former with an 11-bit field merely because primary-header
APIDs have that width. Counts are uint8, status is uint8 (0 disabled, 1 enabled).

| Subtype | MetaCommand / TM | Service data |
|---|---|---|
| TC[19,1] | `TC_19_1_ADD` | `N`, N × `{app_process_id, event_def_id, request}` |
| TC[19,2] | `TC_19_2_DELETE` | `N`, N × event-system identifier |
| TC[19,4] | `TC_19_4_SPECIFIC` / `TC_19_4_ALL` | Specific: `N` + identifiers; all: empty |
| TC[19,5] | `TC_19_5_SPECIFIC` / `TC_19_5_ALL` | Specific: `N` + identifiers; all: empty |
| TC[19,6] | `TC_19_6_REPORT_STATUS` | Empty |
| TM[19,7] | `TM_19_7` | Count + `{app_process_id, event_def_id, ea_status}` entries |
| TC[19,8] | `TC_19_8_ENABLE_FUNCTION` | Empty |
| TC[19,9] | `TC_19_9_DISABLE_FUNCTION` | Empty |
| TC[19,10] | `TC_19_10_REPORT_DEFS` | `N` + identifiers; `N=0` requests all |
| TM[19,11] | `TM_19_11` | Count + `{identifier, status, complete embedded request}` entries |

Commands and containers are under `/PUS19/`. TC[19,3] is outside the example's scope.
A request is a complete CCSDS/PUS TC. Its own packet length at bytes 4–5 determines its size
(`length + 7` bytes). There is **no separate `request_len` argument or wire prefix**.

For TC[19,1], `request` uses `/PUS/EmbeddedTcType`, marked `Yamcs:EmbeddedTc`. Supply the encoded
inner command before CRC append; the link postprocessor fills its length, sequence count and
checksum and then finalizes the outer command. A command dry run is an encoding result, not
proof that link postprocessing has run. Appending a checksum to an already finalized inner
packet would add a second checksum.

Example argument shape, with `request` supplied in the client's binary representation:

```text
{N: 1, entries: [{app_process_id: 1, event_def_id: 1, request: <encoded inner TC bytes>}]}
```

TM[19,11] uses repeated `ea_def_entry` containers, reads ahead to the embedded CCSDS length,
then goes back to extract the full request as binary. Its `DynamicValue` reference omits
`instance`, so the reader now selects the most recently decoded length for each entry. The
regression test `DynamicParameterReferenceTest.pus19DefinitionReportDecodesUnequalEmbeddedPacketLengths`
checks a report containing 13-byte and 16-byte requests against this actual MDB. The former
first-occurrence sizing limitation is fixed. Explicit `instance="0"` still selects the first
occurrence when a database intentionally needs that behavior.

## Execution and rejection behavior

The global function starts disabled; new definitions also start disabled. A release needs
both gates enabled. ST[05] calls `pus19Service.onEvent()` directly; no separate event bus is
present. The simulator's originating application process is 1, so use `app_process_id=1` to
match its events. A matching action is passed to `PusSimulator.processTc()` and emits the
inner command's ST[01] reports. This does not issue a separate ground command-history entry.

The definition limit is **16**. Adding an existing disabled definition replaces its request;
adding over an enabled definition is rejected. Unknown definitions, deletion of enabled
definitions, capacity and malformed requests produce failed-start notifications per rejected
instruction. Valid instructions in a mixed batch still execute; successful completion is sent
if at least one instruction succeeded. A successful-start report is sent only when no
instruction was rejected.

| Failed-start condition | Code |
|---|---|
| Definition not found | 30 |
| Definition enabled | 31 |
| Maximum definitions reached | 32 |
| Invalid embedded request | 33 |

Earlier completion-error codes 5/6 and the claim that definitions are uncapped are obsolete.
The simulator checks the embedded packet header and length and validates its CRC when `embeddedTcCrc` is enabled.

## Manual checks

Start the [example](pus_native_arch.md#build-and-verification). Add a disabled definition for
`(1,1)` whose request is TC[17,1]. Report status, enable the global function and confirm the
still-disabled definition does not release. Enable that definition and observe an autonomous
TM[17,2] when event 1 occurs; disabling either gate must stop further releases without changing
the other gate's state. Disable before deleting. Check the four rejection codes, a mixed valid/
invalid batch, replacement of a disabled definition, and the 16-definition boundary.

Use both single-definition and unequal-length multi-definition TM[19,11] reports when checking
future changes. The regression covers heterogeneous lengths; a live simulator smoke test is
still a separate end-to-end check. State is in memory and resets at simulator restart.
