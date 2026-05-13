# PUS Simulator Architecture Reference

## Key File Locations

| Purpose | Path |
|---------|------|
| Simulator dispatcher | `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java` |
| Abstract service base | `simulator/src/main/java/org/yamcs/simulator/pus/AbstractPusService.java` |
| Time packet class (APID=0) | `simulator/src/main/java/org/yamcs/simulator/pus/PusTmTimePacket.java` |
| TC packet class | `simulator/src/main/java/org/yamcs/simulator/pus/PusTcPacket.java` |
| TM packet class | `simulator/src/main/java/org/yamcs/simulator/pus/PusTmPacket.java` |
| PUS time encoding | `simulator/src/main/java/org/yamcs/simulator/pus/PusTime.java` |
| ST[05] service (event reporting) | `simulator/src/main/java/org/yamcs/simulator/pus/Pus5Service.java` |
| ST[11] service (scheduling) | `simulator/src/main/java/org/yamcs/simulator/pus/Pus11Service.java` |
| ST[17] service (test) | `simulator/src/main/java/org/yamcs/simulator/pus/Pus17Service.java` |
| Base PUS MDB | `examples/pus/src/main/yamcs/mdb/pus.xml` |
| Data types MDB | `examples/pus/src/main/yamcs/mdb/dt.xml` |
| ST[05] MDB | `examples/pus/src/main/yamcs/mdb/pus5.xml` |
| ST[11] MDB | `examples/pus/src/main/yamcs/mdb/pus11.xml` |
| ST[17] MDB | `examples/pus/src/main/yamcs/mdb/pus17.xml` |
| YAMCS instance config | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` |

---

## Service Registration Pattern (PusSimulator.java)

```java
// Constructor — instantiate all services
pus5Service = new Pus5Service(this);
pus11Service = new Pus11Service(this);
pus17Service = new Pus17Service(this);

// doStart() — start services that need periodic tasks
pus5Service.start();
pus11Service.start();

// executePendingCommands() — dispatch by PUS type
switch (commandPacket.getType()) {
    case 5  -> pus5Service.executeTc(commandPacket);
    case 11 -> pus11Service.executeTc(commandPacket);
    case 17 -> pus17Service.executeTc(commandPacket);
}
```

---

## AbstractPusService API

```java
// Constructor
AbstractPusService(PusSimulator pusSimulator, int pusType)

// Lifecycle
void start()                              // override for periodic tasks

// Must implement
void executeTc(PusTcPacket tc)

// Create outgoing TM packet
PusTmPacket newPacket(int subtype, int userDataLength)

// PUS-1 verification responses
void ack_start(PusTcPacket tc)
void nack_start(PusTcPacket tc, int code)
void ack_completion(PusTcPacket tc)
void nack_completion(PusTcPacket tc, int code)

// Error code constants
START_ERR_INVALID_PUS_SUBTYPE = 1
START_ERR_NOT_IMPLEMENTED     = 2
COMPL_ERR_NOT_IMPLEMENTED     = 2
COMPL_ERR_INVALID_EVENT_ID    = 3
COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST = 4
```

All TX goes through `pusSimulator.transmitRealtimeTM(packet)` which appends CRC and sends.

---

## Packet Classes

### PusTmPacket (APID != 0, has secondary header)
- Layout: 6B CCSDS + 7B PUS secondary header + 8B PusTime + user data
- `DATA_OFFSET = 22`
- Auto-fills: type, subtype, counter, destination, PusTime.now()
- `getUserDataBuffer()` → ByteBuffer positioned at user data

### PusTmTimePacket (APID = 0, NO secondary header)
- Layout: 6B CCSDS + 1B rateExponent + 8B PusTime + 2B CRC
- No PUS secondary header (spec §8.9.1b)
- Sent via `pusSimulator.tmLink.sendImmediate(pkt)` (not transmitRealtimeTM)

### PusTcPacket
- `DATA_OFFSET = 11` (6B CCSDS + 5B PUS secondary header)
- `getUserDataBuffer()` → ByteBuffer positioned at application data
- `getType()`, `getSubtype()`, `getSourceId()`

---

## PusTime (CUC format)
- Length: 8 bytes: `[1B pfield=0x2F] + [4B coarse seconds] + [3B fine fraction]`
- `PusTime.now()` → current simulator time
- `time.encode(ByteBuffer)` → write to buffer
- `PusTime.read(ByteBuffer)` → parse from buffer
- Epoch: NONE (arbitrary counter), correlated by tco0 service

---

## Periodic Task Pattern (from Pus5Service)

```java
@Override
public void start() {
    pusSimulator.executor.scheduleAtFixedRate(this::doWork, 0, 1000, TimeUnit.MILLISECONDS);
}
```

To make a reschedulable periodic task:
```java
private ScheduledFuture<?> task;

