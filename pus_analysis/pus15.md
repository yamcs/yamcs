# PUS ST[15] On-Board Storage and Retrieval — Implementation Analysis

**Standard**: ECSS-E-ST-70-41C §6.15 and §8.15  
**Context**: YAMCS/Pixxel — YAMCS is the **ground-station MCS only**. `pus15_simulator.py` emulates satellite on-board behavior for testing.

---

## a) General Context

### What ST[15] Does

ST[15] stores TM packets on-board and retrieves them on ground request:
- When ground-station coverage is intermittent (store during eclipse, dump at next pass)
- To recover lost packets (retain last N packets in a circular buffer)

---

### Ground vs. On-board Responsibility

| Responsibility | Where |
|---|---|
| Send storage / retrieval configuration TCs | **Ground (YAMCS MCS)** — XTCE encodes TC packets |
| Receive and display TM status / config reports | **Ground (YAMCS MCS)** — XTCE decodes TM packets |
| Maintain packet stores at runtime | **On-board (satellite)** |
| Intercept outgoing TM and copy into stores | **On-board (satellite)** |
| Apply filter rules to incoming TM | **On-board (satellite)** |
| Execute open retrieval (continuous) | **On-board (satellite)** |
| Execute by-time-range retrieval | **On-board (satellite)** |
| Track fill percentage and time boundaries | **On-board (satellite)** |

**YAMCS MCS role = XTCE only (`pus15.xml`). No Java changes to `yamcs-core` are needed for ST[15].**

---

### Two Sub-Services

| Sub-service | Role |
|---|---|
| **Storage & Retrieval** | Manages packet stores (create, delete, resize, enable/disable storage, request retrieval) |
| **Packet Selection** | Controls *which* TM packets are stored in which packet store (filter by APID / service type / subtype / HK struct / event ID) |

---

### Scope: Excluded Subtypes

Three subtypes are out of scope for this implementation:

| Sub | Message | Reason excluded |
|---|---|---|
| 24 | TC[15,24] — Copy packets by time-window | Store-to-store copy; on-board concern only; not required for initial MCS integration |
| 26 | TC[15,26] — Change store type to circular | Maintenance operation; deferred from initial scope |
| 27 | TC[15,27] — Change store type to bounded | Maintenance operation; deferred from initial scope |

All remaining **34 TC/TM subtypes** are covered below.

---

### Packet Store Model

| Property | Type | Description |
|---|---|---|
| `store_id` | uint16 | Unique identifier |
| `size_bytes` | uint32 | Allocated capacity |
| `store_type` | uint8 | 0=circular (overwrite oldest), 1=bounded (stop when full) |
| `vc_id` | uint8 | Virtual channel for downlink during retrieval |
| `storage_status` | enum | enabled / disabled |
| `open_retrieval_status` | enum | in-progress / suspended |
| `btr_status` | enum | enabled / disabled |
| `open_retrieval_start_time` | uint64 (CUC) | Cursor for open retrieval |

### Two Retrieval Modes

| Mode | Trigger | Behaviour |
|---|---|---|
| **Open retrieval** | TC[15,15] resume | Continuously transmit from `open_retrieval_start_time` forward; cursor advances as packets are sent |
| **By-time-range (BTR)** | TC[15,9] start | Transmit all packets in fixed `[start_time, end_time]` window, then auto-stop and set `btr_status = disabled` |

### Packet Store Lifecycle

```
         TC[15,20] create
               │
         storage=DISABLED
         open_retrieval=SUSPENDED
         btr=DISABLED
               │
  TC[15,1] ──► storage=ENABLED ──► TC[15,2] ──► DISABLED
               │
  TC[15,15] ──► open_retrieval=IN-PROGRESS ──► TC[15,16] ──► SUSPENDED
               │
  TC[15,9]  ──► btr=ENABLED ──► TC[15,17] / end-reached ──► DISABLED
```

---

## b) Required TC/TM — Detail

### Storage & Retrieval Subservice

---

#### TC[15,1] — Enable storage function of packet stores

**Purpose**: Set `storage_status = enabled` for one or more (or all) packet stores.

**Packet format**:
```
Option A (specific):  [N: uint8] [store_id: uint16] × N
Option B (all stores): (no application data)
```

**XTCE**: Two MetaCommands — `TC_15_1_specific` (N + store_id array) and `TC_15_1_all` (no args), both with service_subtype=1.

**Simulator (on-board)**: Set `store.storage_enabled = True` per store; reject and NACK if store_id unknown.

---

#### TC[15,2] — Disable storage function of packet stores

**Purpose**: Set `storage_status = disabled`; new TM no longer written to those stores.

**Packet format**: Same as TC[15,1].

**XTCE**: Two MetaCommands — `TC_15_2_specific` and `TC_15_2_all` (service_subtype=2).

**Simulator (on-board)**: Set `store.storage_enabled = False` per store.

---

#### TC[15,9] — Start by-time-range retrieval of packet stores

