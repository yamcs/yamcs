# PUS Java simulator architecture

Reviewed against commit `90d4a3d1c` on 2026-11-18. The simulator emulates spacecraft behavior
for the Yamcs ground example. Ground configuration is described in [pus_native_arch.md](pus_native_arch.md).

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

`PusSimulator` constructs ST[02,03,05,09,11,12,13,14,15,17,19,20,21,22] services. ST[06] is
still a proposal. Its `doStart()` creates one `ScheduledThreadPoolExecutor(1)`, schedules command
processing every 200 ms, and starts the services requiring periodic work. Long blocking sleeps
would delay every service; retrieval and sequence work use cancellable scheduled tasks.

A service extends `AbstractPusService`, implements `executeTc(PusTcPacket)`, and overrides
`start()` if needed. `newPacket(subtype, userDataLength)` uses the simulator's time encoding.
Use `ack_start`, `nack_start`, `ack_completion`, and `nack_completion` for ST[01] reports;
progress helpers also exist. Check error constants in the actual service: their values are
not a universal cross-service enumeration.

## Packet layouts

Offsets below refer to a CCSDS packet, excluding transport framing. The default explicit
CUC P-field is `0x2F`: one P-field byte + four coarse bytes + three fine bytes = **8 bytes**.

| Packet | Layout | Default application-data offset |
|---|---|---|
| PUS TC | 6-byte primary + 5-byte secondary + application data + 2-byte CRC | 11 |
| Ordinary TM | 6-byte primary + 7-byte pre-time secondary + CUC time + source data + 2-byte CRC | 21 |
| ST[09] time TM | 6-byte primary + 1-byte rate exponent + CUC time + 2-byte CRC | Time starts at 7; total length 17 |

Ordinary TM service type/subtype are bytes 7/8. TC service type/subtype are also bytes 7/8;
byte 6 is the version/acknowledgement field, not the service type. The CCSDS length field is
`total packet length − 7`. Zero service data still leaves the headers and checksum.

Ordinary TM offset is `13 + timeEncoding.getEncodedLength()`. Use the packet's
`getUserDataOffset()`/`getUserDataBuffer()` with custom time encoding instead of assuming the
static default `DATA_OFFSET`. Epoch and CUC settings must agree with the ground MDB and link
preprocessor; `tco0` supplies correlation for the example's arbitrary simulator time.

Service-data APIDs in ST[11,14,15,17] currently use `/dt/pus_report_apid` (11 bits), and request
sequence counts use `/dt/pus_report_seqcount` (14 bits). Fields are packed without per-field
byte padding. ST[19]'s event-system `app_process_id` remains a uint16 mission field. Keep these
service layouts distinct from the fixed 11-bit primary-header APID.

## Outgoing TM, storage and forwarding

`transmitRealtimeTM()` performs these steps in order:

1. Fill the packet checksum.
2. Call `pus15Service.submitToStores(packet)` regardless of real-time forwarding permission.
3. Apply `pus14Service.shouldForward(packet)`.
4. Send through the configured packet TCP link and/or frame link.

ST[15] retrieval uses `sendStoredPacket()` and bypasses storage recapture and the ST[14] gate.
ST[09] uses its own immediate packet/frame path. These exceptions mean a gate inserted in the
normal TM path does not filter every byte sent by the simulator.

ST[14] permits absent APIDs by default and always permits ST[01] and ST[14] reports. Its HK and
diagnostic tables are bookkeeping; `shouldForward()` applies APFCC and event blocking. ST[15]
has a different default: an empty application-process storage configuration blocks storage.
Enable storage and configure selection before expecting the store to fill.

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

ST[19] and ST[21] release stored embedded TCs through `PusSimulator.processTc()`. Those releases
produce TM verification reports, but do not automatically create independently issued ground
commands in command history. The flight-like target behavior is modeled in Java; the ground
encodes TCs and decodes reports through the configured MDB.

## Build and run

See [build and verification](pus_native_arch.md#build-and-verification). Tests that pass without
running a live example are distinct from manual end-to-end checks. Service pages describe
current layouts and remaining deviations; consult the [review index](README.md) before copying
older research snippets into a mission MDB.
