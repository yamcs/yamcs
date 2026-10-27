# PUS Service 12 — On-board Monitoring

## Section A: General Context

### Purpose

ST[12] On-board Monitoring provides the capability to monitor on-board parameters against ground-defined conditions and raise events (via ST[05]) when violations occur. The satellite evaluates monitoring rules autonomously — no ground contact required during monitoring.

---

### Ground vs. On-board Responsibility (MCS is ground segment only)

| Responsibility | Where |
|---|---|
| Send PMON configuration TCs (add, delete, enable, disable, modify) | **Ground (YAMCS MCS)** — XTCE encodes TC packets |
| Receive and display TM reports (TM[12,9], TM[12,12], TM[12,14]) | **Ground (YAMCS MCS)** — XTCE decodes TM packets |
| Maintain the PMON list at runtime | **On-board (satellite)** |
| Run the periodic parameter evaluation loop | **On-board (satellite)** |
| Raise PUS-5 events on monitoring violations | **On-board (satellite)** |
| Maintain the check transition list | **On-board (satellite)** |

**YAMCS/MCS implementation = XTCE only (`pus12.xml`). No Java changes to `yamcs-core` are needed for ST[12].**

The `Pus12Service.java` described in this document lives in the **simulator package** and emulates the satellite's on-board monitoring behavior for ground testing. It is not part of the MCS.

---

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

Annotated by where each step executes:

```
[GROUND → SAT]  TC[12,5] uplinked by YAMCS
[ON-BOARD]      TC arrives → ACK[1,1] → executeTc() → parse + add to pmonList → ACK[1,7]
[ON-BOARD]      Periodic task → for each enabled PMON def → sample param → evaluate check
                              → if N consecutive matches → establish new checking status
                              → if status changed → add to check transition list
                              → if event_id != 0 → raise PUS-5 event
[ON-BOARD]      TC[12,13] arrives → collect all PMON statuses → emit TM[12,14]
[SAT → GROUND]  TM[12,14] downlinked, decoded by YAMCS via XTCE
[ON-BOARD]      TC[12,8] arrives → collect requested PMON definitions → emit TM[12,9]
[SAT → GROUND]  TM[12,9] downlinked, decoded by YAMCS via XTCE (criteria typed per check_type)
[ON-BOARD]      TC[12,11] arrives → flush check transition list → emit TM[12,12]
[SAT → GROUND]  TM[12,12] downlinked, decoded by YAMCS via XTCE
```

YAMCS (MCS) is involved only at the TC uplink and TM downlink boundaries. All runtime monitoring state is on-board.

---

### Architecture Files (to be created)

