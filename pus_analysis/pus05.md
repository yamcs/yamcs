# PUS ST[05] — Event reporting

Reviewed against commit `90d4a3d1c` on 2026-11-18. ST[05] reports events and controls their
on-board generation status. Yamcs receives the reports; it does not maintain the spacecraft's
enabled/disabled registry locally.

## Messages and sources

| Message | Ground role |
|---|---|
| TM[5,1–4] | Extract event ID and auxiliary parameters, optionally emit native Yamcs events |
| TC[5,5/6] | Enable/disable event report generation on board |
| TC[5,7] | Request disabled-event list, no application data |
| TM[5,8] | Decode disabled count and event ID array |

- [pus5.xml](../examples/pus/src/main/yamcs/mdb/pus5.xml) defines the packet types and commands.
- [PusEventDecoder](../yamcs-core/src/main/java/org/yamcs/pus/PusEventDecoder.java) is the
  instance-level ground service producing native events.
- [Pus5Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus5Service.java) models
  on-board generation and the enabled/disabled map.
- [events.json](../examples/pus/src/main/yamcs/etc/events.json) supplies example templates.

TC[5,5/6] and TM[5,8] carry an 8-bit count followed by event IDs. The example event IDs occupy
one byte. Auxiliary data depends on the event definition; event-specific containers supply
its interpretation. A mission with different ID widths or payloads must change the MDB and
the spacecraft contract together.

## Native event conversion

The ground processor uses `StreamTcCommandReleaser`; commands reach the TC link rather than
being handled by a local ST[05] on-board service. `PusEventDecoder` observes TM streams,
filters to service 5/subtypes 1–4, extracts parameters and formats an event using its template
configuration. `eventTemplateFile` accepts YAML or JSON; the example uses `etc/events.json`.

| PUS report subtype | Yamcs severity |
|---|---|
| 1 | INFO |
| 2 | WATCH |
| 3 | DISTRESS |
| 4 | CRITICAL |

The native event path is additional to normal packet/parameter decoding. TM[5,8] must not
be promoted to an event. `eventIdParameter` can be an enumerated or integer parameter;
enumerated IDs are looked up by engineering label, and plain integer IDs by their numeric value (including unsigned uint64 formatting). A template and the auxiliary field definitions must
agree with the mission MDB. Unknown event payload schemas are not inferred automatically.

## Verification and limitations

The example does not define a response-container verifier for `REPORT_DISABLED_LIST`;
ST[01] completion and arrival of TM[5,8] are separate checks. Configure the exact `reportType`
input on `Pus1Verifier` algorithms so unrelated service packets cannot satisfy/fail them.
This guard is skipped with a warning when the input is absent for compatibility.

Start the [example](pus_native_arch.md#build-and-verification). Disable a known event, request
the disabled list, enable it again, and check a zero-entry list. Check native event severity
and rendered auxiliary fields separately from TM receipt. Existing automated checks include
`yamcs-core/src/test/java/org/yamcs/pus/PusEventDecoderTest.java` (present at the reviewed baseline) and the
live-client walkthrough `examples/pus/tests/test-pus5.py`.

Event-specific auxiliary decoding, on-board occurrence counters and persistent reconciliation
of generation status remain mission concerns. The simulator registry is not made durable by
the ground event archive.
