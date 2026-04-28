# PUS ST[15] On-Board Storage and Retrieval — Implementation Analysis

**Standard**: ECSS-E-ST-70-41C §6.15 and §8.15  
**Context**: YAMCS/Pixxel test_yamcs environment — Python simulator + XTCE MDB (pus_dt.xml / pus20.xml pattern)

---

## a) General Context

### What ST[15] Does

ST[15] provides the ability to store TM packets on-board and retrieve them later on ground request. This is essential when:
- Ground station coverage is intermittent (store all TM during eclipse, dump at next pass)
- Recovering lost packets (retain last N packets in a circular buffer)

### Two Sub-Services

| Sub-service | Role |
|-------------|------|
| **Storage & Retrieval Subservice** | Manages packet stores (create, delete, resize, enable/disable storage, request retrieval) |
| **Packet Selection Subservice** | Controls *which* TM packets are stored in which packet store (filtering by APID, service type, message subtype, HK structure ID, event ID) |

### Packet Store Model

Each packet store has:

| Property | Type | Description |
|----------|------|-------------|
| `store_id` | uint16 | Unique identifier |
| `size_bytes` | uint32 | Allocated capacity |
| `store_type` | uint8 | 0=circular (oldest overwritten), 1=bounded (stops when full) |
| `vc_id` | uint8 | Virtual channel for downlink during retrieval |
| `storage_status` | enum | enabled / disabled |
| `open_retrieval_status` | enum | in-progress / suspended |
| `btr_status` | enum | enabled (retrieval running) / disabled |
| `open_retrieval_start_time` | uint64 (CUC) | Cursor for open retrieval |

### Two Retrieval Modes

| Mode | Trigger | Behaviour |
|------|---------|-----------|
| **Open retrieval** | TC[15,15] resume | Continuously transmit stored packets from `open_retrieval_start_time` forward until caught up, then wait for new packets |
| **By-time-range retrieval** | TC[15,9] | Transmit all packets in a fixed `[start_time, end_time]` window, then auto-stop |

### Packet Store Lifecycle (State Machine)

```
                   TC[15,20] create
                        │
                  storage=DISABLED
                  open_retrieval=SUSPENDED
                  btr=DISABLED
                        │
    TC[15,1] ──► storage=ENABLED ──► TC[15,2] ──► storage=DISABLED
                        │
    TC[15,15] ──► open_retrieval=IN-PROGRESS ──► TC[15,16] ──► SUSPENDED
                        │
    TC[15,9]  ──► btr=ENABLED ──► TC[15,17] / end-reached ──► btr=DISABLED
```

---

## b) Required TC/TM — Context and Implementation Plan

All 35 required subtypes are listed below in two groups.

---

### Storage & Retrieval Subservice

---

#### TC[15,1] — Enable storage function of packet stores

**Purpose**: Set `storage_status = enabled` for one or more (or all) packet stores so that incoming TM matching the selection filter gets stored.

**Packet format**:
```
Option A — specific stores:
  [N: uint8] [store_id_1: uint16] … [store_id_N: uint16]

Option B — all stores:
  (no application data)
```

**XTCE approach**: Two MetaCommands — `TC_15_1_specific` (with N + array of store_ids) and `TC_15_1_all` (no arguments). Both set service_type=15, service_subtype=1.

**Code**: For each store_id, set `store.storage_enabled = True`. Reject if store_id unknown (send PUS-1 NACK).

---

#### TC[15,2] — Disable storage function of packet stores

**Purpose**: Set `storage_status = disabled`. New incoming TM will no longer be written to those stores.

**Packet format**: Same as TC[15,1] (N×store_id or no-arg all).

**XTCE approach**: Same two-MetaCommand pattern (service_subtype=2).

**Code**: Set `store.storage_enabled = False` per store.

---

#### TC[15,9] — Start by-time-range retrieval of packet stores

**Purpose**: Begin transmitting stored packets in a time window `[start_time, end_time]`. Sets `btr_status = enabled` for each specified store.