private void reschedule(long periodMs) {
    if (task != null) task.cancel(false);
    task = pusSimulator.executor.scheduleAtFixedRate(
        this::doWork, 0, periodMs, TimeUnit.MILLISECONDS);
}
```

---

## MDB Container Hierarchy

```
ccsds                         ← extracts apid, seqcount (all packets)
├── pus-time  (apid == 0)     ← TM[9,2] time packets; extracts time-rate, time-type, obt-coarse, obt-fine
└── pus-tm    (apid != 0)     ← all normal TM; extracts type, subtype, counter, destination, pus-time
    ├── pus-tc-ack (type==1)  ← ST[01] ACK/NACK packets
    ├── hk (type==3)          ← housekeeping base
    ├── pus5-tm (type==5)     ← ST[05] event base
    └── pus9-tm (type==9)     ← ST[09] base (when added)
```

---

## MDB File Pattern (pus5.xml as template)

### Base container for a new service (type==N):
```xml
<SequenceContainer name="pusN-tm">
    <EntryList/>
    <BaseContainer containerRef="/PUS/pus-tm">
        <RestrictionCriteria>
            <Comparison parameterRef="/PUS/type" value="N" />
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

### Abstract TC base:
```xml
<MetaCommand name="pusN-tc" abstract="true">
    <BaseMetaCommand metaCommandRef="/PUS/pus-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="apid" argumentValue="1" />
            <ArgumentAssignment argumentName="type" argumentValue="N" />
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <CommandContainer name="pusN-tc"><EntryList/></CommandContainer>
</MetaCommand>
```

### Dynamic-size array (TM side — flat, single count):
```xml
<ArrayParameterType arrayTypeRef="element_type" name="array_type">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ParameterInstanceRef parameterRef="count_param" />
                    <LinearAdjustment intercept="-1" />
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayParameterType>
```

### Dynamic-size array (TC side — flat, single count): same but use `<ArgumentInstanceRef argumentRef="N" />`.

For **nested** (multi-level) arrays see the dedicated section below.

### Conditional field:
```xml
<ParameterRefEntry parameterRef="field">
    <IncludeCondition>
        <Comparison parameterRef="count" comparisonOperator="&gt;" value="0"/>
    </IncludeCondition>
</ParameterRefEntry>
```

---

## pus.xml Base Parameters (reference by `/PUS/paramName`)

| Parameter | Type | Description |
|-----------|------|-------------|
| `apid` | uint11 | CCSDS APID |
| `seqcount` | uint14 | Sequence count |
| `type` | uint8 | PUS service type |
| `subtype` | uint8 | PUS service subtype |
| `counter` | uint16 | Message counter |
| `destination` | uint16 | Destination ID |
| `time-rate` | uint8 | Rate exponent (time packets) |
| `time-type` | uint8 | CUC P-field (0x2F) |
| `obt-coarse` | uint32 | Coarse seconds |
| `obt-fine` | uint24 | Fine fraction |
| `pus-time` | AbsoluteTime | Decoded timestamp via tco0 |

---

## yamcs.pus.yaml — How to Add a New MDB File

Add to the `mdb:` list (order matters — later files can reference earlier ones):
```yaml
mdb:
  - type: "xtce"
    spec: "mdb/dt.xml"
  - type: "xtce"
    spec: "mdb/pus.xml"
  - type: "xtce"
    spec: "mdb/pus9.xml"   # ← add here
  - type: "xtce"
    spec: "mdb/pus5.xml"
  ...
```

---

## Nested Dynamic Array Patterns (Multi-Level)

