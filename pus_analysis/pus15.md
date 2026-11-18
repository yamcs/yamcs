# PUS ST[15] — On-board storage and retrieval

Reviewed against commit `90d4a3d1c` on 2026-11-18. Ground support is in
[pus15.xml](../examples/pus/src/main/yamcs/mdb/pus15.xml). The Java
[Pus15Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus15Service.java) implements
packet stores, selection tables, lifecycle and retrieval. Yamcs archives received packets
separately; its ground archive is not the on-board store.

## Coverage and mission conventions

Implemented groups include storage enable/disable (1/2), application selection (3–6),
by-time-range retrieval (9), content deletion/summary (11–13), open retrieval (14–17),
status/configuration (18–23), resize (25), VC change (28), and HK/diagnostic/event selection
(29–40). TC[15,24/26/27] are outside this example's scope. The wider standard's subtype range
must not be interpreted as every subtype being a TC or being implemented.

| Field | Current representation |
|---|---|
| Store identifier | uint16 |
| Store size | uint32 bytes |
| Store type / VC ID / list counts | uint8 |
| Selection-table APID | packed uint11 |
| Selection structure/event IDs | uint8 |
| Wire-visible storage/retrieval timestamps | uint64 raw milliseconds from simulator time encoding |

The default clock is elapsed simulator milliseconds. These timestamps are **not** 8-byte
CUC fields; `dt.xml` already provides the uint64 TM and TC types. A UNIX simulator epoch would
change their scale. This convention differs from interpreting the standard's time fields
as a universal CUC packet and requires a matching mission ICD.

Store creation always includes type and VC fields in this example, without the formerly
proposed extra presence-flags byte. For all/specific command variants, inspect each command's
actual common arguments: `_ALL` removes the store list, but commands such as delete-content
and change-start-time still carry their timestamp. Empty selection-table aliases encode
`store_id` plus a fixed `N1=0` count.

## Storage, selection and retrieval

New stores have storage disabled, open retrieval suspended and BTR disabled. Enabling a store
with an empty application-process selection table captures nothing. Configure TC[15,3]
first; HK/diagnostic structure and event rules further restrict eligible reports.

Normal outgoing TM is checksummed and submitted to stores **before** ST[14] live forwarding.
There is no separate packet-store manager or UDP forwarding shim. Retrieval uses the shared
single-thread scheduled executor, not one blocking thread per store. BTR steps are 100 ms;
open retrieval polls at 500 ms. Replayed packets go through `sendStoredPacket()`, bypassing
recapture and the live ST[14] gate. APID-zero time packets use another send path.

Circular stores can overwrite; bounded stores stop accepting packets at capacity. There is
a 64-store limit and active-store operations have restrictions. Selection content reports
TM[15,6/36/38/40] carry one store ID and complete nested configurations, with one empty report
for an empty table and a failure rather than partial data when a report is too large.

## Decoder and test limitations

Nested TM arrays can use [aggregate member dimensions](aggregate_member_value.md); the old
claim that only repeated containers work was incorrect. The current PUS15 MDB uses ordinary
XML references within repeats. Without an explicit `instance`, dynamic dimensions now use
the most recently decoded count; explicit `instance="0"` preserves first-occurrence lookup.
The aggregate-member convention is an alternative structured representation and has not been
applied to this example MDB. Keep unequal nested counts in layout regression checks.

The repository's `examples/pus/tests/test-pus15.py` covers the older lifecycle walkthrough.
It is not an acceptance gate for the now-implemented selection behavior. The procedures below
include manual selection checks; no live simulator result is implied by listing them.

## Integration checks