**Purpose**: Transmit stored packets in `[start_time, end_time]`; sets `btr_status = enabled`.

**Packet format**:
```
N: uint8
Per entry:
  store_id:   uint16
  start_time: uint64 (CUC 4B coarse + 3B fine)
  end_time:   uint64 (CUC)
```

**XTCE**: `AggregateArgumentType BtrInstrType = {store_id, start_time, end_time}`; `ArrayArgumentType` of N entries. Requires `uint64` in pus_dt.xml (Gap §1).

**Simulator (on-board)**: Validate store exists and btr_status not already enabled; launch BTR thread that reads `store.packets` where `start_time ≤ ts ≤ end_time`, transmits via UDP, then sets `btr_status = DISABLED`.

---

#### TC[15,11] — Delete content of packet stores up to specified time

**Purpose**: Free space by deleting all packets with `timestamp ≤ time_limit`.

**Packet format**:
```
time_limit: uint64 (CUC)
N: uint8            (0 = all stores)
[store_id: uint16] × N
```

**XTCE**: Two MetaCommands — `TC_15_11_specific` (`time_limit` + N + store_id array) and `TC_15_11_all` (`time_limit` only). Requires uint64 (Gap §1 + §2).

**Simulator (on-board)**: Remove packets where `ts ≤ time_limit` from each target store. Reject if store is in active BTR or open retrieval (btr_status=enabled or open_retrieval_status=in-progress).

---

#### TC[15,12] — Summary-report content of packet stores

**Purpose**: Request TM[15,13] for one or more (or all) packet stores.

**Packet format**: N×store_id or no-arg.

**XTCE**: Two MetaCommands — `TC_15_12_specific` and `TC_15_12_all`.

**Simulator (on-board)**: Build and send TM[15,13] immediately for each targeted store.

---

#### TM[15,13] — Packet store content summary report

**Purpose**: Reports fill level and time boundaries of each packet store.

**Packet format**:
```
N: uint8
Per store:
  store_id:                  uint16
  oldest_ts:                 uint64  (CUC of oldest stored packet)
  newest_ts:                 uint64  (CUC of newest stored packet)
  open_retrieval_start_time: uint64  (current open-retrieval cursor)
  fill_pct:                  uint8   (0–100% capacity used)
  fill_pct_from_start:       uint8   (fill% from cursor to newest packet)
```

**XTCE**: `AggregateParameterType StoreSummaryType` with 6 members; `ArrayParameterType` driven by N. Requires uint64 (Gap §1).

**Simulator (on-board)**: Compute `fill_pct = used_bytes / capacity_bytes * 100`; serialize store state; emit TM[15,13]. YAMCS receives and decodes via XTCE.

---

#### TC[15,14] — Change open retrieval start time tag of packet stores

**Purpose**: Reposition the open-retrieval cursor. Only valid when `open_retrieval_status = suspended`.

**Packet format**:
```
start_time: uint64 (new cursor value)
N: uint8            (0 = all suspended stores)
[store_id: uint16] × N
```

**XTCE**: Two MetaCommands — `TC_15_14_specific` (`start_time` + N + array) and `TC_15_14_all` (`start_time` only). Requires uint64 (Gap §1 + §2).

**Simulator (on-board)**: Validate `open_retrieval_status = SUSPENDED` (reject otherwise); update `store.open_retrieval_start_time = start_time`.

---

#### TC[15,15] — Resume open retrieval of packet stores

**Purpose**: Set `open_retrieval_status = in-progress`; begins continuous transmission from cursor.

**Packet format**: N×store_id or no-arg.

**XTCE**: Two MetaCommands — `TC_15_15_specific` and `TC_15_15_all`.

**Simulator (on-board)**: Set `open_retrieval_status = IN_PROGRESS`; launch thread that reads `store.packets` from cursor forward, transmits via UDP, advances cursor. Thread auto-updates cursor as packets are sent.

---

#### TC[15,16] — Suspend open retrieval of packet stores

**Purpose**: Pause open retrieval; cursor position preserved so TC[15,15] can resume without gap.

**Packet format**: N×store_id or no-arg.

**XTCE**: Two MetaCommands — `TC_15_16_specific` and `TC_15_16_all`.

**Simulator (on-board)**: Set `open_retrieval_status = SUSPENDED`; signal background thread to stop; cursor preserved.

---

#### TC[15,17] — Abort by-time-range retrieval of packet stores

**Purpose**: Cancel in-progress BTR; sets `btr_status = disabled`.

**Packet format**: N×store_id or no-arg.

**XTCE**: Two MetaCommands — `TC_15_17_specific` and `TC_15_17_all`.

**Simulator (on-board)**: Cancel BTR thread per store; set `btr_status = DISABLED`.

---

#### TC[15,18] — Report status of each packet store

**Purpose**: Request TM[15,19] for all stores.

**Packet format**: No application data.

**XTCE**: Single MetaCommand, no arguments.

**Simulator (on-board)**: Iterate all stores; build and send TM[15,19].

