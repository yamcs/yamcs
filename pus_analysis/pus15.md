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

Normal outgoing TM is checksummed when configured. ST[130] storage eligibility is applied before submission to ST[15] and before live forwarding.
There is no separate packet-store manager or UDP forwarding shim. Retrieval uses the shared
single-thread scheduled executor, not one blocking thread per store. BTR steps are 100 ms;
open retrieval polls at 500 ms. Replayed packets go through `sendStoredPacket()`, bypassing
recapture and the live ST[14] gate. APID-zero time packets use another send path.

Circular stores can overwrite; bounded stores stop accepting packets at capacity. There is
a 64-store limit and active-store operations have restrictions. Selection content reports
TM[15,6/36/38/40] carry one store ID and page-sized portions of the nested configuration.
The four-byte secondary-header field holds a one-based page number and total page count
as big-endian uint16s. An empty selection emits page 1 of 1.

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
Web UI: `http://localhost:8090`, instance `pus`. Commands live under `/PUS15/...`, TM containers
under the same `/PUS15/` subsystem (see g.3). An automated walkthrough of most of this table is in
`examples/pus/tests/test-pus15.py`.

### g.2 Command reference — valid inputs

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

Rejection conditions to exercise (all respond NACK completion, not NACK start — the command is
accepted then rejected during execution; see the `Pus15Service` completion error codes):
unknown `store_id` (`COMPL_ERR_STORE_NOT_FOUND` = 5), `TC_15_20_CREATE_STORES` reusing an existing
id (`COMPL_ERR_STORE_ALREADY_EXISTS` = 6), delete/resize/change-VC on a store that still has
storage enabled, open retrieval in progress, or BTR enabled (`COMPL_ERR_STORE_ACTIVE` = 7), more
than 64 total stores (`COMPL_ERR_MAX_STORES_EXCEEDED` = 8), `TC_15_9_START_BTR` on a store with an
already-active BTR (`COMPL_ERR_BTR_ALREADY_ACTIVE` = 9), `TC_15_14_SPECIFIC` naming a store whose
open retrieval isn't suspended (`COMPL_ERR_NOT_SUSPENDED` = 10), and `TC_15_11_*` targeting a store
with an active BTR or open retrieval (`COMPL_ERR_ACTIVE_RETRIEVAL` = 11). An unrecognized subtype
gets NACK **start** (`START_ERR_INVALID_PUS_SUBTYPE`) instead, since the command is rejected before
execution begins.

### g.3 TMs to check

| Container | Subtype | Triggered by | Layout |
|---|---|---|---|
| `/PUS15/STORE_CONFIG_REPORT` | TM[15,23] | `TC_15_22_REPORT_CONFIG` | `N:u8`, then `N` × `{store_id:u16, size_bytes:u32, store_type:u8, vc_id:u8}` |
| `/PUS15/STORE_STATUS_REPORT` | TM[15,19] | `TC_15_18_REPORT_STATUS` | `N:u8`, then `N` × `{store_id:u16, storage_status:u8, open_retrieval_status:u8, btr_status:u8}` (0/1 enums, see pus15.xml) |
| `/PUS15/STORE_SUMMARY_REPORT` | TM[15,13] | `TC_15_12_SPECIFIC` / `TC_15_12_ALL` | `N:u8`, then `N` × `{store_id:u16, oldest_ts:u64, newest_ts:u64, open_retrieval_start_time:u64, fill_pct:u8, fill_pct_from_start:u8}` |
| `/PUS15/APPLICATION_SELECTION_REPORT` | TM[15,6] | `TC_15_5_REPORT_APPLICATION_CONFIG` | `store_id:u16, N1:u8`, then `N1` × `{apid:u11, N2:u8, N2 × {service:u8, N3:u8, N3 × subtype:u8}}` |
| `/PUS15/HK_SELECTION_REPORT` | TM[15,36] | `TC_15_35_REPORT_HK_CONFIG` | `store_id:u16, N1:u8`, then `N1` × `{apid:u11, N2:u8, N2 × structure_id:u8}` |
| `/PUS15/DIAGNOSTIC_SELECTION_REPORT` | TM[15,38] | `TC_15_37_REPORT_DIAGNOSTIC_CONFIG` | Same packed form as TM[15,36] |
| `/PUS15/EVENT_SELECTION_REPORT` | TM[15,40] | `TC_15_39_REPORT_EVENT_CONFIG` | Same packed form as TM[15,36], with `event_definition_id:u8` |