| Layer | Purpose | Path |
|---|---|---|
| **Simulator (on-board emulation)** | Java service emulating satellite monitoring logic | `simulator/src/main/java/org/yamcs/simulator/pus/Pus12Service.java` |
| **Simulator (on-board emulation)** | Register service | `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java` |
| **MCS / YAMCS ground** | XTCE MDB (TC encoding + TM decoding) | `examples/pus/src/main/yamcs/mdb/pus12.xml` |
| **MCS / YAMCS ground** | Add MDB reference | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` |

No changes to `yamcs-core` are required. ST[12] support in YAMCS is purely declarative (XTCE).

---

### Critical XTCE Limitation — Type-Deduced Field Sizes

The most significant challenge for PUS 12 in XTCE is that the packet fields for check criteria (`mask`, `low_limit`, `high_limit`, `expected_value`, `low_threshold`, `high_threshold`) have **sizes and formats deduced from the monitored parameter's type** at definition time. The spec says these are "deduced" — meaning a float32 parameter uses 4-byte limits, a uint8 parameter uses 1-byte limits, etc.

XTCE cannot express arbitrary type polymorphism (an unbounded set of parameter types) within a single `MetaCommand` or `SequenceContainer`. On the **TC side** (TC[12,5], TC[12,7]) this mission still uses per-check-type command variants (Option A below) — collapsing those into one command isn't worth it (see per-subtype section).

On the **TM side** (TM[12,9]), XTCE *can* fully resolve this for a finite, mission-known set of check types: `IncludeCondition` (`ParameterRefEntry`/`ArgumentRefEntry`) lets an entry be conditionally present based on a comparison against an **already-decoded sibling field in the same entry** — in this case `check_type`, decoded earlier in the same repeating entry. Combined with `ContainerRefEntry` + `RepeatEntry` (a dynamic repeat count referencing `pmon_def_count`, the same pattern already used for `pus3-sc-set-entry` in `pus3.xml` and `DETAIL_REPORT_ELEMENT`/`SUMMARY_REPORT_ELEMENT` in `pus11.xml`), each TM[12,9] entry is now decoded via a nested `PMON_ENTRY` container whose criteria fields are typed and mutually-exclusive per `check_type` — no opaque binary blob, no zero-padding. See TM[12,9] below and Gap 1.

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

**XTCE:** ✅ **Fully decoded — discriminated union via `IncludeCondition`**

Each entry is decoded via a nested `PMON_ENTRY` container (`ContainerRefEntry` + `RepeatEntry`, dynamic count = `pmon_def_count` — the same pattern as `pus3-sc-set-entry` in `pus3.xml` / `DETAIL_REPORT_ELEMENT` in `pus11.xml`). The fixed header fields decode first; the criteria fields are typed, mutually-exclusive members gated by `IncludeCondition` comparing the entry's own `check_type` (Table 8-6), so YAMCS displays real `low_limit`/`mask`/`expected_value`/thresholds per entry instead of opaque bytes:

```xml
<SequenceContainer name="PMON_ENTRY"> <!-- no BaseContainer: only included via ContainerRefEntry -->
  <EntryList>
    <ParameterRefEntry parameterRef="pmondef_pmon_id"/>
    <ParameterRefEntry parameterRef="pmondef_param_id"/>
    <ParameterRefEntry parameterRef="pmondef_status"/>
    <ParameterRefEntry parameterRef="pmondef_repetition_number"/>
    <ParameterRefEntry parameterRef="pmondef_check_type"/>
    <ParameterRefEntry parameterRef="pmondef_mask">
      <IncludeCondition><Comparison parameterRef="pmondef_check_type" value="expected-value-checking"/></IncludeCondition>
    </ParameterRefEntry>
    <!-- pmondef_expected_value, pmondef_expected_event_id: same condition -->
    <ParameterRefEntry parameterRef="pmondef_low_limit">
      <IncludeCondition><Comparison parameterRef="pmondef_check_type" value="limit-checking"/></IncludeCondition>
    </ParameterRefEntry>
    <!-- pmondef_low_limit_event_id, pmondef_high_limit, pmondef_high_limit_event_id: same condition -->
    <ParameterRefEntry parameterRef="pmondef_low_threshold">
      <IncludeCondition><Comparison parameterRef="pmondef_check_type" value="delta-checking"/></IncludeCondition>
    </ParameterRefEntry>
    <!-- pmondef_low_threshold_event_id, pmondef_high_threshold, pmondef_high_threshold_event_id,
         pmondef_num_consecutive_deltas: same condition -->
  </EntryList>
</SequenceContainer>

<SequenceContainer name="PMON_DEFINITION_REPORT">
  <EntryList>
    <ParameterRefEntry parameterRef="pmon_def_count"/>
    <ContainerRefEntry containerRef="PMON_ENTRY">
      <RepeatEntry>
        <Count><DynamicValue><ParameterInstanceRef parameterRef="pmon_def_count"/></DynamicValue></Count>
      </RepeatEntry>
    </ContainerRefEntry>
  </EntryList>
  <BaseContainer containerRef="pus12-tm">
    <RestrictionCriteria>
      <Comparison parameterRef="/PUS/subtype" value="9"/>
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>
```

**Java emitter:** `sendPmonDefinitionReport(List<Integer> pmonIds)` writes each entry with exactly the fields the XTCE side expects for that `check_type` — no padding:
```
Packet layout per entry:
  uint16 pmon_id
  uint16 param_id
  uint8  pmon_status
  uint8  repetition_number
  uint8  check_type
  [criteria bytes based on check_type, sized by Pus12Service.criteriaSize()]

