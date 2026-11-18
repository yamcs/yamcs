# PUS ST[14] — Real-time forwarding control

Reviewed against commit `90d4a3d1c` on 2026-11-18. The ground MDB is
[pus14.xml](../examples/pus/src/main/yamcs/mdb/pus14.xml); the on-board emulation is
[Pus14Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus14Service.java).
Yamcs sends configuration TCs and decodes returned reports; the simulator decides which
live TM packets to transmit.

## Message groups and packed fields

| Configuration | Add / delete / report TCs | Report |
|---|---|---|
| Application process / service / subtype (APFCC) | 1 / 2 / 3 | TM[14,4] |
| HK structure IDs | 5 / 6 / 7 | TM[14,8] |
| Diagnostic structure IDs | 9 / 10 / 11 | TM[14,12] |
| Event blocking IDs | 14 / 13 / 15 | TM[14,16] |

APIDs in service data are packed unsigned 11-bit values. Counts, service types, subtypes,
structure IDs and event IDs are unsigned 8-bit values. Fields after an APID start immediately
at the next bit; only the final source-data octet can contain padding. Nested TC arrays use
sibling `ArgumentInstanceRef` counts. Empty-table aliases for deletion still encode a fixed
`N1=0` octet; no user arguments does not necessarily mean zero application data.

Reports are atomic: an oversized configuration returns a report-too-large completion failure
instead of a partially transmitted configuration. An empty table still returns one report
with `N1=0`. The loaded MDB currently represents nested TM with container repeats.

## Forwarding semantics and limitations

`PusSimulator.transmitRealtimeTM()` fills CRC, submits the packet to ST[15] stores, then checks
`Pus14Service.shouldForward()`. Blocked real-time TM can therefore still be stored. Retrieval
replay and APID-zero ST[09] time packets bypass the live forwarding path.

The simulator always allows ST[01] verification and ST[14]'s own reports. It also allows an
absent APID entry, unlike the strict missing-definition block behavior described in the
original PUS research. An APID entry with no service definitions allows all its services;
a defined service with no subtype restrictions allows all its subtypes. Otherwise the
service/subtype must be listed. Event blocking is applied after APFCC authorization.

HK/diagnostic configuration management is implemented, but those tables are not consulted
by `shouldForward()`. The previous explanation that HK reports have no structure IDs was
wrong: ST[03] sends a uint32 `/PUS/hkid`. The ST[14] tables use uint8 structure IDs; connecting
them requires an explicit mapping/width decision and forwarding integration. Diagnostic
parameter reports belong to ST[03] (TM[3,26]), not ST[04]. This remains an implementation gap;
optional-capability wording does not establish that a partially implemented capability is
conformant.

The original assertion that nested TM aggregate arrays cannot work is obsolete: Yamcs supports
[Yamcs:AggregateMember array dimensions](aggregate_member_value.md). The current PUS14 MDB
uses repeated containers instead. Ordinary XML `DynamicValue` references without `instance`
now select the most recently decoded count for each repetition. Explicit `instance="0"` still
selects the first count. Unequal counts remain an important regression case when changing the
packed layout; they no longer require conversion to aggregate-member dimensions for lookup.

## Integration checks

The following current command examples are useful for checking packed layout and simulator
behavior. They do not establish full standard conformance or heterogeneous-count decoding.

### e.1 Start the instance

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests   # first build only
mvn -pl examples/pus yamcs:run
```
Web UI: `http://localhost:8090`, instance `pus`. Commands live under `/PUS14/...`, TM containers
under the same `/PUS14/` subsystem (see e.3).

### e.2 Command reference — valid inputs

