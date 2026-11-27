# PUS Java simulator architecture

The simulator emulates spacecraft behavior for the Yamcs PUS examples and direct mission configuration. Ground configuration is described in [pus_native_arch.md](pus_native_arch.md).

## Source map and dispatch

| Source | Purpose |
|---|---|
| [PusSimulator](../simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java) | Constructs services, starts periodic work, dispatches TCs |
| [AbstractPusService](../simulator/src/main/java/org/yamcs/simulator/pus/AbstractPusService.java) | `executeTc`, `start`, packet creation, ST[01] helpers |
| [PusTcPacket](../simulator/src/main/java/org/yamcs/simulator/pus/PusTcPacket.java) | TC header and application-data access |
| [PusTmPacket](../simulator/src/main/java/org/yamcs/simulator/pus/PusTmPacket.java) | Ordinary TM construction, time and checksum |
| [PusTmTimePacket](../simulator/src/main/java/org/yamcs/simulator/pus/PusTmTimePacket.java) | APID-zero time report, no secondary header |
| [PusTimeEncoding](../simulator/src/main/java/org/yamcs/simulator/pus/PusTimeEncoding.java) | Configurable CUC width, P-field and simulator/UNIX epoch |
| [PusPackedFields](../simulator/src/main/java/org/yamcs/simulator/pus/PusPackedFields.java) | Packed service-data integer fields |

`PusSimulator` constructs ST[02,03,05,09,11,12,13,14,15,17,19,20,21,22,128,130] services. It acknowledges exported TC[131,4] commands with ST[01] reports. ST[06] is a proposal. Its `doStart()` creates one `ScheduledThreadPoolExecutor(1)`, schedules command
processing every 200 ms, and starts the services requiring periodic work. Long blocking sleeps
would delay every service; retrieval and sequence work use cancellable scheduled tasks.

A service extends `AbstractPusService`, implements `executeTc(PusTcPacket)`, and overrides
`start()` if needed. `newPacket(subtype, userDataLength)` uses the simulator's time encoding.
Use `ack_start`, `nack_start`, `ack_completion`, and `nack_completion` for ST[01] reports;
progress helpers also exist. Check error constants in the actual service: their values are
not a universal cross-service enumeration.

## Packet layouts

Offsets count from the start of the CCSDS packet. The examples use explicit CUC with P-field
`0x2F` and eight encoded bytes. The mission profile uses implicit CUC with P-field `0x2E` and six encoded bytes.

| Packet | Layout | Example offset | Mission offset |
|---|---|---:|---:|
| PUS TC | 6-byte primary + 5-byte secondary + 4 spare bytes + service data + optional CRC | 15 | 15 |
| Ordinary TM | 6-byte primary + 7 fixed secondary bytes + CUC + 4 spare bytes + service data + optional CRC | 25 | 23 |
| APID-zero time TM | 6-byte primary + 1-byte rate exponent + CUC + optional CRC | Time at 7 | Time at 7 |

Ordinary TM and TC service type and subtype are at bytes 7 and 8. CCSDS packet length is the
total number of bytes minus seven. The spare field follows the TC source ID or TM CUC time.
ST[11], ST[15], and ST[130] TM use two big-endian unsigned 16-bit pagination fields. The other
services and TC wrappers put zeros there. The APID-zero time packet uses its own header layout.

The simulator packet buffer methods expose the service data alone. Use `getUserDataOffset()`
when indexing a TM packet and keep the active MDB in sync with the configured spare width and
CUC layout. The frame receiver and the packet preprocessor each validate their own checksums.

Service-data APIDs in ST[11,14,15,17] currently use `/dt/pus_report_apid` (11 bits), and request
sequence counts use `/dt/pus_report_seqcount` (14 bits). Fields are packed without per-field
byte padding. ST[19]'s event-system `app_process_id` remains a uint16 mission field. Keep these
service layouts distinct from the fixed 11-bit primary-header APID.

## Outgoing TM, storage and forwarding

`transmitRealtimeTM()` performs these steps in order:

1. Fill the packet checksum when enabled.
2. Apply ST[130] storage eligibility and submit eligible packets to ST[15].
3. Apply both ST[130] transmission eligibility and ST[14] forwarding controls.
4. Send eligible packets through the configured TCP or frame link.

ST[15] retrieval and ST[130] packet-store dump slots use `sendStoredPacket()` to replay finalized
bytes. ST[09] sends directly through the configured link. ST[14] applies application-process,
housekeeping, diagnostic, and event forwarding controls. An empty ST[15] application-process
selection table captures no packets. Enable storage and configure selection before expecting
the store to fill.

## Nested structures in XTCE

TC aggregate arrays use sibling `ArgumentInstanceRef` counts. For TM, a structured aggregate
with nested arrays can use `Yamcs:AggregateMember`; see the complete
[AggregateMemberValue guide](aggregate_member_value.md).

The existing example PUS MDBs instead use repeated containers for many variable reports.
Their dynamic count/length references now use the latest decoded occurrence when `instance`
is omitted. An explicit `instance="0"` deliberately selects the first occurrence from packet
start. Aggregate-member dimensions instead access in-progress aggregate members and preserve
a nested value structure; they are not required for the ordinary repeated-container lookup.

## Cross-service behavior

| Service | State / coupling |
|---|---|
| ST[03] | HK/diagnostic structure registries and periodic reports |
| ST[05]/ST[19] | `Pus5Service` directly calls `pus19Service.onEvent`; no separate event bus |
| ST[12] | Monitoring definitions sample simulator data and raise ST[05] events |
| ST[13] | One uplink reassembly; periodic synthetic fragmented downlinks |
| ST[14] | In-memory forwarding configurations, central TM gate |
| ST[15] | Per-store contents and selection, scheduled retrieval |
| ST[20] | In-memory uint16 ID → uint32 value map |
| ST[21] | In-memory sequence/file maps and cancellable execution chain |
| ST[128] | TLE set, report and checksum validation |
| ST[130] | TM mode, structure eligibility, event overlays and timed pass slots |
| ST[131] | ST[01] verification for TC[131,4] |

## Flight MDB telemetry

Set `pus.telemetry.mdbFiles` to list the XTCE files the simulator should load together.
Use the same XTCE inputs as the Yamcs instance's `mdb` configuration. For a single
file, `pus.telemetry.mdbFile` is also supported. Configure one of these two settings.
The pacing options are `housekeepingPeriodMillis` and `eventPeriodMillis`.

The simulator registers the fixed-size
typed TM[3,25/26] reports by APID, subtype and structure ID. A period of 200 ms staggers
the whole catalog in a cycle, so each of 153 entries repeats approximately every 30.6 seconds.
ST[03] schedule changes and ST[130] generation controls apply to those registrations.
TM[5,1–4] rotates through typed events, and ST[05] enable or disable commands act on the
event IDs for the addressed APID. Emitted events notify ST[19] and ST[130]. The report values
are synthetic and the service data is laid out using the loaded MDB. The simulator checks the
MDB source-data offset against the configured CUC and spare-field widths at startup.

ST[19] and ST[21] release stored embedded TCs through `PusSimulator.processTc()`. Those releases
produce TM verification reports, but do not automatically create independently issued ground
commands in command history. The flight-like target behavior is modeled in Java; the ground
encodes TCs and decodes reports through the configured MDB.

## Build and run

See [build and verification](pus_native_arch.md#build-and-verification). Tests that pass without
running a live example are distinct from manual end-to-end checks. Service pages describe
current layouts and remaining deviations; consult the [review index](README.md) before copying
older research snippets into a mission MDB.