Total size: 1 + sum over entries of (7 + criteria_size)
criteria_size = 4 for expected-value (mask+expected_value+event_id),
                12 for limit (low_limit+low_event_id+high_limit+high_event_id),
                13 for delta (same as limit + num_consecutive_deltas)
```

---

### TC[12,11] — Report Parameter Monitoring Check Transition (missing from original doc)

**Spec:** §6.12.3.12

**Packet structure:** No arguments — application data field omitted.

**Action:** Emit TM[12,12] containing all accumulated check transitions since the last flush; then clear the check transition list.

**XTCE:** ✅ **XTCE-only** — no-argument command, same pattern as TC[12,13].

**Java (simulator):** `case 11 → sendCheckTransitionReport()` — iterates `checkTransitionList`, emits TM[12,12], clears the list.

**Note:** The check transition list accumulates on-board; YAMCS only sees the outgoing TM[12,12]. There is no MCS-side state for this.

---

### TM[12,12] — Parameter Monitoring Transition Report

**Spec:** §6.12.3.12

**Packet structure:**

```
| N (uint) | repeated N times:
    | PMON_ID (enumerated) | prev_checking_status (enumerated) | new_checking_status (enumerated) |
```

**XTCE:** ✅ **XTCE-only** — same aggregate+array pattern as TM[12,14], using the check status enumerations from the tables in Section A.

**Java emitter (simulator):** `sendCheckTransitionReport()`:
```java
PusTmPacket pkt = newPacket(12, 1 + checkTransitionList.size() * 5);
ByteBuffer bb = pkt.getUserDataBuffer();
bb.put((byte) checkTransitionList.size());
for (CheckTransition t : checkTransitionList) {
    bb.putShort((short) t.pmonId);
    bb.put((byte) t.prevStatus.ordinal());
    bb.put((byte) t.newStatus.ordinal());
}
checkTransitionList.clear();
pusSimulator.transmitRealtimeTM(pkt);
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

### Gap 1: Type-deduced field sizes in TC[12,5], TC[12,7], TM[12,9] — Critical XTCE Limitation (TM side resolved)

**Affects:** TC[12,5], TC[12,7] (still workaround); TM[12,9] (resolved natively)
**Severity:** Medium (was High)
**Effort:** High (already spent — TM[12,9] fix implemented)

The `mask`, `low_limit`, `high_limit`, `expected_value`, `low_threshold`, `high_threshold` fields in the check criteria are **"deduced"** — their binary size and encoding depend on the type of the monitored parameter, which is declared at spec/mission time rather than encoded in the packet itself. For example:
- Monitoring a `float32` temperature → 4-byte limit fields
- Monitoring a `uint8` mode register → 1-byte expected-value + mask fields

XTCE cannot size a field directly from an arbitrary referenced parameter's declared type. It **can**, however, express a bounded discriminated union: `IncludeCondition` (on `ArgumentRefEntry`/`ParameterRefEntry`) conditionally includes a fully-typed, fixed-size field based on a comparison against an already-decoded sibling field — here, `check_type` (Table 8-6), which is finite (3 values) and always decoded immediately before the criteria in the same entry.

**TC[12,5]/TC[12,7] (still workaround):** kept as 3 per-check-type MetaCommand variants (`ADD_PMON_LIMIT_F32`, `ADD_PMON_EXPECTED_U8`, `ADD_PMON_DELTA_F32`, and their `MODIFY_*` counterparts). `IncludeCondition` on `ArgumentRefEntry` could in principle collapse these into one command gated on a `check_type` argument, but YAMCS Web doesn't visually hide irrelevant arguments based on another argument's value, so operators would still have to know which fields to fill in — three self-documenting typed commands are the better UX here, not a real limitation of XTCE.