---

#### TM[15,19] — Packet store status report

**Purpose**: Reports live status of each packet store.

**Packet format**:
```
N: uint8
Per store:
  store_id:               uint16
  storage_status:         uint8  (0=disabled, 1=enabled)
  open_retrieval_status:  uint8  (0=suspended, 1=in-progress)
  btr_status:             uint8  (0=disabled, 1=enabled)
```

**XTCE**: `AggregateParameterType StoreStatusType` with 4 members; `ArrayParameterType` driven by N.

---

#### TC[15,20] — Create packet stores

**Purpose**: Dynamically allocate a new packet store.

**Packet format**:
```
N: uint8
Per entry:
  store_id:   uint16
  size_bytes: uint32
  store_type: uint8  (0=circular, 1=bounded)
  vc_id:      uint8
```

**XTCE**: `AggregateArgumentType CreateStoreInstrType`; `ArrayArgumentType` of N entries.

**Simulator (on-board)**: Check store_id not already in use and max stores not exceeded; create `PacketStore`. Initial state: storage=DISABLED, open_retrieval=SUSPENDED, btr=DISABLED.

---

#### TC[15,21] — Delete packet stores

**Purpose**: Remove one or more packet stores (only valid when all three statuses are inactive).

**Packet format**: N×store_id or no-arg (delete all eligible stores).

**XTCE**: Two MetaCommands — `TC_15_21_specific` and `TC_15_21_all`.

**Simulator (on-board)**: Validate all statuses inactive before deletion; NACK if any store is active.

---

#### TC[15,22] — Report configuration of each packet store

**Purpose**: Request TM[15,23] describing the static configuration of all stores.

**Packet format**: No application data.

**XTCE**: Single MetaCommand, no arguments.

---

#### TM[15,23] — Packet store configuration report

**Purpose**: Describes the configured properties of each store.

**Packet format**:
```
N: uint8
Per store:
  store_id:   uint16
  size_bytes: uint32
  store_type: uint8
  vc_id:      uint8
```

**XTCE**: `AggregateParameterType StoreConfigType` with 4 members; `ArrayParameterType` driven by N.

---

#### TC[15,25] — Resize packet stores

**Purpose**: Change a packet store's `size_bytes`. Only valid when all statuses are inactive.

**Packet format**:
```
N: uint8
Per entry: store_id (uint16) + new_size (uint32)
```

**XTCE**: `AggregateArgumentType ResizeInstrType = {store_id, new_size}`; `ArrayArgumentType`.

**Simulator (on-board)**: Validate all statuses inactive, `new_size > 0`, and new_size compatible with available memory; update `store.size_bytes`.

---

#### TC[15,28] — Change virtual channel used by a packet store

**Purpose**: Update which virtual channel is used when transmitting retrieved packets.

**Packet format**:
```
store_id:  uint16
new_vc_id: uint8
```

**XTCE**: Single MetaCommand with two arguments.

**Simulator (on-board)**: Validate store exists and not in active BTR or open retrieval; update `store.vc_id`.

---

### Packet Selection Subservice

Controls *which* incoming TM packets are admitted to each packet store. Three-level filter hierarchy: **APID → service type → message subtype**. Separate optional filter tables for HK parameter reports, diagnostic parameter reports, and event definitions.

---

#### TC[15,3] — Add report types to application process storage-control configuration

**Purpose**: Enable storage of specific TM types (by APID, service type, message subtype) in a packet store.

**Packet format** (§6.15.4.4.1):
```
store_id: uint16
N1: uint8
  Per APID entry:
    apid: uint16
    N2: uint8   (0 = add all services for this APID)
    Per service type entry:
      svc_type: uint8
      N3: uint8   (0 = add all subtypes of this service)
      [msg_subtype: uint8] × N3
```

| Instruction form | Encoding | Effect |
|---|---|---|
| Add specific report type | N2≥1, N3=1 + subtype | Enable one TM subtype |
| Add all subtypes of a service | N2≥1, N3=0 | Enable all subtypes of a service type |
| Add all services of an APID | N2=0 | Enable all reports from that APID |

**XTCE**: ✅ **Single MetaCommand** — 3-level nested `ArrayArgumentType` using sibling-member `ArgumentInstanceRef` (confirmed by `array-in-array-arg.xml`). N2=0 and N3=0 are zero-length arrays. Covers all three instruction forms.

```xml
<!-- Array of N3 subtypes; N3 is a sibling member in the containing aggregate -->
<ArrayArgumentType name="pkt_sel_subtype_array_type" arrayTypeRef="/dt/uint8">
    <DimensionList><Dimension>
        <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
        <EndingIndex><DynamicValue>
            <ArgumentInstanceRef argumentRef="N3"/>
            <LinearAdjustment intercept="-1"/>
        </DynamicValue></EndingIndex>
    </Dimension></DimensionList>
</ArrayArgumentType>

<!-- Service type entry: svc_type + N3 + N3×subtype -->
<AggregateArgumentType name="pkt_sel_svc_entry_type">
    <MemberList>
        <Member name="svc_type" typeRef="/dt/uint8"/>
        <Member name="N3"       typeRef="/dt/uint8"/>
        <Member name="subtypes" typeRef="pkt_sel_subtype_array_type"/>
    </MemberList>
</AggregateArgumentType>
```

