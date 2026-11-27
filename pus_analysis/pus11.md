# PUS ST[11] — Time-based scheduling

Ground scheduling support consists of XTCE
commands/reports and optional TC[11,4] wrapping in `PusCommandPostprocessor`. The on-board
scheduler is emulated by `simulator/.../Pus11Service.java`.

## Current sources

- [Postprocessor and configuration manual](../docs/server-manual/links/command-postprocessor/pus.rst)
- [PusCommandPostprocessor](../yamcs-core/src/main/java/org/yamcs/pus/PusCommandPostprocessor.java)
- [pus11.xml](../examples/pus/src/main/yamcs/mdb/pus11.xml) and [base verifiers](../examples/pus/src/main/yamcs/mdb/pus.xml)
- [Pus11Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus11Service.java)
- [Postprocessor tests](../yamcs-core/src/test/java/org/yamcs/pus/PusCommandPostprocessorTest.java)
  and [scheduler tests](../simulator/src/test/java/org/yamcs/simulator/pus/Pus11ServiceTest.java)

## Scheduling an existing command

The `pus11ScheduleAt` timestamp command option wraps a postprocessed original command into
TC[11,4]. Enable it with a `pus11` map in the link's postprocessor arguments:

```yaml
commandPostprocessorClassName: org.yamcs.pus.PusCommandPostprocessor
commandPostprocessorArgs:
  errorDetection: {type: CRC-16-CCIIT}
  spareBytes: 4
  timeEncoding: {implicitPfield: false, pfield: 0x2f}
  tcoService: tco0
  pus11Apid: 1
  pus11:
    sourceId: 0
    ackFlags: 0xD
    subScheduleId: {bytes: 1, default: 0}
    groupId: {bytes: 1, default: 0}
```

This matches the example's one-byte sub-schedule/group layout. Each ID block is independent:
omit it to omit that field. Configured widths are 1–8 bytes. The matching per-command option
names are `pus11SubScheduleId` and `pus11GroupId`; capitalization matters. `pus11Apid` is
optional and falls back to the original command APID. Set it explicitly when the scheduler
has a dedicated APID. `pus11Crc` applies to any outer TC[11,4], including one encoded by the
MDB. `packetCrc` controls ordinary top-level TCs. `embeddedTcCrc` controls the inner TC in
each path. These checksum options require an `errorDetection` algorithm when enabled.

The former top-level `pus11SourceId`, `pus11AckFlags`, `pus11SubscheduleId`, and `pus11GroupId`
configuration keys are rejected at initialization. Scheduling options are registered when
the corresponding configuration blocks are present. Time and position scheduling options
cannot be combined on one command.

## Wrapper bytes and history

```text
CCSDS primary (6 B) + PUS TC secondary (5 B) + spare (4 B)
+ optional sub-schedule ID + N:u8=1
+ optional group ID + release time + finalized original TC + optional wrapper CRC
```

Overhead is `12 + spareBytes + subScheduleIdBytes + groupIdBytes + encodedTimeLength + crcBytes`.
The example's 1-byte IDs, 8-byte CUC, and 2-byte CRC give 28 bytes. With the same IDs,
a six-byte implicit release time and a disabled wrapper CRC give 24 bytes. The release time
comes from `tcoService.getObt()` when configured, otherwise the configured epoch conversion.
Unavailable correlation fails the command. The default postprocessor epoch is UNIX.

| Command-history attribute | Meaning |
|---|---|
| `binary`, `ccsds-apid`, `ccsds-seqcount` | Original command after postprocessing |
| `pus11Binary`, `pus11Apid`, `pus11CcsdsSeqCount` | TC[11,4] wrapper and its request ID |
| `ccsds-seqcount:<argument path>` | Annotated embedded command sequence count, when applicable |

The generic attributes are not overwritten with the wrapper ID. The example MDB contains a
separate scheduling verifier using the `pus11*` attributes; original-command verifiers retain
the original request ID. Inspect the check windows before scheduling commands far in the
future: verifying insertion and verifying eventual execution are separate events.

## Direct ST[11] commands and reports

The example MDB contains TC[11,1–9/11/12/14–18/20–26] and TM[11,10/13/19/27]. Use its exact
MetaCommand names/arguments rather than reconstructing payloads from historical snippets.
Direct insertion uses an array of activities, including a release time and an embedded TC.
The shared annotated embedded type is finalized by the postprocessor; see
[embedded TC processing](pus_native_arch.md#embedded-tcs).

Request identifiers in service data contain `source_id:u16`, `apid:u11`, `seqcount:u14`.
The APID and sequence count are packed, without padding between fields. These identify the
embedded request, not the surrounding TC[11,4].

The simulator starts with the execution function disabled. It now stores group membership,
checks group state when releasing activities, validates unknown groups and group limits, and
rejects deletion of groups containing activities. Earlier claims that groups are bookkeeping
only are obsolete. Due activities are removed even when a disabling gate prevents release;
disabling the scheduler is therefore not a queue-preserving pause.

## Checks and remaining limitations

Start the [example](pus_native_arch.md#build-and-verification), create/enable the required group
and sub-schedule, and enable the execution function before expecting releases. Check the
wrapper ST[01] reports against `pus11Apid`/`pus11CcsdsSeqCount`, then check the original request's
reports at release. An autonomous simulator release produces TM acknowledgements; it does not
create a newly issued ground command automatically.

Filter commands carry time bounds selected by the filter type, followed by counted
sub-schedule and group lists. An empty list selects every ID in its category. TM[11,10]
uses an 8-bit activity count and carries complete inner TCs. TM[11,13] uses a 16-bit count
and carries the packed request IDs. Both include one-based pagination in the TM spare field.
The ST[11] time and identifier widths must agree across the active MDB, simulator, and
postprocessor.

Several repeated report fields use ordinary XML dynamic references. Their instance policy
must be checked for differing counts/embedded lengths; see the [aggregate guide](aggregate_member_value.md).
Yamcs command queues are ground pre-release queues, not an authoritative mirror of the
spacecraft schedule. No ground schedule persistence or reconciliation is established by
these packet definitions.