**Packet format**:
```
N: uint8
For each instruction:
  store_id: uint16
  start_time: uint64   (CUC 4B coarse + 3B fine, encoded as 8 bytes with pfield)
  end_time:   uint64
```
*(Note: if priority is supported, an additional priority uint8 precedes each time pair — omit for simplified implementation)*

**XTCE approach**: `TC_15_9` with an AggregateArgumentType `BtrInstrType = {store_id:uint16, start_time:uint64, end_time:uint64}` and `ArrayArgumentType` of N entries.

**Code**: For each instruction, validate store exists and btr_status is not already enabled, then launch a background thread that reads `store.packets` where `start_time <= ts <= end_time` and transmits each. When done, set `btr_status = disabled`.

**Gap**: uint64 type not in pus_dt.xml — needs to be added (see Gap §1).

---

#### TC[15,11] — Delete content of packet stores up to specified time

**Purpose**: Free space by deleting all packets with `timestamp <= time_limit` from one or more (or all) stores.

**Packet format**:
```
time_limit: uint64   (CUC)
N: uint8             (0 means all stores)
[store_id_1: uint16] … [store_id_N: uint16]
```

**XTCE approach**: Single MetaCommand with `time_limit` argument, `N` uint8, and ArrayArgumentType of store_ids. When N=0 the array has zero length (all stores).

**Code**: For each target store, remove packets where `ts <= time_limit`. Reject if store is currently in active btr or open retrieval (to avoid deleting in-flight data).

---

#### TC[15,12] — Summary-report content of packet stores

**Purpose**: Request a TM[15,13] status summary for one or more (or all) packet stores.

**Packet format**: N×store_id or no-arg (all).

**XTCE approach**: Two MetaCommands (`_specific` and `_all`).

**Code**: For each targeted store, build and send TM[15,13] immediately.

---

#### TM[15,13] — Packet store content summary report

**Purpose**: Reports fill level and time boundaries of each packet store.

**Packet format**:
```
N: uint8
For each store:
  store_id:                    uint16
  oldest_ts:                   uint64   (CUC of oldest stored packet)
  newest_ts:                   uint64   (CUC of newest stored packet)
  open_retrieval_start_time:   uint64   (current open-retrieval cursor)
  fill_pct:                    uint8    (0–100 % capacity used)
  fill_pct_from_start:         uint8    (fill % from open-retrieval cursor to newest)
```

**XTCE approach**: AggregateParameterType `StoreSummaryType` with 7 members; ArrayParameterType driven by `N`.

**Code**: Compute fill_pct = `len(store.packets) / store.max_packets * 100`.

---

#### TC[15,14] — Change open retrieval start time tag of packet stores

**Purpose**: Reposition the open-retrieval cursor. Only valid when `open_retrieval_status = suspended`.

**Packet format**:
```
start_time: uint64   (new cursor value)
N: uint8
[store_id: uint16] × N   OR   N=0 means all suspended stores
```

**XTCE approach**: Single MetaCommand with `start_time` + N + array.

**Code**: Validate store is suspended (reject otherwise), then `store.open_retrieval_start_time = start_time`.

---

#### TC[15,15] — Resume open retrieval of packet stores

**Purpose**: Set `open_retrieval_status = in-progress`, triggering continuous transmission from `open_retrieval_start_time`.

**Packet format**: N×store_id or no-arg. *(priority uint8 per entry if supported — omit for simplified implementation)*

**XTCE approach**: Two MetaCommands.

**Code**: For each store, set `open_retrieval_status = IN_PROGRESS`, launch background thread that continuously reads `store.packets` from cursor forward and transmits. Thread auto-updates `open_retrieval_start_time` as packets are sent.

---

#### TC[15,16] — Suspend open retrieval of packet stores

**Purpose**: Pause open retrieval. The cursor position is preserved so TC[15,15] can resume without gap.

**Packet format**: N×store_id or no-arg.

**XTCE approach**: Two MetaCommands.

**Code**: Set `open_retrieval_status = SUSPENDED`, signal background thread to stop. Cursor remains.

---

#### TC[15,17] — Abort by-time-range retrieval of packet stores

