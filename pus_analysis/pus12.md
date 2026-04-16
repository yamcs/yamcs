# PUS Service 12 — On-board Monitoring

## Section A: General Context

### Purpose

ST[12] On-board Monitoring provides the capability to monitor on-board parameters against ground-defined conditions and raise events (via ST[05]) when violations occur. The satellite evaluates monitoring rules autonomously — no ground contact required during monitoring.

### Two Subservices

| Subservice | Description | Required? |
|---|---|---|
| **Parameter Monitoring (PMON)** | Checks individual telemetry parameters against limit, expected-value, or delta thresholds | ✅ Yes |
| **Functional Monitoring (FMON)** | Groups PMON definitions into higher-level health checks; raises events when a minimum number of PMON checks fail simultaneously | ❌ Not required |

This document covers only the **parameter monitoring subservice**.

---

### Key Concepts

| Concept | Description |
|---|---|
| **PMON ID** | Unique identifier for a monitoring definition (enumerated, uint16) |
| **PMON list** | Runtime list of all active monitoring definitions; max capacity declared at spec time |
| **PMON function status** | Global on/off switch for all parameter monitoring; starts `"enabled"` on boot |
| **PMON status** | Per-definition `"enabled"` / `"disabled"` flag |
| **PMON checking status** | Result of last evaluation cycle (see enumerations below) |
| **Monitored parameter ID** | The on-board parameter being checked (enumerated) |
| **Check validity condition** | Optional gate: if `(validity_param & mask) != expected_value`, skip the check and set status to `"invalid"` |
| **Repetition number** | N consecutive consistent check results required to establish a new checking status |
| **Monitoring interval** | Time between consecutive evaluations (in on-board minimum sampling interval units) |
| **Check transition list** | Accumulates status transitions; flushed when TM[12,12] check transition report is generated |

---

### Check Types (Table 8-6)

| Raw Value | Engineering Value | Description |
|---|---|---|
| 0 | `expected-value-checking` | `(param & mask) == expected_value` |
| 1 | `limit-checking` | `low_limit ≤ param ≤ high_limit` |
| 2 | `delta-checking` | Average of N consecutive `|param[n] - param[n-1]|` values compared to thresholds |

---

### PMON Checking Status Enumerations

**Limit-check (Table 8-8):**

| Raw | Engineering |
|---|---|
| 0 | `within limits` |
| 1 | `unchecked` |
| 2 | `invalid` |
| 3 | `below low limit` |
| 4 | `above high limit` |

**Expected-value-check (Table 8-7):**

| Raw | Engineering |
|---|---|
| 0 | `expected value` |
| 1 | `unchecked` |
| 2 | `invalid` |
| 3 | `unexpected value` |

**Delta-check (Table 8-9):**

| Raw | Engineering |
|---|---|
| 0 | `within thresholds` |
| 1 | `unchecked` |
| 2 | `invalid` |
| 3 | `below low threshold` |
| 4 | `above high threshold` |

**PMON status (Table 8-10):** `0 = disabled`, `1 = enabled`

---

### Execution Flow

```
TC[12,5] arrives → ACK[1,1] → executeTc() → parse + add to pmonList → ACK[1,7]
Periodic task   → for each enabled PMON def → sample param → evaluate check
                  → if N consecutive matches → establish new checking status
                  → if status changed → add to check transition list
                  → if event_id != 0 → raise PUS-5 event
TC[12,13]       → collect all PMON statuses → emit TM[12,14]
TC[12,8]        → collect requested PMON definitions → emit TM[12,9]
```

---

### Architecture Files (to be created)

