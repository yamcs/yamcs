# PUS ST[14] Real-Time Forwarding Control — Research & Implementation Guide

**Source**: ECSS-E-ST-70-41C §6.14 (pages 237–256)
**Target env**: yamcs-Pixxel-fork (Java simulator + YAMCS XTCE MDB)

---

## a) General PUS 14 Context

### Purpose

ST[14] **Real-Time Forwarding Control** provides the capability to control which on-board reports (TM packets) are forwarded to the ground via the real-time telemetry channel. The service acts as a gate: it defines per-application-process conditions that authorize or block forwarding of specific report types.

This is fundamentally different from all other PUS services: ST[14] does **not** generate telemetry autonomously. Instead, it maintains an in-memory **forwarding control table** that is consulted every time any other service generates a TM packet before that packet is placed on the downlink.

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Application Process (APID)** | A CCSDS application process, identified by its APID. The ST[14] subservice controls forwarding for one or more application processes. |
| **Application Process Forward-Control Definition (APFCD)** | Per-APID entry in the forwarding table. Contains a list of service-type forward-control definitions. |
| **Service-Type Forward-Control Definition (STFCD)** | Per-service-type entry within an APFCD. Contains a list of report-type forward-control definitions. |
| **Report-Type Forward-Control Definition (RTFCD)** | Per-subtype entry within an STFCD. Contains the message subtype identifier of a report type. |
| **Application Process Forward-Control Configuration (APFCC)** | The complete forwarding table: the set of all APFCDs. |
| **HK Forward-Control Configuration (HK FCC)** | Optional extension: per-APID list of housekeeping parameter report structure identifiers that are allowed. Empty HK FCC = block all HK reports. |
| **Diagnostic FCC** | Same concept as HK FCC but for diagnostic parameter reports (ST[04]). |
| **Event Report Blocking FCC** | Optional: per-APID list of event definition IDs to block from forwarding. |

### Forwarding Logic (§6.14.3.3)

The forwarding decision is a three-tier hierarchy:

```
1. If APID is not in APFCC → BLOCK (no definition = no forwarding)

2. If APID is in APFCC and APFCC contains at least one STFCD, but
   NOT for this report's service type → BLOCK

3. If APID in APFCC, STFCD exists for this service type, STFCD has
   at least one RTFCD, but NOT for this report's subtype → BLOCK

4. Otherwise → ALLOW

Special cases (optional capabilities):
  - If HK FCC capability enabled: block HK reports not in the HK FCC structure ID list
  - If Diag FCC capability enabled: block diagnostic reports not in the Diag FCC
  - If Event Blocking capability enabled: block events whose event_id is in the blocking list
```

**Key invariant**: An empty APFCD (APID entry exists but has no STFCDs) means "forward all reports for this APID". A missing APID entry means "block all".

### Architectural Note for Simulator

In a flight system, ST[14] filtering happens at the downlink level — the flight software decides which packets to send. In the YAMCS Java simulator:
- The simulator (`Pus14Service.java`) must maintain the APFCC in memory
- Every outgoing TM packet from any service must be checked against the APFCC before calling `pusSimulator.transmitRealtimeTM(packet)`
- This requires a **cross-cutting concern**: other services call a `shouldForward(apid, type, subtype)` gate method before transmitting
- Alternatively, the simulator can intercept in `PusSimulator.transmitRealtimeTM()` directly

### Architecture Files (to be created)