### TC Side — Sibling-Member `ArgumentInstanceRef` in Nested Aggregates

YAMCS supports N-level nested arrays in TC arguments where **each array's size is a sibling member of its enclosing aggregate** — not a top-level argument.

Confirmed by: `yamcs-core/src/test/resources/xtce/array-in-array-arg.xml`

Pattern for a 3-level `N1 × (apid + N2 × (svc_type + N3 × subtype))` structure:

```xml
<!-- Level 3: array sized by N3 (sibling of svc_type in the containing aggregate) -->
<ArrayArgumentType name="subtype_array_type" arrayTypeRef="/dt/uint8">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N3"/>  <!-- sibling member -->
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<!-- Level 2 aggregate: svc_type + N3 + N3×subtype -->
<AggregateArgumentType name="svc_entry_type">
    <MemberList>
        <Member name="svc_type" typeRef="/dt/uint8"/>
        <Member name="N3"       typeRef="/dt/uint8"/>
        <Member name="subtypes" typeRef="subtype_array_type"/>
    </MemberList>
</AggregateArgumentType>

<!-- Level 2 array sized by N2 (sibling of apid in the containing aggregate) -->
<ArrayArgumentType name="svc_array_type" arrayTypeRef="svc_entry_type">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N2"/>  <!-- sibling member -->
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<!-- Level 1 aggregate: apid + N2 + N2×svc_entry -->
<AggregateArgumentType name="apid_entry_type">
    <MemberList>
        <Member name="apid"        typeRef="/dt/uint16"/>
        <Member name="N2"          typeRef="/dt/uint8"/>
        <Member name="svc_entries" typeRef="svc_array_type"/>
    </MemberList>
</AggregateArgumentType>

<!-- Outer array sized by N1 (top-level argument) -->
<ArrayArgumentType name="apid_array_type" arrayTypeRef="apid_entry_type">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N1"/>  <!-- top-level argument -->
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<MetaCommand name="TC_example">
    <ArgumentList>
        <Argument name="N1"      argumentTypeRef="/dt/uint8"/>
        <Argument name="entries" argumentTypeRef="apid_array_type"/>
    </ArgumentList>
    ...
</MetaCommand>
```

Key rule: `argumentRef="N3"` inside the `ArrayArgumentType` resolves to the `N3` member of the **enclosing** `AggregateArgumentType`, not a top-level arg. This chains arbitrarily deep. `N2=0` / `N3=0` → zero-length array in YAMCS UI → spec "add all" semantics.

---

### TM Side — Nested `ContainerRefEntry` + `RepeatEntry`

For variable-count nested TM structures, the **only** correct YAMCS pattern is nested `ContainerRefEntry` with `RepeatEntry`. Using `ArrayParameterType` with inner dimension referencing a field inside a containing aggregate does **NOT** work.

Confirmed by: `yamcs-xtce/src/main/java/org/yamcs/xtce/ParameterInstanceRef.java` line 53

The mechanism: `ParameterInstanceRef` defaults to `relativeTo = CURRENT_ENTRY_WITHIN_PACKET`, `instance = 0`, which calls `tmParams.getFromEnd(param, 0)` — the **most recently decoded** value of that parameter. Each outer loop iteration decodes a fresh inner count; the inner `RepeatEntry` automatically picks it up.

Pattern for a 3-level `N_outer × (key + N_inner × value)` structure:

```xml
<!-- Innermost container: one value element -->
<SequenceContainer name="inner_element">
    <EntryList>
        <ParameterRefEntry parameterRef="value_param"/>
    </EntryList>
</SequenceContainer>

<!-- Middle container: key + N_inner + N_inner×inner_element -->
<SequenceContainer name="middle_element">
    <EntryList>
        <ParameterRefEntry parameterRef="key_param"/>
        <ParameterRefEntry parameterRef="N_inner"/>
        <ContainerRefEntry containerRef="inner_element">
            <RepeatEntry>
                <Count>
                    <DynamicValue>
                        <!-- getFromEnd(N_inner, 0) → most recently decoded N_inner in this iteration -->
                        <ParameterInstanceRef parameterRef="N_inner"/>
                    </DynamicValue>
                </Count>
            </RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
</SequenceContainer>

<!-- Outer TM container: N_outer + N_outer×middle_element -->
<SequenceContainer name="TM_example" shortDescription="...">
    <EntryList>
        <ParameterRefEntry parameterRef="N_outer"/>
        <ContainerRefEntry containerRef="middle_element">
            <RepeatEntry>
                <Count>
                    <DynamicValue>
                        <ParameterInstanceRef parameterRef="N_outer"/>
                    </DynamicValue>
                </Count>
            </RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
    <BaseContainer containerRef="pusN-tm">...</BaseContainer>
</SequenceContainer>
```