| Purpose | Path |
|---|---|
| Java service | `simulator/src/main/java/org/yamcs/simulator/pus/Pus12Service.java` |
| XTCE MDB | `examples/pus/src/main/yamcs/mdb/pus12.xml` |
| Register in | `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java` |
| Add MDB to | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` |

---

### Critical XTCE Limitation — Type-Deduced Field Sizes

The most significant challenge for PUS 12 in XTCE is that the packet fields for check criteria (`mask`, `low_limit`, `high_limit`, `expected_value`, `low_threshold`, `high_threshold`) have **sizes and formats deduced from the monitored parameter's type** at definition time. The spec says these are "deduced" — meaning a float32 parameter uses 4-byte limits, a uint8 parameter uses 1-byte limits, etc.

XTCE cannot express this polymorphism within a single `MetaCommand` or `SequenceContainer`. This affects TC[12,5], TC[12,7], and TM[12,9]. Options are documented per-subtype below.

---

## Section B: Per-TM/TC Implementation Plan

---

### TC[12,1] — Enable Parameter Monitoring Definitions

**Spec:** §6.12.3.6.1 / Fig 8-111

**Packet structure:**

```
| N (uint) | PMON ID × N (enumerated) |
```

**Action:** For each listed PMON ID: reset repetition counter; set PMON status = `"enabled"`.

**XTCE:** ✅ **XTCE-only** — standard N + array-of-enum-IDs pattern (identical to TC[5,5] pattern in `pus5.xml`).

```xml
<!-- ArgumentTypeSet -->
<EnumeratedArgumentType name="pmon_id_type">
  <IntegerDataEncoding sizeInBits="16"/>
  <EnumerationList>
    <Enumeration value="1" label="PMON_TEMP_CHECK"/>
    <Enumeration value="2" label="PMON_VOLT_CHECK"/>
    <!-- add more as needed -->
  </EnumerationList>
</EnumeratedArgumentType>
<ArrayArgumentType arrayTypeRef="pmon_id_type" name="pmon_id_array_type">
  <DimensionList>
    <Dimension>
      <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
      <EndingIndex>
        <DynamicValue>
          <ArgumentInstanceRef argumentRef="N"/>
          <LinearAdjustment intercept="-1"/>
        </DynamicValue>
      </EndingIndex>
    </Dimension>
  </DimensionList>
</ArrayArgumentType>

<!-- MetaCommand -->
<MetaCommand name="ENABLE_PARAMETER_MONITORING">
  <BaseMetaCommand metaCommandRef="pus12-tc">
    <ArgumentAssignmentList>
      <ArgumentAssignment argumentName="subtype" argumentValue="1"/>
    </ArgumentAssignmentList>
  </BaseMetaCommand>
  <ArgumentList>
    <Argument name="N" argumentTypeRef="/dt/uint8"/>
    <Argument name="pmon_ids" argumentTypeRef="pmon_id_array_type"/>
  </ArgumentList>
  <CommandContainer name="ENABLE_PARAMETER_MONITORING">
    <EntryList>
      <ArgumentRefEntry argumentRef="N"/>
      <ArgumentRefEntry argumentRef="pmon_ids"/>
    </EntryList>
    <BaseContainer containerRef="pus12-tc"/>
  </CommandContainer>