**TM[12,9] (resolved):** `IncludeCondition` and `ContainerRefEntry`/`RepeatEntry` together fully decode variable criteria without opaque bytes or padding — see the TM[12,9] section above. This is the same nested-container pattern already used for `pus3-sc-set-entry` (`pus3.xml`) and `DETAIL_REPORT_ELEMENT`/`SUMMARY_REPORT_ELEMENT` (`pus11.xml`), just combined with per-sibling-field `IncludeCondition` for the first time in this mission's MDB.

---

### Gap 2: Full Pus12Service.java needed from scratch (simulator / on-board emulation only)

**Affects:** All subtypes
**Severity:** Medium
**Effort:** Medium

No existing `Pus12Service.java` exists. This service lives in the **simulator package** and emulates the satellite's on-board monitoring — it is **not** an MCS component and requires no changes to `yamcs-core`. The service requires:

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

### Gap 5: Integration with PUS 5 event system (simulator-side only)

**Affects:** Periodic monitoring logic in Pus12Service (simulator / on-board emulation)
**Severity:** Medium

On-board monitoring violations raise PUS-5 events; YAMCS/MCS receives the resulting TM[5,x] downlink and displays them — no MCS-side action is needed for this integration. In the simulator, `Pus12Service` must call `pusSimulator.pus5Service.raiseEvent(eventId)` when a check transition fires an event. The event IDs encoded in PMON definitions (e.g., `low_event_id`, `high_event_id`) must exist in the PUS 5 event enumeration in `pus5.xml`. A new set of event IDs for monitoring violations (e.g., `EVENT_TEMP_BELOW_LIMIT=10`, `EVENT_TEMP_ABOVE_LIMIT=11`) needs to be added to `pus5.xml` and the simulator event handler.

---

### Gap 6: Optional `monitoring_interval` field parsing complexity

**Affects:** TC[12,5]
**Severity:** Low

The `monitoring_interval` field in TC[12,5] is marked optional in the spec. Whether it is present depends on a subservice capability declaration (single global interval vs. per-definition interval). XTCE cannot express conditional presence without a preceding presence-flag byte. For simplicity, the initial implementation should always require monitoring_interval to be present, using a fixed global interval if the field is omitted — or require it always (simpler Java parsing, slightly non-spec-conformant).

---

## Summary Table

"Java (simulator)" = on-board emulation in `simulator/`; "XTCE" = MCS ground configuration in `pus12.xml`. No `yamcs-core` Java changes are needed for any subtype.

| Subtype | Type | XTCE Coverage (MCS) | Java (simulator) | Existing Code | Effort |
|---------|------|---------------------|------------------|---------------|--------|
| TC[12,1] | TC | ✅ Full | ✅ New Pus12Service | ❌ None | Low |
| TC[12,2] | TC | ✅ Full | ✅ New Pus12Service | ❌ None | Low |
| TC[12,5] | TC | ⚠️ Per-type variants | ✅ Required (parsing) | ❌ None | High |
| TC[12,6] | TC | ✅ Full | ✅ New Pus12Service | ❌ None | Low |
| TC[12,7] | TC | ⚠️ Per-type variants | ✅ Required (parsing) | ❌ None | High |
| TC[12,8] | TC | ✅ Full | ✅ New Pus12Service | ❌ None | Low |
| TM[12,9] | TM | ✅ Full (discriminated union via `IncludeCondition`) | ✅ Required (emit) | ❌ None | Medium |
| TC[12,11] | TC | ✅ Full (no args) | ✅ New Pus12Service | ❌ None | Low |
| TM[12,12] | TM | ✅ Full | ✅ Required (emit) | ❌ None | Low |
| TC[12,13] | TC | ✅ Full (no args) | ✅ New Pus12Service | ❌ None | Low |
| TM[12,14] | TM | ✅ Full | ✅ Required (emit) | ❌ None | Low |