Parameters like `N_inner` and `key_param` accumulate multiple `ParameterValue` instances across repeat iterations. `getFromEnd(param, 0)` always picks the latest one, so inner counts are always correct regardless of how many outer iterations have run.

Canonical reference implementation: `TM[14,4]` in `pus14.md` (3-level: APFCD → STFCD → RTFCD).

---

## Zero-Payload TC/TM Pattern (ST[17] reference)

ST[17] is the canonical example of a service where both TC and TM carry **no application data**.
Use this as a smoke-test template for a new YAMCS + simulator setup.

### Zero-argument MetaCommand (TC[17,1])

```xml
<MetaCommand name="TC_17_1" shortDescription="TC[17,1] Are-you-alive test">
    <!-- No <ArgumentList> element — zero application data per spec -->
    <CommandContainer name="TC_17_1">
        <EntryList>
            <FixedValueEntry name="ccsds-version"   binaryValue="00"   sizeInBits="3" />
            <FixedValueEntry name="ccsds-type"      binaryValue="01"   sizeInBits="1" />
            <FixedValueEntry name="ccsds-sec-hdr"   binaryValue="01"   sizeInBits="1" />
            <FixedValueEntry name="ccsds-apid"      binaryValue="0AA"  sizeInBits="11" />
            <FixedValueEntry name="ccsds-seq-flags" binaryValue="03"   sizeInBits="2" />
            <FixedValueEntry name="ccsds-seq-count" binaryValue="0000" sizeInBits="14" />
            <FixedValueEntry name="ccsds-length"    binaryValue="0001" sizeInBits="16" />
            <FixedValueEntry name="service-type"    binaryValue="11"   sizeInBits="8" />
            <FixedValueEntry name="service-subtype" binaryValue="01"   sizeInBits="8" />
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

Key: omit `<ArgumentList>` entirely. `ccsds-length` = total bytes after length field − 1.
For an 8-byte packet: (8 − 6) − 1 = 1 → `binaryValue="0001"`.

### Zero-payload SequenceContainer (TM[17,2])

```xml
<SequenceContainer name="TM_17_2" shortDescription="TM[17,2] Are-you-alive test report">
    <EntryList/>   <!-- self-closing = zero source data -->
    <BaseContainer containerRef="PUS17Packet">
        <RestrictionCriteria>
            <ComparisonList>
                <Comparison parameterRef="pus_apid"        value="170" />
                <Comparison parameterRef="service_type"    value="17" />
                <Comparison parameterRef="service_subtype" value="2" />
            </ComparisonList>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

Key: `<EntryList/>` (self-closing) — YAMCS decodes only the header parameters from the
base container; no additional parameters are extracted.

### Python builder for zero-payload TM

```python
def build_tm_17_2() -> bytes:
    secondary    = struct.pack(">BB", 17, 2)   # service_type, service_subtype
    pkt_data_len = len(secondary) - 1          # = 1
    word1 = (1 << 11) | APID                   # TM: type=0, sec_hdr=1
    word2 = (0b11 << 14) | _next_seq()
    return struct.pack(">HHH", word1, word2, pkt_data_len) + secondary  # 8 bytes
```

### Smoke-test value

ST[17] end-to-end round-trip (TC[17,1] in → TM[17,2] out) validates the entire stack:
YAMCS TC encoding → UDP uplink → simulator dispatch → UDP downlink → YAMCS TM decoding.

---

## ST[19] Event-Action Service Patterns

### Embedded TC Packet in TC/TM Payload (ST[19] reference)

TC[19,1] and TM[19,11] carry a **raw TC packet** as a payload field. XTCE has no way to
express "a variable-length TC packet embedded in another packet". Two workarounds:

**Workaround A (recommended)**: Non-standard `request_tc_len:uint16` length prefix before the raw bytes. XTCE declares only the fixed fields; Python parses `request_tc_len` + `request_tc_bytes` manually after the last XTCE-declared argument.

**Workaround B**: Fix the action request to a single known TC type (e.g., always TC[17,1] = 8 bytes). No length prefix needed; XTCE can declare 8 FixedValueEntry bytes as part of the aggregate. Only viable when the mission restricts EA requests to one TC type.

### Event-Action Function State Machine

Two independent status layers:
```
ea_function_enabled (global bool)  ← set by TC[19,8]/TC[19,9]
ea_definitions[(app_pid, event_id)]['status']  ← per-definition enabled/disabled
```
Disabling the function does NOT change individual definition statuses (and vice versa).
New definitions added via TC[19,1] start in `status='disabled'`.

### EventBus Pattern (ST[05]/ST[19] coupling)

ST[19] must observe ST[05] events. In a multi-service Python simulator:

```python
class EventBus:
    def __init__(self): self._listeners = []
    def subscribe(self, fn): self._listeners.append(fn)
    def post(self, app_pid: int, event_id: int):
        for fn in self._listeners: fn(app_pid, event_id)

EVENT_BUS = EventBus()
# ST[05] simulator: EVENT_BUS.post(app_pid, event_id)
# ST[19] simulator: EVENT_BUS.subscribe(on_event_handler)
```

This decouples the two services; neither needs to import the other.

### N=0 "All Definitions" Encoding Variants (ST[19] vs ST[15])

ST[15] uses the **zero-payload** convention for "all stores" (empty payload).
ST[19] uses **both conventions** depending on the subtype:
- TC[19,4]/TC[19,5]: zero-payload (per §6.19.7) → use two MetaCommands (`_specific` + `_all`)
- TC[19,10]: N=0 in-band (per §8.19.2.10c) → single MetaCommand; Python checks `num_entries==0`

Always check the spec carefully for which convention applies per subtype.

---

## ST[14] Real-Time Forwarding Control Patterns

### Cross-Cutting Forwarding Gate

ST[14] controls which TM packets are forwarded to ground. The gate must intercept **all** outgoing TM — not just packets from Pus14Service itself. Insert the check in `PusSimulator.transmitRealtimeTM()`:

```java
public void transmitRealtimeTM(PusTmPacket pkt) {
    if (pus14Service != null && !pus14Service.shouldForward(pkt)) {
        return;  // blocked by ST[14] APFCC
    }
    // ... existing CRC append and send logic ...
}
```

This requires zero changes to individual service classes (Pus5Service, Pus11Service, etc.).

**Default startup state**: pass-all (no APFCC populated). Spec-strict default is block-all, but pass-all is more useful for simulator bring-up. Toggle via a config flag if needed.

### APFCC Data Structures

```java
// Application Process Forward-Control Configuration
Map<Integer, ApfcDefinition> apfcc = new LinkedHashMap<>();

// HK Forward-Control Configuration  (apid → set of struct IDs, null = pass-all)
Map<Integer, Set<Integer>> hkFcc  = new LinkedHashMap<>();

// Diagnostic Forward-Control Configuration
Map<Integer, Set<Integer>> diagFcc = new LinkedHashMap<>();

class ApfcDefinition {
    int apid;
    // Empty map = "pass all services for this APID"
    // null value for a service key = "pass all subtypes of that service"
    Map<Integer, Set<Integer>> serviceSubtypes = new LinkedHashMap<>();
}
```

### shouldForward Logic

```java
public boolean shouldForward(PusTmPacket pkt) {
    int apid    = pkt.getApid();
    int svcType = pkt.getType();
    int subtype = pkt.getSubtype();

    ApfcDefinition apfcd = apfcc.get(apid);
    if (apfcd == null) return true;                     // pass-all default

    Map<Integer, Set<Integer>> stfcds = apfcd.serviceSubtypes;
    if (stfcds.isEmpty()) return true;                  // APID entry exists, no restrictions

    Set<Integer> subtypes = stfcds.get(svcType);
    if (subtypes == null) return false;                 // service not in allowed list
    if (subtypes.isEmpty()) return true;                // all subtypes of this service allowed
    return subtypes.contains(subtype);
}
```