</MetaCommand>
```

**Java:** `case 1 → enablePmonDefinitions(bb)` — reads `uint8 N`, loops N × `uint16 pmonId`, validates each exists in `pmonList`, sets `status = ENABLED`, resets `repetitionCounter = 0`.

**Rejection condition:** PMON ID not in list → NACK[1,4].

---

### TC[12,2] — Disable Parameter Monitoring Definitions

**Spec:** §6.12.3.6.2 / Fig 8-112

**Packet structure:** Identical to TC[12,1].

**Action:** For each listed PMON ID: set PMON status = `"disabled"`; set PMON checking status = `"unchecked"`.

**XTCE:** ✅ **XTCE-only** — same pattern as TC[12,1].

**Java:** `case 2 → disablePmonDefinitions(bb)` — reads N + IDs; sets `status = DISABLED`, `checkingStatus = UNCHECKED`.

**Rejection conditions:** PMON ID not in list; PMON is used by a protected FMON definition (not applicable — FMON not implemented).

---

### TC[12,5] — Add Parameter Monitoring Definitions

**Spec:** §6.12.3.9.1 / Fig 8-114, 8-115, 8-116, 8-117

**Packet structure (per entry, repeated N times):**

```
| PMON_ID | monitored_param_ID | [validity_param_ID + mask + expected_value]* |
| [monitoring_interval]* | repetition_number | check_type | check_type_dependent_criteria |
```

*optional fields

**Check type dependent criteria (Fig 8-116 for limit, Fig 8-115 for expected-value, Fig 8-117 for delta):**

| Check Type | Criteria Fields |
|---|---|
| Limit | `low_limit (deduced)` + `low_event_id (uint16)` + `high_limit (deduced)` + `high_event_id (uint16)` |
| Expected-value | `mask (deduced)` + `spare (optional)` + `expected_value (deduced)` + `event_id (uint16)` |
| Delta | `low_threshold (deduced)` + `low_event_id (uint16)` + `high_threshold (deduced)` + `high_event_id (uint16)` + `num_consecutive_deltas (uint)` |

**XTCE:** ⚠️ **XTCE partial — requires Java parsing**

The `deduced` fields have sizes that depend on the monitored parameter's type. XTCE cannot express a single generic MetaCommand for this.

**Two practical approaches:**

**Option A — Per-type TC variants (recommended):** Define separate XTCE commands for each combination of check type and parameter type used in the simulator. For the PUS simulator's known parameters (float32 analog, uint8/uint16 digital):

```xml
<!-- TC for limit-checking a float32 parameter -->
<MetaCommand name="ADD_PMON_LIMIT_F32">
  <!-- subtype=5 -->
  <ArgumentList>
    <Argument name="pmon_id"       argumentTypeRef="pmon_id_type"/>
    <Argument name="param_id"      argumentTypeRef="monitored_param_id_type"/>
    <Argument name="rep_number"    argumentTypeRef="/dt/uint8"/>
    <Argument name="low_limit"     argumentTypeRef="/dt/float32"/>
    <Argument name="low_event_id"  argumentTypeRef="/dt/uint16"/>
    <Argument name="high_limit"    argumentTypeRef="/dt/float32"/>
    <Argument name="high_event_id" argumentTypeRef="/dt/uint16"/>
  </ArgumentList>
  <!-- CommandContainer: pmon_id + param_id + rep_number + check_type=1 (fixed) + low_limit + ... -->
</MetaCommand>

<!-- TC for expected-value-checking a uint8 parameter -->
<MetaCommand name="ADD_PMON_EXPECTED_U8">
  <!-- subtype=5 -->
  <ArgumentList>
    <Argument name="pmon_id"     argumentTypeRef="pmon_id_type"/>
    <Argument name="param_id"    argumentTypeRef="monitored_param_id_type"/>
    <Argument name="rep_number"  argumentTypeRef="/dt/uint8"/>
    <Argument name="mask"        argumentTypeRef="/dt/uint8"/>
    <Argument name="expected"    argumentTypeRef="/dt/uint8"/>
    <Argument name="event_id"    argumentTypeRef="/dt/uint16"/>
  </ArgumentList>