**Key finding:** TC[12,1], TC[12,2], TC[12,6], TC[12,8], TC[12,11], TC[12,13], TM[12,9], TM[12,12], TM[12,14] are fully expressible in XTCE. Only TC[12,5]/TC[12,7] still use per-type command variants due to type-deduced field sizes — a UX choice, not a hard XTCE limitation (see Gap 1: `IncludeCondition` + `ContainerRefEntry`/`RepeatEntry` fully resolved the equivalent problem on the TM[12,9] decode side). All on-board monitoring logic (evaluation loop, state machine, event raising) lives in the simulator, not in YAMCS.

---

## Section D: Testing Methodology

Reflects the actual implementation: `Pus12Service.java`, `examples/pus/src/main/yamcs/mdb/pus12.xml`,
and the `pus5.xml`/`events.json` PMON-event additions. Command paths, argument names, enum labels,
and byte layouts below are taken directly from those files, not the pseudocode in Section B.

### D.1 Start the instance

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests   # first build only
mvn -pl examples/pus yamcs:run
```
Web UI: `http://localhost:8090`, instance `pus`. Commands live under `/PUS12/...`, TM containers
under the same `/PUS12/` subsystem (see D.3).

### D.2 Seed PMON definitions (pre-registered, enabled, no TC required)

| pmon_id | param_id | check_type | thresholds | rep# | interval | events |
|---|---|---|---|---|---|---|
| 1 `PMON_SINE_TEMP_LIMIT` | 1 `PARAM_SINE_TEMP` | limit-checking | low=-10.0, high=50.0 | 3 | 1000ms | low=`EVENT_SINE_TEMP_LOW`(10), high=`EVENT_SINE_TEMP_HIGH`(11) |
| 2 `PMON_RANDWALK_LIMIT` | 2 `PARAM_BUS_CURRENT` | limit-checking | low=1.0, high=8.0 | 2 | 500ms | low=`EVENT_RANDWALK_LOW`(12), high=`EVENT_RANDWALK_HIGH`(13) |
| 3 `PMON_BUSVOLT_DELTA` | 3 `PARAM_BUS_VOLTAGE` | delta-checking | low=0.05 (no event), high=1.0 | 3 | 1000ms | high=`EVENT_BUSVOLT_DELTA_HIGH`(14) |
| 4 `PMON_MODE_EXPECTED` | 4 `PARAM_MODE_REGISTER` | expected-value-checking | mask=0xFF, expected=1 | 2 | 1000ms | `EVENT_MODE_UNEXPECTED`(15) |

`pmon_id_type`/`monitored_param_id_type` in `pus12.xml` only enumerate these 4 IDs each — ground
can only reference these when adding/deleting/reporting; there is no free slot to add a genuinely
new 5th definition without first deleting one of the seeds (see D.4 Test 5 for that workflow).

### D.3 Command reference — valid inputs

All commands are under `/PUS12/`. `N`/`pmon_ids` arguments take **enum label strings** (e.g.
`"PMON_SINE_TEMP_LIMIT"`), matching how `test-pus5.py` passes `"EVENT_1"` rather than `1`.