### Two-Variant MetaCommand Pattern for "Delete-All" TCs

When a TC can either carry N entries to delete **or** carry zero payload meaning "delete everything", two MetaCommands are required. The zero-byte payload is the runtime discriminator (checked in Java via `bb.remaining() == 0`).

Affected subtypes in ST[14]: TC[14,2] (empty APFCC), TC[14,6] (empty HK FCC), TC[14,10] (empty diag FCC).

```xml
<!-- Variant A: delete specific entries (reuses existing array argument type) -->
<MetaCommand name="TC_14_2_DELETE_ENTRIES" ...>
    <ArgumentList>
        <Argument name="N1" argumentTypeRef="/dt/uint8"/>
        <Argument name="apfcd_entries" argumentTypeRef="apfcd_array_type"/>
    </ArgumentList>
    ...
</MetaCommand>

<!-- Variant B: empty the entire table (zero payload) -->
<MetaCommand name="TC_14_2_EMPTY_APFCC" ...>
    <!-- No ArgumentList -->
    <CommandContainer name="TC_14_2_EMPTY">
        <EntryList/>
        <BaseContainer containerRef="pus14-tc"/>
    </CommandContainer>
</MetaCommand>
```

Java handler:
```java
case 2 -> {
    if (bb.remaining() == 0) { apfcc.clear(); ack_completion(tc); return; }
    // ... parse N1/N2/N3 delete entries ...
}
```

This two-variant pattern is spec-faithful, not a workaround — the two forms are mutually exclusive requests with incompatible argument structures.

---

## ST[15] Service Patterns

### PacketStore State Machine

Each packet store has three independent status variables:

| Variable | Values | Changed by |
|----------|--------|------------|
| `storage_enabled` | True/False | TC[15,1] / TC[15,2] |
| `open_retrieval_status` | IN_PROGRESS / SUSPENDED | TC[15,15] / TC[15,16] |
| `btr_status` | ENABLED / DISABLED | TC[15,9] (start) / TC[15,17] (abort) / auto on completion |

On TC[15,20] create: storage=DISABLED, open_retrieval=SUSPENDED, btr=DISABLED.
Delete (TC[15,21]) requires all three to be in their "inactive" state.
Resize (TC[15,25]) and VC change (TC[15,28]) also require all inactive.

### "All stores" vs "Specific stores" TC Pattern

Several ST[15] TCs accept either N specific store IDs or "all stores" (empty payload). XTCE cannot model optional arguments, so use **two MetaCommands per subtype**:
- `TC_15_N_specific` — arguments: `num_stores:uint8` + `store_ids:uint16[]`
- `TC_15_N_all` — no arguments

The simulator disambiguates at runtime by checking remaining payload length after the header. Affected subtypes: 1, 2, 12, 14, 15, 16, 17, 21.

### CUC Timestamp in Simplified PUS (test_yamcs style)

The full YAMCS/PUS environment encodes CUC as a 7-byte secondary header field (1B pfield + 4B coarse + 3B fine → decoded via tco0). The test_yamcs Python simulator uses a simplified header without that secondary PUS header. For ST[15] TCs/TMs carrying timestamps (subtypes 9, 11, 13, 14):

**Add to `pus_dt.xml`**:
```xml
<IntegerParameterType signed="false" name="uint64">
    <IntegerDataEncoding encoding="unsigned" sizeInBits="64" />
</IntegerParameterType>
```
Encode CUC as a raw 8-byte big-endian uint64 (pfield byte + 4B coarse + 3B fine, packed as 8 bytes). Handle CUC interpretation in Python, not in XTCE.

Alternative: split into `cuc_coarse:uint32` + `cuc_fine:uint32` (easier for human inspection).

### TM Bus / Packet Store Interception Pattern

In single-service simulators (e.g. `pus20_simulator.py`), TM is sent directly via UDP socket. ST[15] requires that all outgoing TM be optionally copied into packet stores.