</MetaCommand>
```

**Option B — Raw binary criteria (simpler, less operator-friendly):** Define `criteria` as `BinaryArgumentType`; Java parses internally based on prior knowledge of each PMON ID's parameter type.

**Java:** `case 5 → addPmonDefinitions(bb)` full implementation:
```java
// For each of N entries:
int pmonId    = bb.getShort() & 0xFFFF;
int paramId   = bb.getShort() & 0xFFFF;
int repNum    = bb.get() & 0xFF;
int checkType = bb.get() & 0xFF;
// check_type determines how to parse remaining bytes:
switch (checkType) {
    case 1: // limit — size depends on param's type (e.g. 4B float32)
        float lowLimit    = bb.getFloat();
        int   lowEventId  = bb.getShort() & 0xFFFF;
        float highLimit   = bb.getFloat();
        int   highEventId = bb.getShort() & 0xFFFF;
        pmonList.put(pmonId, new PmonDefinition(...));
}
```

**Rejection conditions:** PMON list full; PMON ID already exists; param not accessible; low limit > high limit.

---

### TC[12,6] — Delete Parameter Monitoring Definitions

**Spec:** §6.12.3.9.3 / Fig 8-118

**Packet structure:**

```
| N (uint) | PMON ID × N (enumerated) |
```

**Action:** Remove each listed PMON ID from `pmonList`; remove associated entries from check transition list.

**XTCE:** ✅ **XTCE-only** — same N + array-of-IDs pattern as TC[12,1/2].

**Java:** `case 6 → deletePmonDefinitions(bb)` — reads N + IDs; for each: validates status == DISABLED (reject otherwise); removes from `pmonList`.

**Rejection conditions:** PMON ID not in list; PMON status is `"enabled"` (must disable first); PMON used by protected FMON (N/A here).

---

### TC[12,7] — Modify Parameter Monitoring Definitions

**Spec:** §6.12.3.9.4 / Fig 8-119, 8-120, 8-121, 8-122

**Packet structure (per entry):**

```
| PMON_ID | monitored_param_ID | repetition_number | check_type | check_type_dependent_criteria |
```

Same `deduced` field issue as TC[12,5]. Check type dependent criteria structures are identical to TC[12,5] but **the check type must match the existing definition** — cannot change check type via modify.

**XTCE:** ⚠️ **XTCE partial — requires Java parsing** — same issue as TC[12,5]. Per-type variants:

```xml
<MetaCommand name="MODIFY_PMON_LIMIT_F32">
  <!-- subtype=7 -->
  <!-- Same args as ADD_PMON_LIMIT_F32 minus initial setup args -->
</MetaCommand>
```

**Java:** `case 7 → modifyPmonDefinitions(bb)` — reads N + entries; for each: validates check_type matches existing definition (NACK if mismatch); updates criteria fields; sets `checkingStatus = UNCHECKED`; resets `repetitionCounter = 0`.

**Rejection conditions:** PMON ID not in list; check_type differs from existing; monitored_param_id differs from existing; limit constraints violated.

---

### TC[12,8] — Report Parameter Monitoring Definitions

**Spec:** §6.12.3.10 / Fig 8-123

**Packet structure:**

```
| N (uint) | PMON ID × N (enumerated) |
```

**Special:** If N = 0, report **all** definitions in the PMON list.

**Response:** TM[12,9].

**XTCE:** ✅ **XTCE-only** — standard N + array-of-IDs. The N=0 convention is purely semantic (Java interprets it); XTCE sends N=0 with empty array.

**Java:** `case 8 → reportPmonDefinitions(bb)` — reads N; if N==0 collect all pmonList entries; else collect requested IDs; call `sendPmonDefinitionReport(ids)`.

---

### TM[12,9] — Parameter Monitoring Definition Report

**Spec:** §6.12.3.10 / Fig 8-124, 8-125, 8-126, 8-127

**Packet structure:**

```
| [max_transition_reporting_delay (uint)]* | N (uint) |
  repeated N times:
    | PMON_ID | monitored_param_ID |
    | [validity_param_ID + mask + expected_value]* |
    | [monitoring_interval]* | PMON_status | repetition_number | check_type |
    | check_type_dependent_criteria |
```

*optional fields

**XTCE:** ⚠️ **XTCE partial — fixed header only**

Same `deduced` field issue in criteria. For the YAMCS container, define a SequenceContainer that decodes the outer fixed fields only:

```xml
<SequenceContainer name="PMON_DEFINITION_REPORT">
  <EntryList>
    <ParameterRefEntry parameterRef="pmon_def_count"/>
    <!-- per-entry PMON_ID, param_ID, pmon_status, check_type decoded here -->
    <!-- criteria bytes remain opaque binary -->
  </EntryList>
  <BaseContainer containerRef="pus12-tm">
    <RestrictionCriteria>
      <Comparison parameterRef="/PUS/subtype" value="9"/>
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>
```

**Java emitter:** `sendPmonDefinitionReport(List<Integer> pmonIds)`:
```
Packet layout per entry:
  uint16 pmon_id
  uint16 param_id
  uint8  pmon_status
  uint8  repetition_number
  uint8  check_type
  [criteria bytes based on check_type and param type]