**Simulator (on-board)**:
```python
for _ in range(n1):
    apid = struct.unpack_from(">H", data, offset)[0]; offset += 2
    n2 = data[offset]; offset += 1
    if n2 == 0:
        store.app_process_config.setdefault(apid, {})
    else:
        for _ in range(n2):
            svc_type = data[offset]; offset += 1
            n3 = data[offset]; offset += 1
            if n3 == 0:
                store.app_process_config.setdefault(apid, {})[svc_type] = set()
            else:
                s = store.app_process_config.setdefault(apid, {}).setdefault(svc_type, set())
                for _ in range(n3):
                    s.add(data[offset]); offset += 1
```

---

#### TC[15,4] — Delete report types from application process storage-control configuration

**Purpose**: Reverse of TC[15,3]; remove from filter table.

**Packet format**: Same N1/N2/N3 hierarchical structure as TC[15,3].

| Instruction form | Encoding | Effect |
|---|---|---|
| Delete specific report type | N2≥1, N3=1 + subtype | Remove one subtype from filter |
| Delete a service type | N2≥1, N3=0 | Remove entire service-type entry |
| Delete an application process | N2=0 | Remove entire APID entry |

**XTCE**: ✅ **Single MetaCommand** — reuses `pkt_sel_apid_array_type` from TC[15,3], service_subtype=4.

**Simulator (on-board)**: Mirror TC[15,3] handler but remove from filter table; cascade-delete empty entries.

---

#### TC[15,5] — Report content of application process storage-control configuration

**Purpose**: Request TM[15,6] for a specific packet store.

**Packet format**: `store_id: uint16`

**XTCE**: Single MetaCommand, one argument.

---

#### TM[15,6] — Application process storage-control configuration content report

**Purpose**: Lists the app-process filter configuration for a packet store.

**Packet format**:
```
store_id: uint16
N_app_processes: uint8
Per app process:
  apid: uint16
  N_service_types: uint8
  Per service type:
    svc_type: uint8
    N_subtypes: uint8
    [msg_subtype: uint8] × N_subtypes
```

**XTCE**: ✅ **3-level nested `ContainerRefEntry` + `RepeatEntry`**. `ParameterInstanceRef` defaults to `relativeTo = CURRENT_ENTRY_WITHIN_PACKET` (`getFromEnd(param, 0)`), meaning the inner `RepeatEntry` count resolves to the **most recently decoded** value of that parameter per outer iteration (confirmed in `ParameterInstanceRef.java` line 53). Same proven pattern as TM[14,4].

```xml
<SequenceContainer name="pkt_sel_subtype_element">
    <EntryList><ParameterRefEntry parameterRef="pkt_sel_msg_subtype"/></EntryList>
</SequenceContainer>

<SequenceContainer name="pkt_sel_svc_element">
    <EntryList>
        <ParameterRefEntry parameterRef="pkt_sel_svc_type"/>
        <ParameterRefEntry parameterRef="pkt_sel_N_subtypes"/>
        <ContainerRefEntry containerRef="pkt_sel_subtype_element">
            <RepeatEntry><Count><DynamicValue>
                <ParameterInstanceRef parameterRef="pkt_sel_N_subtypes"/>
            </DynamicValue></Count></RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
</SequenceContainer>

<SequenceContainer name="pkt_sel_apid_element">
    <EntryList>
        <ParameterRefEntry parameterRef="pkt_sel_apid"/>
        <ParameterRefEntry parameterRef="pkt_sel_N_svc_types"/>
        <ContainerRefEntry containerRef="pkt_sel_svc_element">
            <RepeatEntry><Count><DynamicValue>
                <ParameterInstanceRef parameterRef="pkt_sel_N_svc_types"/>
            </DynamicValue></Count></RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
</SequenceContainer>

<SequenceContainer name="TM_15_6">
    <EntryList>
        <ParameterRefEntry parameterRef="pkt_store_id"/>
        <ParameterRefEntry parameterRef="pkt_sel_N_app_processes"/>
        <ContainerRefEntry containerRef="pkt_sel_apid_element">
            <RepeatEntry><Count><DynamicValue>
                <ParameterInstanceRef parameterRef="pkt_sel_N_app_processes"/>
            </DynamicValue></Count></RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
    <BaseContainer containerRef="pus15-tm">
        <RestrictionCriteria>
            <Comparison parameterRef="/PUS/subtype" value="6"/>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Simulator (on-board)**: Walk `store.app_process_config`; serialize and emit TM[15,6].

---

#### TC[15,29] — Add structure identifiers to HK parameter report storage-control configuration

**Purpose**: Enable storage of specific HK parameter report structures (by APID + HK struct ID) in a packet store.

**Packet format** (§6.15.4.5.1):
```
store_id: uint16
N: uint8
Per entry:
  apid:            uint16
  hk_struct_id:    uint16
  [subsampling_rate: uint8]  (if subsampling supported — include always in test env)