| Purpose | Path |
|---------|------|
| Java service | `simulator/src/main/java/org/yamcs/simulator/pus/Pus14Service.java` |
| XTCE MDB | `examples/pus/src/main/yamcs/mdb/pus14.xml` |
| Register in | `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java` |
| Add MDB to | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` |

---

## b) Required TM/TC — Context, Implementation Plan, XTCE vs Java

### Mission-Specific Field Sizes

| Field | Chosen type | Size |
|-------|-------------|------|
| `application_process_id` (APID) | uint11 (fits in uint16) | 2 bytes |
| `service_type_id` | uint8 | 1 byte |
| `message_subtype_id` | uint8 | 1 byte |
| `hk_structure_id` | uint16 | 2 bytes |
| `diagnostic_structure_id` | uint16 | 2 bytes |
| `event_definition_id` | uint16 | 2 bytes |
| Counts (N of items) | uint8 | 1 byte |

---

### TC[14,1] — Add Report Types to the Application Process Forward-Control Configuration

**Spec**: §6.14.3.4.1

**Purpose**: Add forwarding permissions. Each request carries a combination of one or more instructions. Each instruction is one of three forms:

| Instruction Form | Fields | Effect |
|---|---|---|
| Add specific report type | `apid` + `service_type` + `subtype` | Enable forwarding of one specific TM subtype |
| Add all subtypes of a service | `apid` + `service_type` | Enable forwarding of all subtypes of a service |
| Add all services of an APID | `apid` | Enable forwarding of all reports from an application process |

**Packet layout**: `N_instructions` + repeated instruction blocks (mixed types).

**XTCE**: ⚠️ **Not implementable as a single MetaCommand**

XTCE arrays require homogeneous element types with fixed field layout. TC[14,1] mixes three instruction shapes in one packet. Options:

**Option A — Three separate TC variants (recommended)**:
```xml
<!-- TC[14,1]a — Add specific report type (per subtype) -->
<MetaCommand name="TC_14_1_ADD_REPORT_TYPE">
  <ArgumentList>
    <Argument name="N"            argumentTypeRef="/dt/uint8"/>
    <Argument name="apid"         argumentTypeRef="/dt/uint16"/>
    <Argument name="service_type" argumentTypeRef="/dt/uint8"/>
    <Argument name="subtype"      argumentTypeRef="/dt/uint8"/>
  </ArgumentList>
  <!-- BaseMetaCommand subtype=1 -->
</MetaCommand>

<!-- TC[14,1]b — Add all subtypes of a service type -->
<MetaCommand name="TC_14_1_ADD_SERVICE_TYPE">
  <ArgumentList>
    <Argument name="N"            argumentTypeRef="/dt/uint8"/>
    <Argument name="apid"         argumentTypeRef="/dt/uint16"/>
    <Argument name="service_type" argumentTypeRef="/dt/uint8"/>
  </ArgumentList>
</MetaCommand>

<!-- TC[14,1]c — Add all services of an application process -->
<MetaCommand name="TC_14_1_ADD_ALL_APID">
  <ArgumentList>
    <Argument name="N"            argumentTypeRef="/dt/uint8"/>
    <Argument name="apid"         argumentTypeRef="/dt/uint16"/>
  </ArgumentList>
</MetaCommand>
```

Each variant encodes N=1 with its own fixed-layout instruction. Multi-instruction TCs (N>1 mixed) require Java scripting or a raw binary argument.

**Option B — Raw binary instruction list**: One TC with a `BinaryArgumentType` for the instruction payload; Java parses instruction-type byte per entry.

**Java** (`case 1 → addReportTypes(bb)`):
```java
int n = bb.get() & 0xFF;
for (int i = 0; i < n; i++) {
    int apid    = bb.getShort() & 0x7FF;
    // Instruction form is determined by what follows (mission convention):
    // e.g., service_type==0xFF means "all services for this APID"
    int svcType = bb.get() & 0xFF;
    int subtype = bb.get() & 0xFF;  // 0xFF = "all subtypes of this service"
    apfcc.addEntry(apid, svcType, subtype);
}
ack_completion(tc);
```

**Rejection conditions** (per spec):
- Max service type forward-control definitions already reached
- Max report type forward-control definitions already reached
- Instruction contradicts existing state (e.g., "all subtypes" already enabled)
- APID not controlled by this subservice

**Gaps**: Multi-instruction mixed-type TCs require raw binary or scripting. Single-instruction XTCE variants are operator-friendly for 90% of use cases.

---

### TC[14,2] — Delete Report Types from the Application Process Forward-Control Configuration

**Spec**: §6.14.3.4.2

**Purpose**: Remove forwarding permissions. Contains EITHER:
1. One or more delete instructions (delete a report type / delete a service type / delete an APID), OR
2. A single "empty the entire APFCC" instruction (no arguments)

**Instruction forms**:

| Form | Fields |
|---|---|
| Delete specific report type | `apid` + `service_type` + `subtype` |
| Delete a service type | `apid` + `service_type` |
| Delete an application process | `apid` |
| Empty APFCC | (no arguments) |

**XTCE**: ⚠️ **Not implementable as a single MetaCommand** — same polymorphism problem as TC[14,1].

**Four separate TC variants** (recommended):
```xml
<!-- TC[14,2]a — Delete specific report type -->
<MetaCommand name="TC_14_2_DELETE_REPORT_TYPE">
  <!-- apid + service_type + subtype -->