Total size: 2 + N × (7 + criteria_size)
criteria_size = 14 for limit-f32 (4+2+4+2+2 spare), 6 for expected-u8, etc.
```

---

### TC[12,13] — Report Status of Each Parameter Monitoring Definition

**Spec:** §6.12.3.11 / §8.12.2.13

**Packet structure:** No arguments — application data field omitted.

**Action:** Emit TM[12,14] containing PMON ID + PMON status for every entry in the PMON list.

**XTCE:** ✅ **XTCE-only** — no-argument command, same pattern as TC[11,17] / TC[11,18].

```xml
<MetaCommand name="REPORT_PMON_STATUS">
  <BaseMetaCommand metaCommandRef="pus12-tc">
    <ArgumentAssignmentList>
      <ArgumentAssignment argumentName="subtype" argumentValue="13"/>
    </ArgumentAssignmentList>
  </BaseMetaCommand>
  <CommandContainer name="REPORT_PMON_STATUS">
    <EntryList/>
    <BaseContainer containerRef="pus12-tc"/>
  </CommandContainer>
</MetaCommand>
```

**Java:** `case 13 → sendPmonStatusReport()` — iterates `pmonList`, emits TM[12,14].

---

### TM[12,14] — Parameter Monitoring Definition Status Report

**Spec:** §6.12.3.11 / Fig 8-130

**Packet structure:**

```
| N (uint) | repeated N times: PMON_ID (enumerated) | PMON_status (enumerated) |
```

**XTCE:** ✅ **XTCE-only** — fully expressible using aggregate parameter type + dynamic array.

```xml
<!-- ParameterTypeSet -->
<EnumeratedParameterType name="pmon_id_type">
  <IntegerDataEncoding sizeInBits="16"/>
  <EnumerationList>
    <Enumeration value="1" label="PMON_TEMP_CHECK"/>
    <Enumeration value="2" label="PMON_VOLT_CHECK"/>
  </EnumerationList>
</EnumeratedParameterType>

<EnumeratedParameterType name="pmon_status_type">
  <IntegerDataEncoding sizeInBits="8"/>
  <EnumerationList>
    <Enumeration value="0" label="disabled"/>
    <Enumeration value="1" label="enabled"/>
  </EnumerationList>
</EnumeratedParameterType>

<AggregateParameterType name="pmon_status_entry_type">
  <MemberList>
    <Member name="pmon_id"     typeRef="pmon_id_type"/>
    <Member name="pmon_status" typeRef="pmon_status_type"/>
  </MemberList>
</AggregateParameterType>

<IntegerParameterType name="pmon_count_type" baseType="/dt/uint8"/>

<ArrayParameterType arrayTypeRef="pmon_status_entry_type" name="pmon_status_array_type">
  <DimensionList>
    <Dimension>
      <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
      <EndingIndex>
        <DynamicValue>
          <ParameterInstanceRef parameterRef="pmon_count"/>
          <LinearAdjustment intercept="-1"/>
        </DynamicValue>
      </EndingIndex>
    </Dimension>
  </DimensionList>
</ArrayParameterType>