| Command | Subtype | Valid example args |
|---|---|---|
| `ENABLE_PARAMETER_MONITORING` | TC[12,1] | `{"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]}` |
| `DISABLE_PARAMETER_MONITORING` | TC[12,2] | `{"N": 2, "pmon_ids": ["PMON_SINE_TEMP_LIMIT", "PMON_RANDWALK_LIMIT"]}` |
| `ADD_PMON_LIMIT_F32` | TC[12,5] | `{"pmon_id": "PMON_SINE_TEMP_LIMIT", "param_id": "PARAM_SINE_TEMP", "monitoring_interval_ms": 1000, "repetition_number": 3, "low_limit": -10.0, "low_event_id": 10, "high_limit": 50.0, "high_event_id": 11}` |
| `ADD_PMON_EXPECTED_U8` | TC[12,5] | `{"pmon_id": "PMON_MODE_EXPECTED", "param_id": "PARAM_MODE_REGISTER", "monitoring_interval_ms": 1000, "repetition_number": 2, "mask": 255, "expected_value": 1, "event_id": 15}` |
| `ADD_PMON_DELTA_F32` | TC[12,5] | `{"pmon_id": "PMON_BUSVOLT_DELTA", "param_id": "PARAM_BUS_VOLTAGE", "monitoring_interval_ms": 1000, "repetition_number": 3, "low_threshold": 0.05, "low_event_id": 0, "high_threshold": 1.0, "high_event_id": 14, "num_consecutive_deltas": 3}` |
| `DELETE_PMON_DEFINITIONS` | TC[12,6] | `{"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]}` — target must be **disabled** first |
| `MODIFY_PMON_LIMIT_F32` | TC[12,7] | Same shape as `ADD_PMON_LIMIT_F32` minus `monitoring_interval_ms`; `pmon_id`/`param_id`/check_type must match the existing definition exactly |
| `MODIFY_PMON_EXPECTED_U8` | TC[12,7] | Same shape as `ADD_PMON_EXPECTED_U8` minus `monitoring_interval_ms` |
| `MODIFY_PMON_DELTA_F32` | TC[12,7] | Same shape as `ADD_PMON_DELTA_F32` minus `monitoring_interval_ms` |
| `REPORT_PMON_DEFINITIONS` | TC[12,8] | `{"N": 0, "pmon_ids": []}` → all; or `{"N": 1, "pmon_ids": ["PMON_MODE_EXPECTED"]}` → one |
| `REPORT_PMON_CHECK_TRANSITIONS` | TC[12,11] | `{}` (no arguments) |
| `REPORT_PMON_STATUS` | TC[12,13] | `{}` (no arguments) |

Rejection conditions to exercise (all respond NACK completion, not NACK start — the command is
accepted then rejected during execution): re-adding an existing `pmon_id` (`ALREADY_EXISTS`),
deleting/modifying an unknown `pmon_id` (`PMON_ID_UNKNOWN`), deleting a still-`enabled` definition
(`PMON_STILL_ENABLED`), modifying with the wrong check-type variant (`CHECK_TYPE_MISMATCH`), or
modifying with a `param_id` that doesn't match the stored definition (`PARAM_ID_MISMATCH`).

### D.4 TMs to check

| Container | Subtype | Triggered by | Layout (after the 21-byte common PUS TM header) |
|---|---|---|---|
| `/PUS12/PMON_DEFINITION_REPORT` | TM[12,9] | TC[12,8] | `count:u8` + count × variable-length entries (see D.5): `pmon_id:u16, param_id:u16, pmon_status:u8, repetition_number:u8, check_type:u8`, then criteria fields gated by `check_type` — no padding, fully decoded per-entry by XTCE (`PMON_ENTRY` container) |
| `/PUS12/PMON_TRANSITION_REPORT` | TM[12,12] | TC[12,11] | `count:u8` + count × 4B entries: `pmon_id:u16, prev_status:u8, new_status:u8` (flushes and clears `checkTransitionList`) |
| `/PUS12/PMON_STATUS_REPORT` | TM[12,14] | TC[12,13] | `count:u8` + count × 3B entries: `pmon_id:u16, pmon_status:u8` |
| `/PUS5/event_sine_temp_low`, `event_sine_temp_high`, `event_randwalk_low`, `event_randwalk_high`, `event_busvolt_delta_high`, `event_mode_unexpected` | TM[5,2] (subtype always hardcoded to 2 by `Pus12Service.maybeRaiseEvent`) | An enabled PMON transitioning into LOW/HIGH (never on recovery) | `event_id:u8` (10-15) + `pmon_id:u16` + `value:f32` (or `value:u8` for `event_mode_unexpected`) |