</MetaCommand>

<!-- TC[14,2]b — Delete a service type -->
<MetaCommand name="TC_14_2_DELETE_SERVICE_TYPE">
  <!-- apid + service_type -->
</MetaCommand>

<!-- TC[14,2]c — Delete an application process -->
<MetaCommand name="TC_14_2_DELETE_APID">
  <!-- apid only -->
</MetaCommand>

<!-- TC[14,2]d — Empty the entire APFCC -->
<MetaCommand name="TC_14_2_EMPTY_APFCC">
  <!-- no arguments -->
</MetaCommand>
```

**Java** (`case 2 → deleteReportTypes(bb)`): Parse instruction type indicator (mission-defined byte) and dispatch to `apfcc.removeEntry(...)` / `apfcc.clear()`. Cascade empty: if an STFCD becomes empty → remove it; if an APFCD becomes empty → remove it.

**Rejection conditions**: Referenced APID/service/subtype not in APFCC → NACK[1,4] per-instruction.

**Gaps**: Multi-instruction delete (e.g., delete 5 specific subtypes in one TC) requires raw binary or scripting. Single-instruction variants cover the common case.

---

### TC[14,3] — Report the Content of the Application Process Forward-Control Configuration

**Spec**: §6.14.3.4.3

**Purpose**: Request a dump of the entire APFCC. No application data — zero-argument TC. Response: one TM[14,4] per APFCD in the table.

**Packet layout**: No application data field.

**XTCE**: ✅ **XTCE-only** — no-argument command, identical pattern to TC[11,17], TC[12,13].

```xml
<MetaCommand name="TC_14_3_REPORT_APFCC"
             shortDescription="TC[14,3] Report APFC configuration">
  <BaseMetaCommand metaCommandRef="pus14-tc">
    <ArgumentAssignmentList>
      <ArgumentAssignment argumentName="subtype" argumentValue="3"/>
    </ArgumentAssignmentList>
  </BaseMetaCommand>
  <CommandContainer name="TC_14_3">
    <EntryList/>
    <BaseContainer containerRef="pus14-tc"/>
  </CommandContainer>
</MetaCommand>
```

**Java** (`case 3 → reportApfcc(tc)`): Iterate `apfcc.entries()`; for each APFCD build and send one TM[14,4].

**Gaps**: None. Standard no-argument TC.

---

### TM[14,4] — Application Process Forward-Control Configuration Content Report

**Spec**: §6.14.3.4.3

**Purpose**: One packet per APFCD (application process). Contains the full hierarchy of service types and subtypes allowed for forwarding.

**Packet structure (per packet)**:
```
[apid: uint16]
[N_service_types: uint8]
  repeated N_service_types times:
    [service_type_id: uint8]
    [N_subtypes: uint8]
      repeated N_subtypes times:
        [subtype_id: uint8]