```

**XTCE**: `AggregateArgumentType HkStructInstrType = {apid, hk_struct_id, subsampling_rate}`; `ArrayArgumentType` of N entries.

**Simulator (on-board)**: Add `(apid, hk_struct_id)` to `store.hk_config`.

---

#### TC[15,30] — Delete structure identifiers from HK parameter report storage-control configuration

**Purpose**: Reverse of TC[15,29].

**Packet format**: `store_id` + N × `{apid: uint16, hk_struct_id: uint16}` (no subsampling_rate).

**XTCE**: `AggregateArgumentType HkStructDelInstrType = {apid, hk_struct_id}`; `ArrayArgumentType`.

**Simulator (on-board)**: Remove `(apid, hk_struct_id)` from `store.hk_config`; cascade-delete empty entries.

---

#### TC[15,31] — Add structure identifiers to diagnostic parameter report storage-control configuration

**Purpose**: Same as TC[15,29] but for ST[04] diagnostic parameter report structures.

**Packet format**:
```
store_id: uint16
N: uint8
Per entry:
  apid:           uint16
  diag_struct_id: uint16
  [subsampling_rate: uint8]
```

**XTCE**: `AggregateArgumentType DiagStructInstrType = {apid, diag_struct_id, subsampling_rate}`; `ArrayArgumentType`.

**Simulator (on-board)**: Add to `store.diag_config`.

---

#### TC[15,32] — Delete structure identifiers from diagnostic parameter report storage-control configuration

**Purpose**: Reverse of TC[15,31].

**Packet format**: `store_id` + N × `{apid: uint16, diag_struct_id: uint16}`.

**XTCE**: `AggregateArgumentType DiagStructDelInstrType = {apid, diag_struct_id}`; `ArrayArgumentType`.

**Simulator (on-board)**: Remove from `store.diag_config`; cascade-delete empty entries.

---

#### TC[15,33] — Delete event definition identifiers from event report blocking storage-control configuration

**Purpose**: Remove event IDs from the block list — allows those events to be stored again.

**Packet format**:
```
store_id: uint16
N: uint8
Per entry: apid (uint16) + event_def_id (uint16)
```

**XTCE**: `AggregateArgumentType EventDefInstrType = {apid, event_def_id}`; `ArrayArgumentType`.

**Simulator (on-board)**: Remove `event_def_id` from block list; cascade-delete empty entries.

---

#### TC[15,34] — Add event definition identifiers to event report blocking storage-control configuration

**Purpose**: Block specific event IDs from being stored in a packet store.

**Packet format**: Same as TC[15,33].

**XTCE**: Reuses `EventDefInstrType`; service_subtype=34.

**Simulator (on-board)**: Add `event_def_id` to `store.event_block_config[apid]`.

---

#### TC[15,35] — Report content of HK parameter report storage-control configuration

**Purpose**: Request TM[15,36] for a packet store.

**Packet format**: `store_id: uint16`

**XTCE**: Single MetaCommand, one argument.

---

#### TM[15,36] — HK parameter report storage-control configuration content report

**Purpose**: Lists the HK-structure filter configuration for a packet store.

**Packet format**:
```
store_id: uint16
N_app_processes: uint8
Per app process:
  apid: uint16
  N_hk_structs: uint8
  Per HK struct entry:
    hk_struct_id:      uint16
    [subsampling_rate: uint8]  (if subsampling supported)
```

**XTCE**: ✅ **3-level nested `ContainerRefEntry` + `RepeatEntry`** — same `CURRENT_ENTRY_WITHIN_PACKET` pattern as TM[15,6]. If subsampling is included, the innermost entry is an aggregate `{hk_struct_id, subsampling_rate}` — no additional complexity.

---

#### TC[15,37] — Report content of diagnostic parameter report storage-control configuration

**Purpose**: Request TM[15,38] for a packet store.

**Packet format**: `store_id: uint16`

**XTCE**: Single MetaCommand, one argument.

---

#### TM[15,38] — Diagnostic parameter report storage-control configuration content report

**Purpose**: Lists the diagnostic-structure filter configuration.

**Packet format**: Same structure as TM[15,36] but with `diag_struct_id` entries.

**XTCE**: ✅ Identical design to TM[15,36]; reuse nested container structure with `diag_struct_id` parameter names.

---

#### TC[15,39] — Report content of event report blocking storage-control configuration

**Purpose**: Request TM[15,40] for a packet store.

**Packet format**: `store_id: uint16`

**XTCE**: Single MetaCommand, one argument.

---

#### TM[15,40] — Event report blocking storage-control configuration content report

**Purpose**: Lists the event-blocking filter configuration.

**Packet format**:
```
store_id: uint16
N_app_processes: uint8
Per app process:
  apid: uint16
  N_events: uint8
  [event_def_id: uint16] × N_events