**Pattern**: introduce a shared `PacketStoreManager` class:
```python
class PacketStoreManager:
    def submit_tm(self, raw_packet: bytes) -> None:
        for store in self.stores.values():
            if store.storage_enabled and self._matches_filter(store, raw_packet):
                store.append(raw_packet, timestamp=time.time())
        self.udp_sock.sendto(raw_packet, (TM_HOST, TM_PORT))
```
All services call `PacketStoreManager.submit_tm()` instead of direct socket sends. The `_matches_filter` method checks the packet's APID (bytes 0-1), service type (byte 6), and subtype (byte 7) against the store's application-process storage-control config.

### Retrieval Background Thread Pattern (extends Periodic Task Pattern)

Open retrieval and by-time-range retrieval are continuous background processes — not one-shot responses. Use the same executor pattern as ST[11] scheduling:

```python
# By-time-range retrieval
def _start_btr_thread(self, store, start_ts, end_ts):
    def _run():
        for pkt, ts in store.packets:
            if start_ts <= ts <= end_ts:
                self.manager.udp_sock.sendto(pkt, (TM_HOST, TM_PORT))
        store.btr_status = BtrStatus.DISABLED
    threading.Thread(target=_run, daemon=True).start()

# Open retrieval — continuous, cursor-advancing
def _start_open_retrieval(self, store):
    def _run():
        while store.open_retrieval_status == OpenRetrievalStatus.IN_PROGRESS:
            cursor = store.open_retrieval_start_time
            new_pkts = [(p, t) for p, t in store.packets if t >= cursor]
            for pkt, ts in new_pkts:
                self.manager.udp_sock.sendto(pkt, (TM_HOST, TM_PORT))
                store.open_retrieval_start_time = ts
            time.sleep(0.1)
    threading.Thread(target=_run, daemon=True).start()
```

### Nested Aggregate Arrays in XTCE (for TM[15,6/36/38/40])

These TM packets have a multi-level hierarchy: list of app-processes, each containing a variable-length list of service/struct/event IDs.

**Correct approach**: Use nested `ContainerRefEntry` + `RepeatEntry` (see "Nested Dynamic Array Patterns" section above). The `ParameterInstanceRef` with default `instance=0` resolves to the most recently decoded count — correct per outer iteration. This is confirmed working; no flattening needed.

**Do not use**: `ArrayParameterType` with inner `DynamicValue` referencing a field inside a containing `AggregateParameterType`. YAMCS does not resolve inner-aggregate parameter references for TM array dimensions.

---

## ST[20] On-Board Parameter Management Patterns

### Overall Feasibility
ST[20] (TC[20,1], TM[20,2], TC[20,3]) is **fully implementable with XTCE + minor simulator code**.
The only spec feature not expressible in XTCE is the "deduced" value type — resolved by mission
convention of fixing all parameter values to uint32.

### PARAM_STORE Pattern

```python
PARAM_STORE: dict[int, int] = {
    0x1001: 42,        # voltage_level  (uint32, mV)
    0x1002: 3000,      # temperature_raw
}
```

- Keys: 16-bit param_id (matches the `param_id` field in TC/TM packets)
- Values: 32-bit unsigned integer (mission convention — all params are uint32)
- TC[20,1] reads from store; TC[20,3] writes to store.

### Deduced-Value Mission Convention

The PUS spec marks the value field as "deduced" (type depends on param_id). XTCE cannot
express per-param-id type variation. Resolution: **fix all on-board parameters to uint32**
and document in mission ICD. This allows:
- `AggregateParameterType { param_id:uint16, param_value:uint32 }` for TM[20,2]
- `AggregateArgumentType  { param_id:uint16, param_value:uint32 }` for TC[20,3]

### Partial Per-Param Execution Pattern

Spec §6.20.4.1f says: process valid params even when the same TC contains invalid ones.
XTCE cannot express this — implement in simulator:

```python
for pid, val in entries:
    if pid not in PARAM_STORE:
        log.warning("unknown param_id 0x%04X, skipping", pid)
        continue       # skip invalid, continue processing rest
    PARAM_STORE[pid] = val
```

This pattern applies to both TC[20,1] (read) and TC[20,3] (set).

### TC[20,3] Set Values — Packet Layout