```

**XTCE**: ⚠️ **Outer level only** — The top-level `apid` and `N_service_types` can be decoded with a SequenceContainer. The inner dynamic array (service types, each containing its own inner array of subtypes) is a **nested variable-length structure** that XTCE cannot express in a single container. XTCE dynamic arrays require their count to be a top-level parameter; here each service type's subtype count is embedded inside the service type element.

```xml
<SequenceContainer name="TM_14_4" shortDescription="TM[14,4] APFC config content report">
  <EntryList>
    <ParameterRefEntry parameterRef="apfc_apid"/>
    <ParameterRefEntry parameterRef="apfc_n_services"/>
    <!-- inner nesting not expressible in XTCE; remaining bytes are opaque -->
  </EntryList>
  <BaseContainer containerRef="pus14-tm">
    <RestrictionCriteria>
      <Comparison parameterRef="/PUS/subtype" value="4"/>
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>
```

**Java emitter** (`sendApfcReport(ApfcDefinition apfcd)`):
```java
// Compute total size: 2 (apid) + 1 (N_svc) + sum_per_svc(1 svc_id + 1 N_sub + N_sub * 1)
int size = 3;
for (StfcDefinition stfc : apfcd.services()) {
    size += 2 + stfc.subtypes().size();
}
PusTmPacket pkt = newPacket(4, size);
ByteBuffer bb = pkt.getUserDataBuffer();
bb.putShort((short) apfcd.apid());
bb.put((byte) apfcd.services().size());
for (StfcDefinition stfc : apfcd.services()) {
    bb.put((byte) stfc.serviceType());
    bb.put((byte) stfc.subtypes().size());
    for (int subtype : stfc.subtypes()) {
        bb.put((byte) subtype);
    }
}
pusSimulator.transmitRealtimeTM(pkt);
```

**Gaps**:
- XTCE decoding limited to outer fields only; YAMCS Web shows inner structure as raw bytes
- One TM packet per APFCD; if table is large, may generate many packets

---

### TC[14,5] — Add Structure Identifiers to the HK Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.5.1

**Purpose**: Authorize specific housekeeping parameter report structures for forwarding. Contains EITHER:
1. One or more instructions: each = `apid` + `hk_structure_id` [+ `subsampling_rate` if subsampling supported]
2. An "add all" instruction: just `apid` (enables all HK structures for that APID)

**XTCE**: ⚠️ **Two separate TC variants** (one for specific structs, one for add-all):

```xml
<!-- TC[14,5]a — Add specific HK structure identifiers -->
<MetaCommand name="TC_14_5_ADD_HK_STRUCTS">
  <ArgumentList>
    <Argument name="N"              argumentTypeRef="/dt/uint8"/>
    <Argument name="apid"           argumentTypeRef="/dt/uint16"/>
    <Argument name="hk_struct_ids"  argumentTypeRef="hk_struct_id_array_type"/>
  </ArgumentList>
  <!-- Dynamic array of uint16 struct IDs, size = N -->
</MetaCommand>

<!-- TC[14,5]b — Add all HK structure identifiers for an APID -->
<MetaCommand name="TC_14_5_ADD_ALL_HK">
  <ArgumentList>
    <Argument name="apid" argumentTypeRef="/dt/uint16"/>
  </ArgumentList>
</MetaCommand>
```

**Java** (`case 5 → addHkStructIds(bb)`):
- Reads APID + N structure IDs (or "all" indicator)
- If "all": clear any existing blocked-struct list, set mode to "pass-all" for this APID
- Else: add each struct ID to the HK FCC for the specified APID

**Rejection conditions**: APID not controlled by subservice; max struct IDs reached; HK FCC has no struct defined yet (trying to add when mode is "no struct → block all").

**Gaps**: Subsampling rate is optional per spec (§6.14.3.2.1d). For initial simulator implementation, omit subsampling — all authorized structures are forwarded at their native rate. This is a valid simplification (subsampling is a declared capability, not mandatory).

---

### TC[14,6] — Delete Structure Identifiers from the HK Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.5.2

**Purpose**: Revoke specific HK structure forwarding permissions. Contains EITHER:
1. One or more delete-struct instructions: `apid` + `hk_structure_id`; OR delete-APID instruction: `apid` only
2. An "empty HK FCC" instruction (no arguments)

**XTCE**: ⚠️ **Three separate TC variants**:

```xml
<!-- TC[14,6]a — Delete specific HK structure identifier -->
<MetaCommand name="TC_14_6_DELETE_HK_STRUCT">
  <ArgumentList>
    <Argument name="N"             argumentTypeRef="/dt/uint8"/>
    <Argument name="apid"          argumentTypeRef="/dt/uint16"/>
    <Argument name="hk_struct_ids" argumentTypeRef="hk_struct_id_array_type"/>
  </ArgumentList>
</MetaCommand>

<!-- TC[14,6]b — Delete an application process from the HK FCC -->
<MetaCommand name="TC_14_6_DELETE_HK_APID">
  <ArgumentList>
    <Argument name="apid" argumentTypeRef="/dt/uint16"/>
  </ArgumentList>
</MetaCommand>

<!-- TC[14,6]c — Empty the HK FCC entirely -->
<MetaCommand name="TC_14_6_EMPTY_HK_FCC">
  <!-- no arguments -->