<!-- ContainerSet -->
<SequenceContainer name="PMON_STATUS_REPORT">
  <EntryList>
    <ParameterRefEntry parameterRef="pmon_count"/>
    <ParameterRefEntry parameterRef="pmon_status_entries">
      <IncludeCondition>
        <Comparison parameterRef="pmon_count" comparisonOperator="&gt;" value="0"/>
      </IncludeCondition>
    </ParameterRefEntry>
  </EntryList>
  <BaseContainer containerRef="pus12-tm">
    <RestrictionCriteria>
      <Comparison parameterRef="/PUS/subtype" value="14"/>
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>
```

**Java emitter:** `sendPmonStatusReport()`:
```java
int n = pmonList.size();
PusTmPacket pkt = newPacket(14, 1 + n * 3);  // 1B count + N×(2B id + 1B status)
ByteBuffer bb = pkt.getUserDataBuffer();
bb.put((byte) n);
for (PmonDefinition def : pmonList.values()) {
    bb.putShort((short) def.pmonId);
    bb.put((byte) def.status.ordinal());
}
pusSimulator.transmitRealtimeTM(pkt);
```

---

## Section C: Gaps and Shortcomings

---

### Gap 1: Type-deduced field sizes in TC[12,5], TC[12,7], TM[12,9] — Critical XTCE Limitation

**Affects:** TC[12,5], TC[12,7], TM[12,9]
**Severity:** High
**Effort:** High

The `mask`, `low_limit`, `high_limit`, `expected_value`, `low_threshold`, `high_threshold` fields in the check criteria are **"deduced"** — their binary size and encoding depend on the type of the monitored parameter, which is declared at spec/mission time rather than encoded in the packet itself. For example:
- Monitoring a `float32` temperature → 4-byte limit fields
- Monitoring a `uint8` mode register → 1-byte expected-value + mask fields

**XTCE cannot express this polymorphism in a single MetaCommand or SequenceContainer.** XTCE field sizes must be known at definition time, not at packet parse time.

**Workarounds:**
1. **Per-type TC variants (recommended for simulator):** `ADD_PMON_LIMIT_F32`, `ADD_PMON_LIMIT_U16`, `ADD_PMON_EXPECTED_U8`, etc. Each command has fixed-size criteria fields matching its parameter type. Practical for a simulator with a small, known parameter set.
2. **Raw binary `criteria` argument:** TC[12,5/7] pass the criteria as an opaque `BinaryArgumentType` blob. YAMCS Web cannot construct it intelligently; scripting via `yamcs-client` required.
3. **Operator convention:** Document that all simulator PMON parameters are `float32`, allowing a single universal `ADD_PMON_LIMIT_F32` command definition with no ambiguity.

**Impact on TM[12,9]:** The response packet mixes criteria of potentially different sizes per entry. XTCE container can only decode the fixed outer fields (count, PMON_ID, param_ID, PMON_status, check_type); criteria bytes remain opaque in YAMCS display.

---

### Gap 2: Full Pus12Service.java needed from scratch

**Affects:** All subtypes
**Severity:** Medium
**Effort:** Medium

No existing `Pus12Service.java` exists. The service requires:

```java
// Key data structures
Map<Integer, PmonDefinition> pmonList = new LinkedHashMap<>();
boolean pmonFunctionEnabled = true;
List<CheckTransition> checkTransitionList = new ArrayList<>();