**Purpose**: Cancel an in-progress BTR. Sets `btr_status = disabled`.

**Packet format**: N×store_id or no-arg.

**XTCE approach**: Two MetaCommands.

**Code**: Cancel BTR thread for each store, set `btr_status = DISABLED`.

---

#### TC[15,18] — Report status of each packet store

**Purpose**: Request TM[15,19] for all stores.

**Packet format**: No application data.

**XTCE approach**: Single MetaCommand, no arguments.

**Code**: Iterate all stores, build and send TM[15,19].

---

#### TM[15,19] — Packet store status report

**Purpose**: Reports live status of each packet store.

**Packet format**:
```
N: uint8
For each store:
  store_id:               uint16
  storage_status:         uint8   (0=disabled, 1=enabled)
  open_retrieval_status:  uint8   (0=suspended, 1=in-progress)
  btr_status:             uint8   (0=disabled, 1=enabled)
```

**XTCE approach**: AggregateParameterType `StoreStatusType` with 4 members; ArrayParameterType driven by N.

---

#### TC[15,20] — Create packet stores

**Purpose**: Dynamically allocate a new packet store with given size and type.

**Packet format**:
```
N: uint8
For each instruction:
  store_id:    uint16
  size_bytes:  uint32
  store_type:  uint8   (0=circular, 1=bounded)
  vc_id:       uint8
```

**XTCE approach**: AggregateArgumentType `CreateStoreInstrType`; ArrayArgumentType of N entries.

**Code**: Check store_id not already in use and max stores not exceeded. Create `PacketStore(store_id, size_bytes, store_type, vc_id)`. Initial state: storage=DISABLED, open_retrieval=SUSPENDED, btr=DISABLED.

---

#### TC[15,21] — Delete packet stores

**Purpose**: Remove one or more packet stores (only deletable when all three statuses are inactive).

**Packet format**: N×store_id or no-arg (delete all eligible).

**XTCE approach**: Two MetaCommands.

**Code**: For each store, validate all statuses are inactive, then remove from store map. Reject with NACK if active.

---

#### TC[15,22] — Report configuration of each packet store

**Purpose**: Request TM[15,23] describing the static configuration of all stores.

**Packet format**: No application data.

**XTCE approach**: Single MetaCommand, no arguments.

---

#### TM[15,23] — Packet store configuration report

**Purpose**: Describes the configured properties of each store.

**Packet format**:
```
N: uint8
For each store:
  store_id:    uint16
  size_bytes:  uint32
  store_type:  uint8
  vc_id:       uint8
```

**XTCE approach**: AggregateParameterType `StoreConfigType`; ArrayParameterType.

---

#### TC[15,25] — Resize packet stores

**Purpose**: Change a packet store's `size_bytes`. Only valid when all statuses are inactive.

**Packet format**:
```
N: uint8
For each instruction:
  store_id:     uint16
  new_size:     uint32
```

**XTCE approach**: AggregateArgumentType `ResizeInstrType`; ArrayArgumentType.

**Code**: Validate all statuses inactive, update `store.size_bytes`. Reject if active or if new_size is 0 or exceeds available memory (check is implementation-defined).

---

#### TC[15,28] — Change virtual channel used by a packet store

**Purpose**: Update which virtual channel is used when transmitting retrieved packets.

**Packet format**:
```
store_id:  uint16
new_vc_id: uint8
```

**XTCE approach**: Single MetaCommand with two arguments.

**Code**: Validate store exists and not currently in btr or open retrieval. Update `store.vc_id`.

---

### Packet Selection Subservice

The packet selection subservice controls *which* incoming TM packets are admitted to each packet store. It is structured as a three-level filter hierarchy: **application process → service type → message subtype**. Additionally there are separate HK-parameter, diagnostic-parameter, and event-definition filter tables.

---

#### TC[15,3] — Add report types to application process storage-control configuration

**Purpose**: Enable storage of specific TM types (identified by APID, service type, message subtype) into a packet store.