If there are zero stores (config report) or the target list resolves to zero entries, the report is
still sent with `N=0` and no entries. Likewise, each empty packet-selection table produces one
content report with `N1=0`; ST[14] uses the same one-report empty-table rule.

Also watch the standard PUS-1 verification containers (`/PUS/pus-tc-ack-*`) for ACK/NACK
start/completion of every TC[15,x] above.

### g.4 Suggested manual test walkthrough

1. **Create stores**: send `TC_15_20_CREATE_STORES` with the example args from g.2 (store 100
   circular/200000B, store 200 bounded/5000B). Confirm ACK completion, then
   `TC_15_22_REPORT_CONFIG` → `STORE_CONFIG_REPORT` shows both with the right type/size/vc.
2. **Reject duplicate id**: resend `TC_15_20_CREATE_STORES` reusing `store_id: 100` and confirm NACK
   completion with code 6 (`COMPL_ERR_STORE_ALREADY_EXISTS`); confirm the config report is
   unchanged (still exactly 2 stores).
3. **Configure and enable storage**: add ST[3,25] for APID 1 using
   `TC_15_3_ADD_REPORT_TYPES`, authorize the desired 8-bit HK structure ID with
   `TC_15_29_ADD_HK_STRUCTURES`, then send `TC_15_1_SPECIFIC` for store 100. Wait a couple of
   seconds and request `TC_15_12_SPECIFIC`; `STORE_SUMMARY_REPORT` should show `fill_pct > 0` and
   `oldest_ts <= newest_ts`. A newly-created store without TC[15,3] configuration remains empty.
4. **Active-store deletion is rejected**: with storage still enabled on store 100, send
   `TC_15_21_SPECIFIC` for store 100 and confirm NACK completion with code 7
   (`COMPL_ERR_STORE_ACTIVE`).
5. **Open retrieval resume/suspend**: disable storage (`TC_15_2_SPECIFIC`), then
   `TC_15_15_SPECIFIC` for store 100 → `TC_15_18_REPORT_STATUS` shows `open_retrieval_status=1`
   (in_progress); watch previously-captured packets replay on the TM link (same containers as their
   original type, e.g. `/PUS3/...` HK reports, now arriving a second time via
   `PusSimulator.sendStoredPacket`). Then `TC_15_16_SPECIFIC` → status flips back to `0`
   (suspended), and replay stops.
6. **Cursor reposition requires suspended**: with store 100 back in `SUSPENDED` state, send
   `TC_15_14_SPECIFIC` with `start_time: 0` and confirm ACK completion (cursor rewound to the
   oldest packet). Then resume open retrieval again (`TC_15_15_SPECIFIC`) and confirm every
   captured packet up to the newest gets replayed once, in order.
7. **By-time-range retrieval**: send `TC_15_9_START_BTR` for store 100 with the full-range example
   from g.2. Immediately check `TC_15_18_REPORT_STATUS` → `btr_status=1` (enabled) while packets are
   still draining (100ms between sends, see `BTR_STEP_DELAY_MS`); after a couple of seconds, status
   flips back to `0` (disabled) once the backlog is exhausted.
8. **BTR double-start rejected**: while a BTR is still draining on store 100 (or immediately restart
   one on a store with plenty of backlog), send a second `TC_15_9_START_BTR` for the same store and
   confirm NACK completion with code 9 (`COMPL_ERR_BTR_ALREADY_ACTIVE`).
9. **Resize + VC change**: with store 200 untouched (storage never enabled, so it's inactive), send
   `TC_15_25_RESIZE_STORES` (`new_size: 8000`) and `TC_15_28_CHANGE_VC` (`new_vc_id: 3`); confirm
   `TC_15_22_REPORT_CONFIG` reflects both changes.
10. **Delete content by time, then delete the stores**: send `TC_15_11_SPECIFIC` for store 100 with
    a far-future `time_limit` and confirm the next `TC_15_12_SPECIFIC` summary shows
    `fill_pct=0`. Finally send `TC_15_21_ALL` (no args) and confirm `TC_15_22_REPORT_CONFIG` reports
    zero stores.

### g.5 Caveats specific to this simulator

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