</MetaCommand>
```

**Java** (`case 6 → deleteHkStructIds(bb)`): Inverse of TC[14,5]. Cascade: if HK FCC entry becomes empty after deletion → remove the APID entry from HK FCC.

**Rejection conditions**: APID not in HK FCC; struct ID not in definition for that APID.

**Gaps**: None beyond the multi-instruction type issue (same as TC[14,1/2]).

---

### TC[14,7] — Report the Content of the HK Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.5.3

**Purpose**: Request a dump of the HK FCC. No application data. Response: TM[14,8].

**XTCE**: ✅ **XTCE-only** — no-argument TC, identical pattern to TC[14,3].

```xml
<MetaCommand name="TC_14_7_REPORT_HK_FCC"
             shortDescription="TC[14,7] Report HK FCC content">
  <BaseMetaCommand metaCommandRef="pus14-tc">
    <ArgumentAssignmentList>
      <ArgumentAssignment argumentName="subtype" argumentValue="7"/>
    </ArgumentAssignmentList>
  </BaseMetaCommand>
  <CommandContainer name="TC_14_7"><EntryList/></CommandContainer>
</MetaCommand>
```

**Java** (`case 7 → reportHkFcc(tc)`): Iterate HK FCC entries; for each APID emit one TM[14,8].

**Gaps**: None.

---

### TM[14,8] — HK Parameter Report Forward-Control Configuration Content Report

**Spec**: §6.14.3.5.3

**Purpose**: One packet per HK FCC entry (per APID). Reports which HK structure identifiers are authorized for forwarding.

**Packet structure (per packet)**:
```
[apid: uint16]
[N_structs: uint8]
  repeated N_structs times:
    [hk_structure_id: uint16]
    [subsampling_rate: uint8]  ← optional; omit if subsampling not supported
```

**XTCE**: ✅ **Mostly implementable** — this is a flat 2-level structure (top-level count + array of fixed-size structs). If subsampling is omitted, each entry is a fixed 2-byte `uint16`.

```xml
<!-- ParameterTypeSet -->
<IntegerParameterType name="hk_struct_id_type" signed="false">
  <IntegerDataEncoding sizeInBits="16"/>
</IntegerParameterType>

<ArrayParameterType arrayTypeRef="hk_struct_id_type" name="hk_struct_id_array_type">
  <DimensionList>
    <Dimension>
      <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
      <EndingIndex>
        <DynamicValue>
          <ParameterInstanceRef parameterRef="hk_fcc_n_structs"/>
          <LinearAdjustment intercept="-1"/>
        </DynamicValue>
      </EndingIndex>
    </Dimension>
  </DimensionList>
</ArrayParameterType>

<!-- ContainerSet -->
<SequenceContainer name="TM_14_8" shortDescription="TM[14,8] HK FCC content report">
  <EntryList>
    <ParameterRefEntry parameterRef="hk_fcc_apid"/>
    <ParameterRefEntry parameterRef="hk_fcc_n_structs"/>
    <ParameterRefEntry parameterRef="hk_fcc_struct_ids">
      <IncludeCondition>
        <Comparison parameterRef="hk_fcc_n_structs" comparisonOperator="&gt;" value="0"/>
      </IncludeCondition>
    </ParameterRefEntry>
  </EntryList>
  <BaseContainer containerRef="pus14-tm">
    <RestrictionCriteria>
      <Comparison parameterRef="/PUS/subtype" value="8"/>
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>
```

**Java emitter** (`sendHkFccReport(int apid, List<Integer> structIds)`):
```java
PusTmPacket pkt = newPacket(8, 2 + 1 + structIds.size() * 2);
ByteBuffer bb = pkt.getUserDataBuffer();
bb.putShort((short) apid);
bb.put((byte) structIds.size());
for (int sid : structIds) bb.putShort((short) sid);
pusSimulator.transmitRealtimeTM(pkt);
```

**Gaps**: If subsampling rates are added later, each entry becomes `uint16 + uint8` (aggregate type) — minor extension.

---

### TC[14,9] — Add Structure Identifiers to the Diagnostic Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.6.1

**Purpose**: Identical structure and semantics to TC[14,5] but for diagnostic parameter reports (ST[04] structures).

**XTCE**: ⚠️ **Same as TC[14,5]** — two separate TC variants (specific structs vs add-all).

```xml
<MetaCommand name="TC_14_9_ADD_DIAG_STRUCTS">
  <ArgumentList>
    <Argument name="N"                argumentTypeRef="/dt/uint8"/>
    <Argument name="apid"             argumentTypeRef="/dt/uint16"/>
    <Argument name="diag_struct_ids"  argumentTypeRef="diag_struct_id_array_type"/>
  </ArgumentList>