**Packet format**:
```
store_id: uint16
N: uint8   (number of instructions)
For each instruction — one of:
  (a) {apid: uint16, svc_type: uint8, msg_subtype: uint8}   add specific report type
  (b) {apid: uint16, svc_type: uint8}                        add all subtypes of a service
  (c) {apid: uint16}                                          add all types of an app process
```

**XTCE approach**: For simplicity in the test environment, model as a fixed `{apid, svc_type, msg_subtype}` AggregateArgumentType. A `0xFF` sentinel for svc_type or msg_subtype means "all". This avoids the variable-length inner instructions problem.

**Code**: For each instruction, upsert into `store.app_process_config[apid][svc_type].add(msg_subtype)`.

---

#### TC[15,4] — Delete report types from application process storage-control configuration

**Purpose**: Reverse of TC[15,3].

**Packet format**: Same structure as TC[15,3] but for deletion.

**XTCE approach**: Same aggregate/array pattern, service_subtype=4.

**Code**: For each instruction, remove from filter table. If the resulting svc_type entry is empty, delete it. If apid entry is empty, delete it. If store entry is empty, delete it.

---

#### TC[15,5] — Report content of application process storage-control configuration

**Purpose**: Request TM[15,6] for a specific packet store.

**Packet format**: `store_id: uint16`

**XTCE approach**: Single MetaCommand with one argument.

---

#### TM[15,6] — Application process storage-control configuration content report

**Purpose**: Lists the filter configuration for a packet store.

**Packet format**:
```
store_id: uint16
N_app_processes: uint8
For each app process:
  apid: uint16
  N_service_types: uint8
  For each service type:
    svc_type: uint8
    N_subtypes: uint8
    [msg_subtype: uint8] × N_subtypes
```

**XTCE approach**: This is a nested array structure. Model as:
- `AppProcessEntryType = {apid: uint16, N_svc: uint8, svc_entries: SvcTypeEntryType[]}`
- `SvcTypeEntryType = {svc_type: uint8, N_sub: uint8, subtypes: uint8[]}`
XTCE supports this nesting but the dimension of inner arrays depends on a field in the outer aggregate — this requires `DynamicValue` with `ParameterInstanceRef` pointing into the aggregate context. **Feasible but complex; the most advanced XTCE pattern in this implementation.**

**Code**: Walk `store.app_process_config` and serialize.

---

#### TC[15,29] — Add structure identifiers to HK parameter report storage-control configuration

**Purpose**: Enable storage of specific housekeeping parameter report structures (identified by HK structure ID) into a packet store.

**Packet format**:
```
store_id: uint16
N: uint8
For each instruction:
  apid: uint16
  hk_struct_id: uint16
  [subsampling_rate: uint8]   (if subsampling supported)
```

**XTCE approach**: AggregateArgumentType `HkStructInstrType`; ArrayArgumentType. For test environment, always include subsampling_rate (set to 1 if not used).

**Code**: Add to `store.hk_config[apid].add(hk_struct_id)`.

---

#### TC[15,30] — Delete structure identifiers from HK parameter report storage-control configuration

**Purpose**: Reverse of TC[15,29].

**Packet format**: Same minus subsampling_rate.

---

#### TC[15,31] — Add structure identifiers to diagnostic parameter report storage-control configuration

**Purpose**: Same as TC[15,29] but for diagnostic (ST[04]) parameter report structures.

**Packet format**:
```
store_id: uint16
N: uint8
For each: {apid: uint16, diag_struct_id: uint16}
```

---

#### TC[15,32] — Delete structure identifiers from diagnostic parameter report storage-control configuration

Reverse of TC[15,31].

---

#### TC[15,33] — Delete event definition identifiers from event report blocking storage-control configuration

**Purpose**: Remove specific event IDs from the block list, allowing those events to be stored again.

**Packet format**:
```
store_id: uint16
N: uint8
For each: {apid: uint16, event_def_id: uint16}
```

---

#### TC[15,34] — Add event definition identifiers to event report blocking storage-control configuration

**Purpose**: Block specific event IDs from being stored in a packet store.

**Packet format**: Same as TC[15,33].

---

#### TC[15,35] — Report content of HK parameter report storage-control configuration

**Purpose**: Request TM[15,36] for a packet store.