`pmon_status`: `0=disabled, 1=enabled`. `check_type`: `0=expected-value-checking, 1=limit-checking, 2=delta-checking`.
`prev_status`/`new_status` (generic across all 3 check types, see `PmonCheckingStatus`): `0=good, 1=unchecked, 2=invalid, 3=low_or_unexpected, 4=high`.

Criteria decode by `check_type` (all big-endian, no padding — entries are genuinely variable-length):
- limit-checking (12B): `low_limit:f32, low_event_id:u16, high_limit:f32, high_event_id:u16`
- delta-checking (13B): `low_threshold:f32, low_event_id:u16, high_threshold:f32, high_event_id:u16, num_consecutive_deltas:u8`
- expected-value-checking (4B): `mask:u8, expected_value:u8, event_id:u16`

### D.5 Automated integration test

`examples/pus/tests/test-pus12.py` (same `yamcs.client.YamcsClient` pattern as `test-pus5.py`/`test-pus11.py`):

```bash
python3 examples/pus/tests/test-pus12.py
```

Covers, in order: initial 4-seed state via TM[12,14]; enable/disable round-trip; TC[12,8] N=0 full
report via TM[12,9]; delete-while-enabled rejection; disable→delete→re-add round-trip (the only way
to exercise a successful TC[12,5] given the fixed 4-entry `pmon_id_type` enum); duplicate-add
rejection; modify check-type-mismatch and param-id-mismatch rejections; a valid modify verified by
re-reading TM[12,9] criteria bytes; and a structural check of TM[12,12]. It restores the original
seed state at the end, so it is safe to re-run against the same live instance.

It deliberately does **not** assert which checking-status transitions fire from the synthetic
signals — that depends on wall-clock timing (see D.6) — only on the deterministic command/response
state machine.

### D.6 Manual verification of the timing-based monitoring loop

The four signal generators run in `Pus12Service.advanceSignalGenerators()` on a 100ms master tick,
independent of the CLI/UI. With the seed defaults, on a freshly started instance:

- **PMON 1** (sine wave, ±35 around 20°C over a 24s period, low=-10/high=50, rep#=3, 1000ms interval):
  expect a `HIGH` transition within the first ~10-12s (sine peak ≈ 55 > 50), a `GOOD` recovery a few
  seconds later, and a `LOW` transition around the trough (sine trough ≈ -15 < -10) roughly every
  24s cycle thereafter.
- **PMON 2** (mean-reverting random walk around 4.5, low=1.0/high=8.0, rep#=2, 500ms interval):
  stochastic — expect occasional `LOW`/`HIGH` excursions within the first ~30-60s, not on a fixed
  schedule.
- **PMON 3** (flat 28.0V with a scripted spike to 31.0V every 15s held for 1.5s, low=0.05 [no event]/
  high=1.0, rep#=3, 1000ms interval): expect a `HIGH` transition roughly every 15s window (the
  averaged delta across 3 consecutive 1s samples crosses the spike).
- **PMON 4** (mode register cycling NOMINAL(1)×8 ticks / SAFE(2)×2 ticks, expected=1, rep#=2, 1000ms
  interval): expect a `LOW` ("unexpected value") transition roughly every 10 ticks of its own
  sampling cadence, recovering to `GOOD` shortly after.

To observe: subscribe to `/PUS12/PMON_TRANSITION_REPORT` (send `REPORT_PMON_CHECK_TRANSITIONS`
periodically, or poll it) and to the YAMCS Events view for `EVENT_SINE_TEMP_LOW`/`HIGH`,
`EVENT_RANDWALK_LOW`/`HIGH`, `EVENT_BUSVOLT_DELTA_HIGH`, `EVENT_MODE_UNEXPECTED` (ids 10-15) —
each should have a readable rendered message from `events.json`. Recovery-to-`GOOD` transitions are
visible in TM[12,12] but do **not** raise an event — the spec's `low_event_id`/`high_event_id`/
`event_id` fields (Fig 8-116/8-117) have no dedicated "recovered" counterpart, so `Pus12Service`
fires events only on entry into `LOW`/`HIGH`.