### g.1 Start the instance

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests   # first build only
mvn -pl examples/pus yamcs:run
```

TC[15,3] adds report types; TC[15,4] deletes them. TC[15,5] carries only
`packet_store_id` and requests TM[15,6]. `service_type` and the elements of
`subservices` are plain unsigned integers. The optional outer APID-selection
block is always present in this mission profile. Zero service/subservice
counts retain the whole-parent selection meaning defined for the operation.

All commands are under `/PUS15/`. `N` count fields must be supplied explicitly alongside their
corresponding arrays — YAMCS does not infer them from array length (same convention as PUS12's
`N`/`pmon_ids`). An "_ALL" variant omits `N` and the store-id array, but retains common fields such as `time_limit` or `start_time` when required. A "_SPECIFIC" variant adds `N` and the store-id array.

| Command | Subtype | Valid example args |
|---|---|---|
| `TC_15_20_CREATE_STORES` | TC[15,20] | `{"N": 2, "stores": [{"store_id": 100, "size_bytes": 200000, "store_type": "circular", "vc_id": 0}, {"store_id": 200, "size_bytes": 5000, "store_type": "bounded", "vc_id": 1}]}` |
| `TC_15_22_REPORT_CONFIG` | TC[15,22] | `{}` (no arguments) — dumps TM[15,23] for every store |
| `TC_15_1_SPECIFIC` | TC[15,1] | `{"N": 1, "store_ids": [100]}` — enable storage on store 100 |
| `TC_15_1_ALL` | TC[15,1] | `{}` — enable storage on every store |
| `TC_15_2_SPECIFIC` | TC[15,2] | `{"N": 1, "store_ids": [100]}` |
| `TC_15_2_ALL` | TC[15,2] | `{}` |
| `TC_15_3_ADD_REPORT_TYPES` | TC[15,3] | `{"store_id":100,"N1":1,"apid_entries":[{"apid":1,"n2":1,"services":[{"service_type":3,"n3":1,"subtypes":[25]}]}]}` |
| `TC_15_4_EMPTY` | TC[15,4] | `{"store_id":100}` — emits the fixed `N1=0` octet and restores the block-all application default |
| `TC_15_5_REPORT_APPLICATION_CONFIG` | TC[15,5] | `{"store_id":100}` — emits one atomic TM[15,6] |
| `TC_15_29_ADD_HK_STRUCTURES` | TC[15,29] | `{"store_id":100,"N1":1,"identifier_entries":[{"apid":1,"n2":1,"identifiers":[1]}]}` |
| `TC_15_30_EMPTY` | TC[15,30] | `{"store_id":100}` — clears the HK table by emitting fixed `N1=0` |
| `TC_15_31_ADD_DIAGNOSTIC_STRUCTURES` | TC[15,31] | Same argument shape as TC[15,29], with diagnostic structure IDs |
| `TC_15_32_EMPTY` | TC[15,32] | `{"store_id":100}` — clears the diagnostic table |
| `TC_15_34_ADD_EVENT_BLOCKS` | TC[15,34] | Same argument shape as TC[15,29], with 8-bit event IDs; `n2=0` blocks all events for the APID |
| `TC_15_33_EMPTY` | TC[15,33] | `{"store_id":100}` — clears the event-blocking table |
| `TC_15_35_REPORT_HK_CONFIG` / `TC_15_37_REPORT_DIAGNOSTIC_CONFIG` / `TC_15_39_REPORT_EVENT_CONFIG` | TC[15,35/37/39] | `{"store_id":100}` |
| `TC_15_18_REPORT_STATUS` | TC[15,18] | `{}` (no arguments) — dumps TM[15,19] for every store |
| `TC_15_12_SPECIFIC` | TC[15,12] | `{"N": 1, "store_ids": [100]}` — TM[15,13] for store 100 only |
| `TC_15_12_ALL` | TC[15,12] | `{}` — TM[15,13] for every store |
| `TC_15_9_START_BTR` | TC[15,9] | `{"N": 1, "btr_entries": [{"store_id": 100, "start_time": 0, "end_time": 9223372036854775807}]}` — dumps everything currently in store 100 (timestamps are raw millis, see timestamp convention above; `end_time` here is `2**63-1`) |
| `TC_15_17_SPECIFIC` | TC[15,17] | `{"N": 1, "store_ids": [100]}` — abort an in-progress BTR |
| `TC_15_17_ALL` | TC[15,17] | `{}` |
| `TC_15_15_SPECIFIC` | TC[15,15] | `{"N": 1, "store_ids": [100]}` — resume open retrieval |
| `TC_15_15_ALL` | TC[15,15] | `{}` |
| `TC_15_16_SPECIFIC` | TC[15,16] | `{"N": 1, "store_ids": [100]}` — suspend open retrieval |
| `TC_15_16_ALL` | TC[15,16] | `{}` |
| `TC_15_14_SPECIFIC` | TC[15,14] | `{"start_time": 0, "N": 1, "store_ids": [100]}` — reposition the cursor on a *suspended* store 100 |
| `TC_15_14_ALL` | TC[15,14] | `{"start_time": 0}` — reposition the cursor on every currently-suspended store |
| `TC_15_11_SPECIFIC` | TC[15,11] | `{"time_limit": 9223372036854775807, "N": 1, "store_ids": [100]}` — delete all content of store 100 (see g.5 on timestamp scale) |
| `TC_15_11_ALL` | TC[15,11] | `{"time_limit": 9223372036854775807}` |
| `TC_15_25_RESIZE_STORES` | TC[15,25] | `{"N": 1, "resize_entries": [{"store_id": 200, "new_size": 8000}]}` |
| `TC_15_28_CHANGE_VC` | TC[15,28] | `{"store_id": 200, "new_vc_id": 3}` |
| `TC_15_21_SPECIFIC` | TC[15,21] | `{"N": 1, "store_ids": [100]}` — delete only if storage disabled, open retrieval suspended, and BTR disabled |
| `TC_15_21_ALL` | TC[15,21] | `{}` — delete every store currently eligible, silently skipping active ones |

```text
packet_store_id:u8
n_count:u8
n_count × {
    apid:u11
    structure_id_count:u8
    structure_id_count × {
        structure_id:enum8
        subsampling_rate:u8
    }
}
```

TC[15,30] and TC[15,32] use the same hierarchy but each structure entry
contains only `structure_id`; a delete entry has no subsampling field.
TC[15,35] carries one store ID and requests TM[15,36]. TC[15,37] carries one
store ID and requests TM[15,38]. Subsampling is mandatory in the add requests
and configuration reports where shown.

### Event blocking selection

TC[15,33], TC[15,34], and TM[15,40] use:

```text
packet_store_id:u8
n_count:u8
n_count × {
    apid:u11
    event_count:u8
    event_count × event_ids:enum8
}
```

**TC[15,33] deletes** event identifiers from the blocking configuration;
**TC[15,34] adds** them. TC[15,39] carries one store ID and requests TM[15,40].
Event blocking is an on-board selection policy; the XTCE schema represents
requests and reports and preserves applicable zero-count wildcard selections.

## XTCE construction

Each subtype has an explicit `add_pus_tc_15_<subtype>_command` or
`add_pus_tm_15_<subtype>_container` function. TC[15,1]'s original interface is
preserved. Builders use descriptive names, aggregates and counted arrays,
standard TC verifiers, and the shared header. All TM containers inherit from
`pus_header.pus_tm_nontime_container` and match service 15 plus their subtype.

- **Single generated APID**: ordinary generated TM in this simulator uses `MAIN_APID = 1`
  (`PusSimulator.newPacket`). Packed report encoding and decoding can contain APIDs above 255, but
  only APID 1 can match the simulator's normally generated telemetry.
- **Timestamps are elapsed-millis-since-server-start, not wall-clock or CUC**: `Pus15Service` reads
  `pusSimulator.timeEncoding.now().millis()` (an `Epoch.SIMULATOR` clock that starts near zero at
  server boot) for every stored packet, and all wire-visible timestamp fields
  (`start_time`/`end_time`/`time_limit`/`oldest_ts`/`newest_ts`/`open_retrieval_start_time`) carry
  that same raw value as a `uint64`, not a CUC-encoded field (see timestamp convention above). There is no real-time
  correlation to compute when testing — a small integer close to the server's uptime in
  milliseconds, or a maximal value like `2**63-1` to mean "everything", are the typical inputs for the default simulator clock. The epoch is configurable, so check the active time encoding before using these values.
- **Block-all storage default**: enabling an unconfigured store captures no TM. TC[15,3] must first
  authorize an application process and service/subtype; ST[3] and ST[5] are then additionally
  subject to their specialized structure or event rules.
- **Open retrieval replay reuses the original TM containers**: a replayed packet decodes identically
  to its original transmission (same container, same fields) — there's no separate "this came from
  a packet store" wrapper. The only way to distinguish a replay from a live packet in this simulator
  is timing (it arrives again, later, while `open_retrieval_status=in_progress`) or byte-for-byte
  comparison.
- **BTR/open-retrieval replay bypasses ST[14]'s forwarding gate**: `PusSimulator.sendStoredPacket`
  is used for retrieval replay specifically so a restrictive APFCC (ST[14]) can't silently swallow a
  ground-requested dump; only live, first-time transmissions are subject to `shouldForward()`. Keep
  this in mind if testing ST[14] and ST[15] together — a packet blocked in real time can still show
  up later via retrieval.