**Packet format**: `store_id: uint16`

---

#### TM[15,36] — HK parameter report storage-control configuration content report

**Packet format**:
```
store_id: uint16
N_app_processes: uint8
For each app process:
  apid: uint16
  N_hk_structs: uint8
  [hk_struct_id: uint16] × N_hk_structs
  [subsampling_rate: uint8 per struct if supported]
```

**XTCE approach**: Nested aggregate array — same complexity class as TM[15,6].

---

#### TC[15,37] — Report content of diagnostic parameter report storage-control configuration

**Packet format**: `store_id: uint16`

---

#### TM[15,38] — Diagnostic parameter report storage-control configuration content report

**Packet format**: Same structure as TM[15,36] but with `diag_struct_id` entries.

---

#### TC[15,39] — Report content of event report blocking storage-control configuration

**Packet format**: `store_id: uint16`

---

#### TM[15,40] — Event report blocking storage-control configuration content report

**Packet format**:
```
store_id: uint16
N_app_processes: uint8
For each app process:
  apid: uint16
  N_events: uint8
  [event_def_id: uint16] × N_events
```

---

## c) Gaps and Shortcomings

### Gap 1 — `uint64` / CUC timestamp not in pus_dt.xml

**Affects**: TC[15,9], TC[15,11], TC[15,13], TC[15,14] and all their TM counterparts.

**Problem**: CUC time is 8 bytes (1B pfield + 4B coarse + 3B fine). `pus_dt.xml` only defines uint8/uint16/uint32. There is no uint64, uint24, or composite time type.

**Fix**: Add to `pus_dt.xml`:
```xml
<IntegerParameterType signed="false" name="uint64">
    <IntegerDataEncoding encoding="unsigned" sizeInBits="64" />
</IntegerParameterType>
```
And treat the 8-byte CUC block as a raw uint64 (ignoring pfield byte interpretation in XTCE; handle decoding in Python). Alternatively, split into `cuc_coarse: uint32` + `cuc_fine: uint32` and omit the pfield byte for simplicity.

---

### Gap 2 — "All stores" vs. "Specific stores" duality requires two MetaCommands per TC

**Affects**: TC[15,1], TC[15,2], TC[15,12], TC[15,14], TC[15,15], TC[15,16], TC[15,17], TC[15,21].

**Problem**: The spec allows a TC to carry either N specific store IDs or zero arguments (meaning "all stores"). XTCE MetaCommands require a fixed argument schema — there is no way to make all arguments optional-or-present.

**Fix**: Define two MetaCommands per ambiguous subtype:
- `TC_15_1_specific` — arguments: `N:uint8`, `store_ids: uint16[]`
- `TC_15_1_all` — no arguments, fixed subtype=1

Both share the same APID and service_subtype in the packet; the Python simulator disambiguates by checking remaining payload length after the header.

---

### Gap 3 — Nested arrays-of-arrays in TM[15,6/36/38/40] exceed standard XTCE complexity

**Affects**: TM[15,6], TM[15,36], TM[15,38], TM[15,40].

**Problem**: These TM packets contain a list of per-application-process entries, each of which contains a variable-length list of sub-entries (service types / HK struct IDs / event IDs). XTCE `ArrayParameterType` dimension must reference a `ParameterInstanceRef`, but when the length field is inside a containing aggregate rather than a standalone parameter, some YAMCS versions may not resolve the reference correctly.

**Fix option A**: Flatten the structure — use a single flat array where each entry carries both the outer key (apid) and inner value (svc_type, msg_subtype). Duplicate apid across rows. Simpler XTCE, slightly redundant wire format.

**Fix option B**: Use nested AggregateParameterType with dynamic inner dimension referencing the N_svc field of the outer aggregate member. Spec-faithful but relies on YAMCS correctly tracking inner-aggregate parameter references. Test required.

**Recommendation**: Use option A for initial implementation; upgrade to option B only if YAMCS handles it correctly.

---

### Gap 4 — Optional/conditional fields in TC[15,20] and TM[15,23]

**Affects**: TC[15,20] (create stores), TM[15,23] (configuration report).