All commands are under `/PUS14/`. The nested `N1`/`n2`/`n3`/`n_structs` count fields must be
supplied explicitly alongside their corresponding arrays — YAMCS does not infer them from array
length (same convention as PUS12's `N`/`pmon_ids`).

| Command | Subtype | Valid example args |
|---|---|---|
| `TC_14_1_ADD_REPORT_TYPES` (specific report type) | TC[14,1] | `{"N1": 1, "apfcd_entries": [{"apid": 1, "n2": 1, "service_types": [{"service_type": 3, "n3": 1, "subtypes": [25]}]}]}` — authorizes HK reports (type=3, subtype=25) for apid=1 |
| `TC_14_1_ADD_REPORT_TYPES` (all subtypes of a service) | TC[14,1] | `{"N1": 1, "apfcd_entries": [{"apid": 1, "n2": 1, "service_types": [{"service_type": 5, "n3": 0, "subtypes": []}]}]}` — authorizes all ST[05] event subtypes |
| `TC_14_1_ADD_REPORT_TYPES` (all services of an APID) | TC[14,1] | `{"N1": 1, "apfcd_entries": [{"apid": 1, "n2": 0, "service_types": []}]}` — authorizes everything for apid=1 (equivalent to pass-all) |
| `TC_14_2_DELETE_ENTRIES` | TC[14,2] | `{"N1": 1, "apfcd_entries": [{"apid": 1, "n2": 1, "service_types": [{"service_type": 3, "n3": 1, "subtypes": [25]}]}]}` — revokes just HK forwarding, leaving other authorized services intact |
| `TC_14_2_EMPTY_APFCC` | TC[14,2] | `{}` (no arguments) — clears the whole APFCC, reverting to pass-all default |
| `TC_14_3_REPORT_APFCC` | TC[14,3] | `{}` (no arguments) |
| `TC_14_5_ADD_HK_STRUCTS` | TC[14,5] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_structs": 1, "struct_ids": [100]}]}` |
| `TC_14_6_DELETE_HK_ENTRIES` | TC[14,6] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_structs": 1, "struct_ids": [100]}]}` |
| `TC_14_6_EMPTY_HK_FCC` | TC[14,6] | `{}` (no arguments) |
| `TC_14_7_REPORT_HK_FCC` | TC[14,7] | `{}` (no arguments) |
| `TC_14_9_ADD_DIAG_STRUCTS` | TC[14,9] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_structs": 1, "struct_ids": [200]}]}` |
| `TC_14_10_DELETE_DIAG_ENTRIES` | TC[14,10] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_structs": 1, "struct_ids": [200]}]}` |
| `TC_14_10_EMPTY_DIAG_FCC` | TC[14,10] | `{}` (no arguments) |
| `TC_14_11_REPORT_DIAG_FCC` | TC[14,11] | `{}` (no arguments) |
| `TC_14_13_DELETE_EVENT_ENTRIES` | TC[14,13] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_events": 1, "event_ids": [2]}]}` |
| `TC_14_13_EMPTY_EVENT_FCC` | TC[14,13] | `{}` (encodes fixed `N1=0`) |
| `TC_14_14_ADD_EVENT_ENTRIES` | TC[14,14] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_events": 1, "event_ids": [2]}]}` |
| `TC_14_15_REPORT_EVENT_FCC` | TC[14,15] | `{}` (no arguments) |

Rejection conditions to exercise (all respond NACK completion, not NACK start — the command is
accepted then rejected during execution; see the `Pus14Service` completion error codes): deleting/
narrowing an APID not present in the APFCC (`COMPL_ERR_APID_NOT_IN_APFCC` = 5), deleting a service
type not present in that APFCD (`COMPL_ERR_SVC_NOT_IN_APFCD` = 6), deleting an APID not present in
the HK FCC (`COMPL_ERR_APID_NOT_IN_HK_FCC` = 7) or the Diagnostic FCC
(`COMPL_ERR_APID_NOT_IN_DIAG_FCC` = 8). An unrecognized subtype gets NACK **start**
(`START_ERR_INVALID_PUS_SUBTYPE`) instead, since the command is rejected before execution begins.
Event-FCC failures use codes 9–11, and an atomic report that exceeds a count or TM-size limit uses
`COMPL_ERR_REPORT_TOO_LARGE` = 12.

### e.3 TMs to check

| Container | Subtype | Triggered by | Layout |
|---|---|---|---|
| `/PUS14/TM_14_4` | TM[14,4] | `TC_14_3_REPORT_APFCC` | `N1:u8`, then N1 × `{apid:u11, n_services:u8, services…}`; all fields are contiguous bits |
| `/PUS14/TM_14_8` | TM[14,8] | `TC_14_7_REPORT_HK_FCC` | `N1:u8`, then N1 × `{apid:u11, n_structs:u8, struct_ids:u8[n_structs]}` |
| `/PUS14/TM_14_12` | TM[14,12] | `TC_14_11_REPORT_DIAG_FCC` | Same layout as TM[14,8], for the Diagnostic FCC |
| `/PUS14/TM_14_16` | TM[14,16] | `TC_14_15_REPORT_EVENT_FCC` | `N1:u8`, then N1 × `{apid:u11, n_events:u8, event_ids:u8[n_events]}` |

If any configuration is empty, its report TC produces exactly one TM packet whose source data is
the single octet `N1=0`.

Also watch the standard PUS-1 verification containers (`/PUS/pus-tc-ack-*`) for ACK/NACK
start/completion of every TC[14,x] above — see e.4 step 4 for why these should never silently
disappear regardless of APFCC configuration.

### e.4 Suggested manual test walkthrough

1. **Baseline (pass-all)**: with a freshly started instance and no TC[14,1] sent yet, confirm HK
   reports (type=3/subtype=25) and events (type=5) keep flowing normally — `apfcc` is empty, so
   `shouldForward()` returns `true` for every APID.
2. **Narrow the gate**: send `TC_14_1_ADD_REPORT_TYPES` with the "specific report type" args from
   e.2, authorizing *only* HK reports (type=3/subtype=25) for apid=1. Confirm HK reports keep
   arriving, but events (type=5) and diagnostic reports (type=3/subtype=26) stop.
3. **Verify the dump**: add two APID groups, send `TC_14_3_REPORT_APFCC`, and check that one
   `/PUS14/TM_14_4` reports `N1=2` followed by both packed APID definitions.
4. **Verify the PUS-1/ST14 exemption**: with the restrictive APFCC from step 2 still active (which
   does *not* authorize type=1 or type=14), confirm ACK/NACK verification reports for every command
   you send, and the `TM_14_4` report itself, still arrive — `shouldForward()` special-cases
   `type == 1 || type == 14` before consulting the APFCC at all.
5. **Widen back**: send `TC_14_1_ADD_REPORT_TYPES` again with the "all subtypes of a service" args
   from e.2 for service_type=5 (events), and confirm events resume while HK stays restricted to
   subtype 25 only — adding is additive, not a reset (see `addReportTypes`).
6. **Delete + rejection**: send `TC_14_2_DELETE_ENTRIES` targeting an `apid` never added (e.g. 2)
   and confirm a NACK completion with code 5 (`COMPL_ERR_APID_NOT_IN_APFCC`).
7. **Empty the table**: send `TC_14_2_EMPTY_APFCC` (no args) and confirm HK and events both return
   to flowing freely (pass-all default restored).
8. **HK/Diag FCC is bookkeeping only**: send `TC_14_5_ADD_HK_STRUCTS` then `TC_14_7_REPORT_HK_FCC`,
   confirm `/PUS14/TM_14_8` reflects the added struct id — but also confirm actual HK TM
   (type=3/subtype=25) is completely unaffected by this table (see the limitations above): HK forwarding uses APFCC, never `hkFcc`/`diagFcc`; event reports also have event-blocking rules.
9. **Event blocking**: add event ID 2 for APID 1 with `TC_14_14_ADD_EVENT_ENTRIES`; confirm event 2
   stops while event 1 continues, and confirm TM[14,16] reports the definition. Then send
   `TC_14_13_EMPTY_EVENT_FCC` and confirm both events flow again.

### e.5 Caveats specific to this simulator

- **Single normal report APID**: ordinary generated TM uses `MAIN_APID = 1`
  (`PusSimulator.newPacket` / the `pus-tc` argument assignment), so in practice every APFCC/HK
  FCC/Diag FCC entry created during testing will have `apid=1`. The per-APID dimension of ST[14] is
  exercised structurally (both the XTCE and the Java support arbitrary APIDs) but not observably
  multi-APID in this environment.
- **TM[9,2] time packets bypass the gate entirely**: `Pus9Service` sends `PusTmTimePacket` via
  `pusSimulator.tmLink.sendImmediate(...)`, not `transmitRealtimeTM()`, so CUC time packets are
  never subject to any APFCC configuration — they keep flowing even under a fully restrictive setup.
- **HK FCC / Diagnostic FCC don't gate anything yet**: APFCC and event blocking are applied by
  `shouldForward()`; HK/diagnostic tables are not. TC[14,5-12] maintain real in-memory tables and answer real dump reports, but
  have no effect on which TM actually reaches the ground until the forwarding gate is explicitly integrated with the applicable structure IDs. ST[03] already carries a uint32 hkid; the ST[14] structure tables use uint8 IDs, so a mapping/width decision is also needed.