```

**XTCE**: ✅ Same 3-level nested `ContainerRefEntry` + `RepeatEntry` pattern as TM[15,36]. Innermost element: `event_def_id: uint16`.

---

## c) Gaps and Shortcomings

### Gap 1 — `uint64` / CUC timestamp not in pus_dt.xml

**Affects**: TC[15,9] `start_time`/`end_time`, TC[15,11] `time_limit`, TC[15,14] `start_time`, TM[15,13] `oldest_ts`/`newest_ts`/`open_retrieval_start_time`.

**Fix**: Add to `pus_dt.xml`:
```xml
<IntegerParameterType signed="false" name="uint64">
    <IntegerDataEncoding encoding="unsigned" sizeInBits="64"/>
</IntegerParameterType>
<IntegerArgumentType signed="false" name="uint64">
    <IntegerDataEncoding encoding="unsigned" sizeInBits="64"/>
</IntegerArgumentType>
```
Treat the 8-byte CUC block as a raw uint64 (ignoring pfield interpretation in XTCE; handle in Python). Alternative: split into `cuc_coarse: uint32` + `cuc_fine: uint32`, omitting the 1-byte pfield for simplicity.

---

### Gap 2 — "All stores" vs "Specific stores" duality requires two MetaCommands per TC

**Affects**: TC[15,1], TC[15,2], TC[15,11], TC[15,12], TC[15,14], TC[15,15], TC[15,16], TC[15,17], TC[15,21].

**Problem**: XTCE MetaCommands require a fixed argument schema — arguments cannot be optional-or-present.

**Fix**: Two MetaCommands per affected subtype:
- `TC_15_X_specific` — N:uint8 + store_ids:uint16[]
- `TC_15_X_all` — no arguments (fixed subtype=X)

Simulator disambiguates by checking remaining payload length after the primary header.

---

### Gap 3 — Nested TM structures (TM[15,6/36/38/40]) — **Resolved**

**Affects**: TM[15,6], TM[15,36], TM[15,38], TM[15,40].

These 3-level nested structures are fully expressible using nested `ContainerRefEntry` + `RepeatEntry`. The key: `ParameterInstanceRef` defaults to `CURRENT_ENTRY_WITHIN_PACKET` (confirmed `ParameterInstanceRef.java` line 53), resolving inner count to the most recently decoded value of that parameter per outer iteration.

**Critical note**: `AggregateParameterType` with inner dynamic dimension (array-in-array) does **not** work for TM decoding in YAMCS. Only `ContainerRefEntry` + `RepeatEntry` works. Canonical reference: TM[14,4] in pus14.md.

---

### Gap 4 — `store_type` and `vc_id` are optional in the spec

**Affects**: TC[15,20], TM[15,23].

**Fix**: For the test environment, always include `store_type` and `vc_id` as fixed-size fields. Add a `flags: uint8` before them (bit0=store_type present, bit1=vc_id present); set `flags=0x03` always.

---

### Gap 5 — Active retrieval requires threading (simulator / on-board emulation only)

**Affects**: TC[15,9] (BTR start), TC[15,15] (open retrieval resume). On-board concern — no MCS involvement.

**Simulator requirement**: Two thread patterns per store:
- **Open retrieval thread**: reads `store.packets[cursor:]`, transmits each via UDP, advances cursor, then waits for new packets
- **BTR thread**: reads `store.packets` where `start_time ≤ ts ≤ end_time`, transmits, then sets `btr_status = DISABLED`

---

### Gap 6 — TM bus interception requires shared infrastructure in simulator

**Affects**: All storage functions (TC[15,1/2/3] are useless without this). On-board concern only.

**Simulator fix**: Introduce a shared `PacketStoreManager` class:
```python
class PacketStoreManager:
    def submit_tm(self, raw_packet: bytes) -> None:
        for store in self.stores.values():
            if store.storage_enabled and self._matches_filter(store, raw_packet):
                store.append(raw_packet, timestamp=now())
        self.udp_sock.sendto(raw_packet, (TM_HOST, TM_PORT))