**Problem**: `store_type` and `vc_id` are optional in the spec (only present if the subservice supports multiple store types / multiple VCs). XTCE `IncludeCondition` requires a preceding indicator field.

**Fix**: For the test environment, always include `store_type` and `vc_id` as fixed-size fields. Add a `flags: uint8` byte before them where bit 0 = store_type present, bit 1 = vc_id present. Set flags=0x03 always.

---

### Gap 5 — Active retrieval is a stateful background process requiring threading

**Affects**: TC[15,9] (BTR), TC[15,15] (open retrieval resume).

**Problem**: Both retrieval modes require continuously transmitting stored packets over time. This cannot be modelled in XTCE — it is purely simulator-side behavior.

**Code requirement**: Two thread patterns per store (similar to ST[11] scheduled execution):
- Open retrieval thread: reads `store.packets[cursor:]`, transmits each via UDP, advances cursor, then waits for new packets
- BTR thread: reads `store.packets` where `start_time ≤ ts ≤ end_time`, transmits, then signals completion and sets `btr_status = DISABLED`

**Complexity**: Moderate. Directly analogous to the ST[11] pattern already implemented.

---

### Gap 6 — TM bus / packet store interception requires architectural addition to simulator

**Affects**: The core storage function (all TC[15,1]/TC[15,3] etc. are useless without this).

**Problem**: Currently each Python service (e.g. `pus20_simulator.py`) sends TM directly via UDP to YAMCS. PUS 15 requires that all outgoing TM be optionally intercepted and copied into packet stores before (or during) transmission. This is not in the current architecture.

**Fix**: Introduce a shared `PacketStoreManager` class that wraps the TM send socket:
```python
class PacketStoreManager:
    def submit_tm(self, raw_packet: bytes) -> None:
        """Called by all services before sending TM via UDP.
        Checks each enabled store's filter config; appends matching packets."""
        for store in self.stores.values():
            if store.storage_enabled and self._matches_filter(store, raw_packet):
                store.append(raw_packet, timestamp=now())
        self.udp_sock.sendto(raw_packet, (TM_HOST, TM_PORT))
```
All services import and call `PacketStoreManager.submit_tm()` instead of sending directly. This is a one-time shared infrastructure addition.

---

### Gap 7 — Fill percentage tracking requires capacity bookkeeping

**Affects**: TM[15,13] (content summary).

**Problem**: `fill_pct` is `stored_bytes / capacity_bytes * 100`. This needs the simulator to track byte count (not just packet count) since packets vary in size.

**Fix**: Each `PacketStore` maintains a `used_bytes` counter incremented on append and decremented on deletion or circular overwrite.

---

## Summary: XTCE Feasibility

| Criterion | Answer |
|-----------|--------|
| All 35 TC/TM formats expressible in XTCE? | **YES** — using aggregates, arrays, dynamic dimensions |
| Any XTCE features needed that are untested in this codebase? | Nested aggregate arrays (TM 6/36/38/40) — verify YAMCS resolves inner dimension references |
| MDB-only with zero code? | **NO** — packet store state machine, retrieval threads, and TM bus are required |
| Simulator code complexity vs PUS 20? | Significantly higher — stateful, threaded, cross-service interception needed |

## Recommended Implementation Sequence

1. Add `uint64` to `pus_dt.xml`
2. Add `PacketStore` class + `PacketStoreManager` to simulator
3. Implement TC[15,20/21/22/23] (create/delete/configure stores) — static management, no threads
4. Implement TC[15,1/2/18/19] (enable/disable storage + status report) — basic state transitions
5. Implement TC[15,3/4/5/6] (app process storage-control config) — filter table management
6. Implement TC[15,12/13] (content summary) — fills in remaining store state
7. Implement TC[15,9/17] (BTR start/abort) — adds BTR thread
8. Implement TC[15,14/15/16] (open retrieval start/resume/suspend) — adds open retrieval thread
9. Implement TC[15,11/25/28] (delete content, resize, change VC)
10. Implement TC[15,29-40] (HK/diag/event storage-control configs)