</MetaCommand>

<MetaCommand name="TC_14_9_ADD_ALL_DIAG">
  <ArgumentList>
    <Argument name="apid" argumentTypeRef="/dt/uint16"/>
  </ArgumentList>
</MetaCommand>
```

**Java** (`case 9 → addDiagStructIds(bb)`): Mirror of TC[14,5] handler but targeting `diagFcc` map.

**Gaps**: None beyond TC[14,5] gaps. If the simulator does not implement ST[04] (diagnostic parameter reports), this TC has no observable effect — can be implemented as a stub that updates the diag FCC table and ACKs.

---

### TC[14,10] — Delete Structure Identifiers from the Diagnostic Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.6.2

**Purpose**: Identical structure and semantics to TC[14,6] but for diagnostic FCC.

**XTCE**: ⚠️ **Same as TC[14,6]** — three separate TC variants (delete specific structs / delete APID / empty diag FCC).

**Java** (`case 10 → deleteDiagStructIds(bb)`): Mirror of TC[14,6] handler targeting `diagFcc`.

**Gaps**: Same as TC[14,6].

---

### TC[14,11] — Report the Content of the Diagnostic Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.6.3

**Purpose**: Identical to TC[14,7] but for diagnostic FCC. No arguments. Response: TM[14,12].

**XTCE**: ✅ **XTCE-only** — no-argument TC, same as TC[14,3] and TC[14,7].

```xml
<MetaCommand name="TC_14_11_REPORT_DIAG_FCC">
  <BaseMetaCommand metaCommandRef="pus14-tc">
    <ArgumentAssignmentList>
      <ArgumentAssignment argumentName="subtype" argumentValue="11"/>
    </ArgumentAssignmentList>
  </BaseMetaCommand>
  <CommandContainer name="TC_14_11"><EntryList/></CommandContainer>
</MetaCommand>
```

**Java** (`case 11 → reportDiagFcc(tc)`): Iterate `diagFcc`; emit TM[14,12] per APID.

**Gaps**: None.

---

### TM[14,12] — Diagnostic Parameter Report Forward-Control Configuration Content Report

**Spec**: §6.14.3.6.3

**Purpose**: Identical structure to TM[14,8] but for diagnostic structure identifiers.

**Packet structure**:
```
[apid: uint16]
[N_structs: uint8]
  repeated N_structs times:
    [diag_structure_id: uint16]