```
All simulator services call `submit_tm()` instead of sending directly.

---

### Gap 7 — Fill percentage tracking requires byte-level bookkeeping (simulator only)

**Affects**: TM[15,13] `fill_pct` and `fill_pct_from_start`.

**Fix**: Each `PacketStore` maintains `used_bytes` counter; incremented on append, decremented on delete or circular overwrite.

---

## d) XTCE-Only vs Native Java — Per-Message Table

**YAMCS is ground-station only.** All on-board logic lives in `pus15_simulator.py`. YAMCS MCS encodes uplink TC packets and decodes downlink TM reports — both exclusively via XTCE. **No `yamcs-core` Java is needed for ST[15].**

| Sub | Message | Direction | XTCE Approach (MCS) | Java in yamcs-core? | XTCE Complexity | Notes |
|---|---|---|---|---|---|---|
| 1 | TC[15,1] | Send | Two MetaCommands: `_specific` (N + store_id[]) and `_all` (no args) | **No** | Low | Gap §2 dual-variant |
| 2 | TC[15,2] | Send | Two MetaCommands: `_specific` and `_all` | **No** | Low | Same pattern as TC[15,1] |
| 3 | TC[15,3] | Send | Single MetaCommand; 3-level nested `ArrayArgumentType` with sibling-member `ArgumentInstanceRef` | **No** | **High** | store_id + N1×{apid+N2×{svc_type+N3×subtype}}; N=0 → zero-length arrays |
| 4 | TC[15,4] | Send | Single MetaCommand; reuses TC[15,3] nested array types | **No** | **High** | Same wire format as TC[15,3] |
| 5 | TC[15,5] | Send | Single MetaCommand; `store_id` arg only | **No** | Low | |
| 6 | TM[15,6] | Receive | 3-level nested `ContainerRefEntry` + `RepeatEntry`; `ParameterInstanceRef` `CURRENT_ENTRY` | **No** | **High** | Proven — same pattern as TM[14,4] |
| — | — | — | — | — | — | — |
| 9 | TC[15,9] | Send | `AggregateArgType BtrInstrType`; `ArrayArgType` of N | **No** | Medium | Needs uint64 (Gap §1) |
| 11 | TC[15,11] | Send | `time_limit` (uint64) + two MetaCommands `_specific`/`_all` | **No** | Medium | Gap §1 + §2 |
| 12 | TC[15,12] | Send | Two MetaCommands: `_specific` and `_all` | **No** | Low | Gap §2 |
| 13 | TM[15,13] | Receive | `AggregateParamType` (6 fields incl. 3× uint64); `ArrayParamType` driven by N | **No** | Medium | Needs uint64 (Gap §1) |
| 14 | TC[15,14] | Send | `start_time` (uint64) + two MetaCommands `_specific`/`_all` | **No** | Medium | Gap §1 + §2 |
| 15 | TC[15,15] | Send | Two MetaCommands: `_specific` and `_all` | **No** | Low | Gap §2 |
| 16 | TC[15,16] | Send | Two MetaCommands: `_specific` and `_all` | **No** | Low | Gap §2 |
| 17 | TC[15,17] | Send | Two MetaCommands: `_specific` and `_all` | **No** | Low | Gap §2 |
| 18 | TC[15,18] | Send | Single MetaCommand, no args | **No** | Low | |
| 19 | TM[15,19] | Receive | `AggregateParamType StoreStatusType` (4 fields); `ArrayParamType` | **No** | Low | |
| 20 | TC[15,20] | Send | `AggregateArgType CreateStoreInstrType` (4 fields); `ArrayArgType` | **No** | Medium | |
| 21 | TC[15,21] | Send | Two MetaCommands: `_specific` and `_all` | **No** | Low | Gap §2 |
| 22 | TC[15,22] | Send | Single MetaCommand, no args | **No** | Low | |
| 23 | TM[15,23] | Receive | `AggregateParamType StoreConfigType` (4 fields); `ArrayParamType` | **No** | Low | |
| **24** | **TC[15,24]** | — | **OUT OF SCOPE** | — | — | Copy packets by time window; excluded |
| 25 | TC[15,25] | Send | `AggregateArgType ResizeInstrType = {store_id, new_size}`; `ArrayArgType` | **No** | Low | |
| **26** | **TC[15,26]** | — | **OUT OF SCOPE** | — | — | Change store type to circular; excluded |
| **27** | **TC[15,27]** | — | **OUT OF SCOPE** | — | — | Change store type to bounded; excluded |
| 28 | TC[15,28] | Send | Single MetaCommand; `store_id (uint16)` + `new_vc_id (uint8)` | **No** | Low | |
| — | — | — | — | — | — | — |
| 29 | TC[15,29] | Send | `AggregateArgType HkStructInstrType = {apid, hk_struct_id, subsampling_rate}`; `ArrayArgType` | **No** | Low | |
| 30 | TC[15,30] | Send | `AggregateArgType HkStructDelInstrType = {apid, hk_struct_id}`; `ArrayArgType` | **No** | Low | |
| 31 | TC[15,31] | Send | `AggregateArgType DiagStructInstrType = {apid, diag_struct_id, subsampling_rate}`; `ArrayArgType` | **No** | Low | |
| 32 | TC[15,32] | Send | `AggregateArgType DiagStructDelInstrType = {apid, diag_struct_id}`; `ArrayArgType` | **No** | Low | |
| 33 | TC[15,33] | Send | `AggregateArgType EventDefInstrType = {apid, event_def_id}`; `ArrayArgType` | **No** | Low | **Delete** from block list (re-enables storage) |
| 34 | TC[15,34] | Send | `AggregateArgType EventDefInstrType = {apid, event_def_id}`; `ArrayArgType` | **No** | Low | **Add** to block list (prevents storage) |
| 35 | TC[15,35] | Send | Single MetaCommand; `store_id` arg only | **No** | Low | |
| 36 | TM[15,36] | Receive | 3-level nested `ContainerRefEntry` + `RepeatEntry`; `CURRENT_ENTRY` | **No** | **High** | store_id + N_app×{apid+N_hk×{hk_struct_id[+subsampling]}} |
| 37 | TC[15,37] | Send | Single MetaCommand; `store_id` arg only | **No** | Low | |
| 38 | TM[15,38] | Receive | 3-level nested `ContainerRefEntry` + `RepeatEntry`; `CURRENT_ENTRY` | **No** | **High** | Identical design to TM[15,36] with `diag_struct_id` |
| 39 | TC[15,39] | Send | Single MetaCommand; `store_id` arg only | **No** | Low | |
| 40 | TM[15,40] | Receive | 3-level nested `ContainerRefEntry` + `RepeatEntry`; `CURRENT_ENTRY` | **No** | Medium | store_id + N_app×{apid+N_evt×event_def_id} |

**All 34 required messages: XTCE only. Java required: 0 of 34.**

---

### Overall Verdict

| Criterion | Answer |
|---|---|
| All 34 TC/TM formats expressible in XTCE? | **YES** — aggregates, arrays, dynamic dimensions, nested containers |
| XTCE features needed that are untested in this codebase? | **None** — nested TM uses `ContainerRefEntry`+`RepeatEntry` (confirmed `ParameterInstanceRef.java:53`); nested TC uses sibling-member `ArgumentInstanceRef` (confirmed `array-in-array-arg.xml`) |
| MCS (YAMCS ground) needs Java code? | **NO** |
| Simulator complexity vs PUS-20? | Significantly higher — stateful, threaded, cross-service interception required |

---

### Contrast with other services

| Service | XTCE Sufficient for MCS? | Java in yamcs-core? | Why Java is / is not needed |
|---|---|---|---|
| **ST[05]** | Partial | **Yes — `PusEventDecoder`** | TM[5,1–4] must be promoted to YAMCS native events; no XTCE mechanism exists for event emission |
| **ST[11]** | Yes | No (`PusCommandPostprocessor` only) | TC[11,4] wrapping via command extra; all TM are plain containers |
| **ST[14]** | Yes | No | Forwarding-control logic is on-board; MCS only sends config TCs and decodes FCC dump reports |
| **ST[15]** | **Yes** | **No** | Packet store lifecycle, TM interception, retrieval threads are on-board; MCS only encodes TCs and decodes status/config TMs |

---

## e) Implementation Files

| Layer | File | Action |
|---|---|---|
| **MCS / YAMCS ground** | `mdb/pus15.xml` | Create — XTCE TC encoding (all TC[15,x]) and TM decoding (all TM[15,x]) |
| **MCS / YAMCS ground** | `yamcs.pus-test.yaml` | Update — add `mdb/pus15.xml` to MDB list |
| **MCS / YAMCS ground** | `mdb/pus_dt.xml` | Update — add `uint64` `IntegerParameterType` + `IntegerArgumentType` (Gap §1) |
| **Simulator (on-board)** | `simulators/pus15_simulator.py` | Create — `PacketStore`, `PacketStoreManager` TM-bus intercept, storage filter tables (app-process / HK / diag / event), open-retrieval thread, BTR thread, all TM report generation |

### Reference Files
- `mdb/pus20.xml` — XTCE structure pattern
- `simulators/pus20_simulator.py` — Python simulator pattern
- `mdb/pus_dt.xml` — shared data types (uint8/16/32)
- `mdb/pus14.xml` — canonical TM[14,4] nested `ContainerRefEntry`+`RepeatEntry` pattern

---

## f) Recommended Implementation Sequence

1. **[GROUND]** Add `uint64` to `pus_dt.xml`
2. **[ON-BOARD sim]** Add `PacketStore` class + `PacketStoreManager` TM-bus wrapper
3. **[ON-BOARD sim]** TC[15,20/21/22/23] — create/delete/configure stores (static, no threads)
4. **[ON-BOARD sim]** TC[15,1/2/18/19] — enable/disable storage + status report
5. **[ON-BOARD sim]** TC[15,3/4/5/6] — app-process storage-control config
6. **[ON-BOARD sim]** TC[15,12/13] — content summary report
7. **[ON-BOARD sim]** TC[15,9/17] — BTR start/abort (BTR thread)
8. **[ON-BOARD sim]** TC[15,14/15/16] — open-retrieval cursor/resume/suspend (open-retrieval thread)
9. **[ON-BOARD sim]** TC[15,11/25/28] — delete content, resize, change VC
10. **[ON-BOARD sim]** TC[15,29–40] — HK/diag/event storage-control configs
11. **[GROUND]** Author `pus15.xml` in parallel — XTCE definitions for all TC argument structures and TM container hierarchies