// PmonDefinition inner class fields:
int pmonId, monitoredParamId, checkType;
float lowLimit, highLimit;          // for limit-check
float expectedValue; int mask;      // for expected-value-check
double lowThreshold, highThreshold; int numConsecutiveDeltas; // for delta-check
int repetitionNumber, repetitionCounter;
int monitoringIntervalMs;
PmonStatus status = DISABLED;
PmonCheckingStatus checkingStatus = UNCHECKED;
int lowEventId, highEventId;        // event IDs to raise on violation
```

Periodic task runs at the minimum monitoring interval; for each enabled definition: samples the simulator's parameter value, evaluates the check, updates `checkingStatus`, fires events via `pus5Service` when transitions occur. Parameter values must be read from the simulator's live telemetry (e.g., `pusSimulator.flightPacket.temperature`).

Registration required in:
- `PusSimulator.java` constructor: `pus12Service = new Pus12Service(this);`
- `PusSimulator.doStart()`: `pus12Service.start();`
- `PusSimulator.executePendingCommands()`: `case 12 → pus12Service.executeTc(commandPacket);`

---

### Gap 3: PMON ID and parameter ID enumerations are mission-specific

**Affects:** All TC/TM using PMON_ID or monitored_param_ID
**Severity:** Medium

PMON IDs and the enumeration of monitorable parameter IDs are not defined by the PUS standard — they are mission-specific. For the simulator, the XTCE enumerations must be hand-defined to match the simulator's live telemetry parameters (temperature, voltage, mode flags, etc.).

When ground operators send TC[12,5] to add a new PMON definition for a parameter not in the initial enum, the YAMCS Web interface will show a raw integer. This is the same limitation seen with PUS 11 subschedule IDs. Production deployments require a mission database tool to maintain the enumeration.

---

### Gap 4: Check validity condition (conditional monitoring) not implemented in basic version

**Affects:** TC[12,5], TC[12,7], TM[12,9]
**Severity:** Low (optional feature)

The check validity condition (`validity_param_id + mask + expected_value` — present when the optional flag is set) allows disabling a PMON check when associated equipment is inactive. This adds additional `deduced`-size fields to the packet. For the initial simulator implementation, this should be omitted (all checks run unconditionally). The XTCE variant commands should not include these optional fields in the initial version.

---

### Gap 5: Integration with PUS 5 event system

**Affects:** Periodic monitoring logic in Pus12Service
**Severity:** Medium

PUS 12 violations must raise PUS 5 events. The `Pus12Service` must call `pusSimulator.pus5Service.raiseEvent(eventId)`. The event IDs encoded in PMON definitions (e.g., `low_event_id`, `high_event_id`) must exist in the PUS 5 event enumeration defined in `pus5.xml` and handled by `Pus5Service.java`. A new set of event IDs for monitoring violations (e.g., `EVENT_TEMP_BELOW_LIMIT=10`, `EVENT_TEMP_ABOVE_LIMIT=11`) needs to be added to `pus5.xml` and the simulator.

---

### Gap 6: Optional `monitoring_interval` field parsing complexity

**Affects:** TC[12,5]
**Severity:** Low

The `monitoring_interval` field in TC[12,5] is marked optional in the spec. Whether it is present depends on a subservice capability declaration (single global interval vs. per-definition interval). XTCE cannot express conditional presence without a preceding presence-flag byte. For simplicity, the initial implementation should always require monitoring_interval to be present, using a fixed global interval if the field is omitted — or require it always (simpler Java parsing, slightly non-spec-conformant).

---

## Summary Table

| Subtype | Type | XTCE Coverage | Java Required | Existing Code | Effort |
|---------|------|--------------|---------------|---------------|--------|
| TC[12,1] | TC | ✅ Full | ✅ New Pus12Service | ❌ None | Low |
| TC[12,2] | TC | ✅ Full | ✅ New Pus12Service | ❌ None | Low |
| TC[12,5] | TC | ⚠️ Per-type variants | ✅ Required (parsing) | ❌ None | High |
| TC[12,6] | TC | ✅ Full | ✅ New Pus12Service | ❌ None | Low |
| TC[12,7] | TC | ⚠️ Per-type variants | ✅ Required (parsing) | ❌ None | High |
| TC[12,8] | TC | ✅ Full | ✅ New Pus12Service | ❌ None | Low |
| TM[12,9] | TM | ⚠️ Partial (header only) | ✅ Required (emit) | ❌ None | Medium |
| TC[12,13] | TC | ✅ Full (no args) | ✅ New Pus12Service | ❌ None | Low |
| TM[12,14] | TM | ✅ Full | ✅ New Pus12Service | ❌ None | Low |

**Key finding:** TC[12,1], TC[12,2], TC[12,6], TC[12,8], TC[12,13], TM[12,14] are fully expressible in XTCE. TC[12,5], TC[12,7], TM[12,9] require per-type XTCE variants or raw binary workarounds due to type-deduced field sizes — the primary limitation for PUS 12 XTCE coverage.