```

**XTCE**: ✅ **Same as TM[14,8]** — flat 2-level structure, fully expressible using dynamic array with `diag_struct_id_array_type`.

**Java emitter**: Mirror of TM[14,8] emitter with subtype=12 and `diagFcc` data.

**Gaps**: None.

---

## c) Gaps & Shortcomings Summary

### Gap 1: Polymorphic Instruction Lists — TC[14,1] and TC[14,2]

**Affects**: TC[14,1], TC[14,2]
**Severity**: High
**Effort**: Medium

The PUS spec allows a single TC to contain a mix of different instruction types (add specific subtype + add whole service + add whole APID) in one packet. XTCE arrays require homogeneous element types with a fixed binary layout. A mixed-type instruction list cannot be expressed in a single `MetaCommand`.

**Workaround**: Define 3–4 separate XTCE MetaCommand variants per TC, each handling one instruction type at a time (N=1 per instruction). Operators send multiple TCs to compose complex table updates. Multi-instruction TCs require raw binary argument or scripting.

**Impact**: Minor operator inconvenience for multi-step table updates. No loss of functional testability.

---

### Gap 2: Nested Variable-Length TM Structure — TM[14,4]

**Affects**: TM[14,4]
**Severity**: Medium
**Effort**: Low (Java is straightforward; XTCE partial is acceptable)

TM[14,4] contains a 3-level nested structure: APFCDs → STFCDs → RTFCDs. XTCE supports 1-level dynamic arrays (count parameter + array). It cannot express nested arrays where the inner count is embedded within each outer element.

**Workaround**: The XTCE container decodes only `apid` and `N_service_types` at the outer level. Remaining bytes are opaque in YAMCS Web. Java emitter correctly constructs the full nested structure. Ground operators can use the yamcs-client Python SDK to parse the raw bytes programmatically.

---

### Gap 3: Two-Form TC Requests — TC[14,5], TC[14,6], TC[14,9], TC[14,10]

**Affects**: TC[14,5], TC[14,6], TC[14,9], TC[14,10]
**Severity**: Low
**Effort**: Low

Each TC subtype allows two mutually exclusive request forms (e.g., "list of specific structs" OR "add all for an APID"). XTCE cannot express this OR-choice in a single MetaCommand.

**Workaround**: Two separate MetaCommand variants per TC subtype (e.g., `TC_14_5_ADD_HK_STRUCTS` and `TC_14_5_ADD_ALL_HK`). Adds XTCE verbosity but is fully functional.

---

### Gap 4: Forwarding Interceptor Requires Cross-Cutting Change

**Affects**: All other PUS services (Pus5Service, Pus11Service, etc.)
**Severity**: High
**Effort**: Medium

ST[14]'s filtering must be applied to all outgoing TM packets — not just its own. The cleanest implementation is to insert the filter check inside `PusSimulator.transmitRealtimeTM()`:

```java
public void transmitRealtimeTM(PusTmPacket pkt) {
    if (pus14Service != null && !pus14Service.shouldForward(pkt)) {
        return;  // blocked by ST[14] configuration
    }
    // ... existing CRC append and send logic ...
}
```

This requires:
1. `Pus14Service.shouldForward(PusTmPacket pkt)` to read `apid`, `type`, `subtype` from the packet header
2. A single change to `PusSimulator.transmitRealtimeTM()` — no changes to individual service classes

Initial startup state (no APFCC populated) should default to **pass-all** (for simulator usability) rather than the spec-strict **block-all**. This can be toggled via a config flag.

---

### Gap 5: No Existing Pus14Service.java

**Affects**: All subtypes
**Severity**: Medium
**Effort**: Medium

New `Pus14Service.java` required from scratch. Key data structures:

```java
// Application Process Forward-Control Configuration
Map<Integer, ApfcDefinition> apfcc = new LinkedHashMap<>();

// HK Forward-Control Configuration
Map<Integer, Set<Integer>> hkFcc = new LinkedHashMap<>();

// Diagnostic Forward-Control Configuration
Map<Integer, Set<Integer>> diagFcc = new LinkedHashMap<>();

// Inner class
class ApfcDefinition {
    int apid;
    // null STFCDs = "pass all"; empty list = "block all"
    Map<Integer, Set<Integer>> serviceSubtypes = new LinkedHashMap<>();
}

public boolean shouldForward(PusTmPacket pkt) {
    int apid    = pkt.getApid();
    int svcType = pkt.getType();
    int subtype = pkt.getSubtype();

    ApfcDefinition apfcd = apfcc.get(apid);
    if (apfcd == null) return true; // pass-all default (simulator mode)

    Map<Integer, Set<Integer>> stfcds = apfcd.serviceSubtypes;
    if (stfcds.isEmpty()) return true; // APID entry exists, no restrictions

    Set<Integer> subtypes = stfcds.get(svcType);
    if (subtypes == null) return false; // service not in allowed list
    if (subtypes.isEmpty()) return true; // all subtypes of this service allowed
    return subtypes.contains(subtype);
}
```

Registration in `PusSimulator.java`:
```java
// Constructor
pus14Service = new Pus14Service(this);

// doStart() — no periodic task needed