```
[6]   service_type    = 20
[7]   service_subtype = 3
[8]   N (uint8)
[9+]  N × { param_id:uint16, value:uint32 }  (6 bytes per entry)
```

Parse: `struct.unpack_from(">HI", data, 9 + i * 6)` for each entry i in range(N).

---

## ST[21] Request Sequencing Patterns

### Fixed-String ID Mission Convention

The spec defines `request_sequence_ID` as "fixed character-string" without specifying the
length. Mission fixes to **16 bytes (128 bits), null-padded ASCII**:

```python
# Encode:  sid.encode('ascii').ljust(16, b'\x00')[:16]
# Decode:  data[offset:offset+16].decode('ascii').rstrip('\x00')
```

In XTCE, declare as a `StringArgumentType`/`StringParameterType` with:
```xml
<StringDataEncoding>
    <SizeInBits><Fixed><FixedValue>128</FixedValue></Fixed></SizeInBits>
</StringDataEncoding>
```

### SEQUENCE_STORE State Model

```python
SEQUENCE_STORE: dict[str, RequestSequence]   # key = seq_id string

# RequestSequence fields:
#   id          : str              (16-byte ID)
#   status      : "inactive" | "under_execution"
#   entries     : list[(raw_tc: bytes, delay_ms: int)]
#   exec_thread : threading.Thread | None
```

Execution status enum (Table 8-22): inactive=0, under_execution=1.

### Embedded-TC-in-Sequence Workaround (extends ST[19] pattern)

TC[21,1] and TM[21,12] carry `N × {TC_packet (variable-length), delay}` — the same structural
gap as ST[19]. XTCE declares only `seq_id (16B fixed-string)` + `N (uint8)`; the entry block is
raw binary parsed entirely in Python.

**Mission wire format for each entry**:
```
tc_len : uint16  (big-endian, length of TC packet in bytes)
TC_packet: raw bytes (tc_len bytes)
delay_ms : uint32  (big-endian, milliseconds — mission relative-time convention)
```

**Parse snippet**:
```python
offset = 25   # after 16B seq_id + 1B N
for _ in range(n):
    tc_len = struct.unpack_from(">H", data, offset)[0]; offset += 2
    raw_tc = data[offset:offset + tc_len];              offset += tc_len
    delay_ms = struct.unpack_from(">I", data, offset)[0]; offset += 4
    entries.append((raw_tc, delay_ms))
```

### Sequence Execution Thread Pattern

Each activated sequence runs in a daemon thread. The thread checks `seq.status` before each
entry to support abort:

```python
def _run_sequence(seq):
    for raw_tc, delay_ms in seq.entries:
        if seq.status != "under_execution":
            break                      # aborted externally
        TC_RELAY_SOCK.sendto(raw_tc, TC_RELAY_ADDR)
        time.sleep(delay_ms / 1000.0)
    seq.status = "inactive"
    seq.exec_thread = None
```

TC[21,5] abort: set `seq.status = "inactive"` — thread exits on next iteration check.
TC[21,13] abort-all: iterate all sequences; set inactive; collect IDs; send TM[21,14].

### Dual-MetaCommand Variant for Optional File Path (TC[21,2], TC[21,8])

XTCE cannot express optional blocks or variable-length strings. Use two MetaCommands:
- `TC_21_2_no_path`: seq_id only (16 bytes); loading policy selects the file on-board.
- `TC_21_2_with_path`: seq_id + `repo_path_len:uint8` + `repo_path_bytes` +
  `file_name_len:uint8` + `file_name_bytes`; Python decodes the string fields.

Same dual-variant pattern applies to TC[21,8] (load-by-reference-and-activate).

### Sequence Checksum Convention

TC[21,9] requests a checksum; TM[21,10] reports it. Mission uses **CRC-16/CCITT** over the
concatenated serialized entries (raw_tc bytes + delay_ms bytes per entry):

```python
def crc16(data: bytes) -> int:
    crc = 0xFFFF
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            crc = (crc << 1) ^ 0x1021 if crc & 0x8000 else crc << 1
    return crc & 0xFFFF
```

---

## Build & Run

```bash
# Build (skip tests)
mvn -pl simulator,examples/pus clean install -DskipTests

# Run
mvn -pl examples/pus yamcs:run
```