// executePendingCommands()
case 14 -> pus14Service.executeTc(commandPacket);
```

---

### Gap 6: HK/Diagnostic FCC Not Linked to ST[03]/ST[04]

**Affects**: TC[14,5], TC[14,6], TC[14,7], TM[14,8], TC[14,9]–TM[14,12]
**Severity**: Low (optional capabilities per spec)

The HK and Diagnostic FCC features require that ST[03] (housekeeping) and ST[04] (diagnostic) services exist and use structure identifiers. The PUS simulator currently implements ST[03] HK without structure IDs (sends periodic HK as a fixed APID/type/subtype combination).

**Implication**: TC[14,5/6/7/8] can be implemented as stubs that maintain the in-memory HK FCC table and respond to TC[14,7] with TM[14,8], but the actual filtering of ST[03] reports against the HK FCC is a no-op until ST[03] is updated to use structure IDs.

**Recommended initial approach**: Implement TC/TM for HK FCC management (XTCE + Java), but leave the `shouldForwardHkReport(apid, structId)` check as a TODO. This is fully conformant — the capability is declared as optional.

---

## Summary Table

| Subtype | Dir | XTCE Coverage | Java Required | Effort | Notes |
|---------|-----|--------------|---------------|--------|-------|
| TC[14,1] | TC | ⚠️ Per-mode variants (3 MetaCommands) | ✅ Required | Medium | Mixed instruction types; 3 XTCE variants cover common cases |
| TC[14,2] | TC | ⚠️ Per-mode variants (4 MetaCommands) | ✅ Required | Medium | Same; "empty APFCC" variant is no-arg |
| TC[14,3] | TC | ✅ Full (no args) | ✅ New Pus14Service | Low | Identical to TC[11,17] pattern |
| TM[14,4] | TM | ⚠️ Outer fields only | ✅ Required (emit) | Medium | 3-level nested structure; Java constructs; XTCE decodes header only |
| TC[14,5] | TC | ⚠️ 2 variants | ✅ Required | Low | HK-specific structs vs "add all"; XTCE covers both forms |
| TC[14,6] | TC | ⚠️ 3 variants | ✅ Required | Low | Delete struct / delete APID / empty |
| TC[14,7] | TC | ✅ Full (no args) | ✅ New Pus14Service | Low | Same as TC[14,3] pattern |
| TM[14,8] | TM | ✅ Full | ✅ Required (emit) | Low | Flat 2-level; dynamic array; fully XTCE-expressible |
| TC[14,9] | TC | ⚠️ 2 variants | ✅ Required | Low | Mirror of TC[14,5] for diagnostic FCC |
| TC[14,10] | TC | ⚠️ 3 variants | ✅ Required | Low | Mirror of TC[14,6] for diagnostic FCC |
| TC[14,11] | TC | ✅ Full (no args) | ✅ New Pus14Service | Low | Mirror of TC[14,7] |
| TM[14,12] | TM | ✅ Full | ✅ Required (emit) | Low | Mirror of TM[14,8] for diagnostic FCC |

### Overall Verdict

**PUS 14 is ~75% implementable with XTCE alone**, covering TC/TM packet structures. The key gaps are:

1. **TC[14,1/2] polymorphic instruction lists** — not XTCE-expressible in a single command; 3–4 per-mode variants cover all common single-instruction operations
2. **TM[14,4] nested 3-level structure** — XTCE decodes outer fields only; Java emitter handles full construction
3. **Forwarding interceptor requires one PusSimulator.java edit** — inserting `shouldForward()` check inside `transmitRealtimeTM()` — a clean cross-cutting concern, not per-service changes
4. **New Pus14Service.java required** — but the logic is straightforward map/set operations; no timing or periodic tasks needed

**Zero changes to existing service classes** (Pus5Service, Pus11Service, etc.) are required beyond the single interceptor hook in `PusSimulator.transmitRealtimeTM()`.

---

## Implementation Files (when building)

| File | Action |
|------|--------|
| `simulator/src/main/java/org/yamcs/simulator/pus/Pus14Service.java` | Create — APFCC/HK FCC/Diag FCC data structures + TC handler + `shouldForward()` |
| `examples/pus/src/main/yamcs/mdb/pus14.xml` | Create — XTCE containers + commands (3+4+2+3 MetaCommands, 4 SequenceContainers) |
| `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java` | Edit — register Pus14Service; add `shouldForward()` gate in `transmitRealtimeTM()` |
| `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` | Edit — add `mdb/pus14.xml` to MDB list |

### Reference Files
- `simulator/src/main/java/org/yamcs/simulator/pus/AbstractPusService.java` — base class
- `simulator/src/main/java/org/yamcs/simulator/pus/PusTmPacket.java` — packet structure
- `examples/pus/src/main/yamcs/mdb/pus5.xml` — XTCE pattern reference
- `pus_simulator_architecture.md` — full architecture reference
