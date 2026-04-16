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

**Purpose**: Add forwarding permissions. The packet carries N1 application process entries, each with N2 service type entries, each with N3 subtype entries. The three "instruction forms" from the spec are encoded via the count fields:

| Instruction Form | Encoding | Effect |
|---|---|---|
| Add specific report type | `apid` + N2=1 + `service_type` + N3=1 + `subtype` | Enable forwarding of one specific TM subtype |
| Add all subtypes of a service | `apid` + N2=1 + `service_type` + **N3=0** | Enable forwarding of all subtypes of a service |
| Add all services of an APID | `apid` + **N2=0** | Enable forwarding of all reports from an application process |

**Packet layout** (Figure 8-147):
```
N1 (uint8)
  repeated N1 times:
    apid (uint16)
    N2 (uint8)             ← 0 = "add all services for this APID"
    repeated N2 times:
      service_type (uint8)
      N3 (uint8)           ← 0 = "add all subtypes of this service type"
      repeated N3 times:
        subtype (uint8)
```

**XTCE**: ✅ **Single MetaCommand — fully implementable**

YAMCS supports nested dynamic arrays in TC arguments where each inner array's size is determined by a **sibling member** of the enclosing aggregate (confirmed by `yamcs-core/src/test/resources/xtce/array-in-array-arg.xml`). The `ArgumentInstanceRef argumentRef="N2"` inside `ServiceTypeArrayType` resolves to the `N2` member of the containing `ApfcdEntryType` aggregate — no top-level reference is needed.

```xml
<!-- Innermost element: one subtype -->
<IntegerArgumentType name="apfc_subtype_type" baseType="/dt/uint8"/>

<!-- Array of N3 subtypes; N3 is a sibling member in the containing aggregate -->
<ArrayArgumentType name="apfc_subtype_array_type" arrayTypeRef="apfc_subtype_type">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N3"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<!-- Service type entry: service_type + N3 + N3×subtype -->
<AggregateArgumentType name="apfc_service_entry_type">
    <MemberList>
        <Member name="service_type" typeRef="/dt/uint8"/>
        <Member name="N3"           typeRef="/dt/uint8"/>
        <Member name="subtypes"     typeRef="apfc_subtype_array_type"/>
    </MemberList>
</AggregateArgumentType>

<!-- Array of N2 service entries; N2 is a sibling member in the containing aggregate -->
<ArrayArgumentType name="apfc_service_array_type" arrayTypeRef="apfc_service_entry_type">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N2"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<!-- APFCD entry: apid + N2 + N2×service_entry -->
<AggregateArgumentType name="apfcd_entry_type">
    <MemberList>
        <Member name="apid"          typeRef="/dt/uint16"/>
        <Member name="N2"            typeRef="/dt/uint8"/>
        <Member name="service_types" typeRef="apfc_service_array_type"/>
    </MemberList>
</AggregateArgumentType>

<!-- Outer array of N1 APFCD entries; N1 is a top-level argument -->
<ArrayArgumentType name="apfcd_array_type" arrayTypeRef="apfcd_entry_type">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N1"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<!-- Single MetaCommand handles all three instruction forms -->
<MetaCommand name="TC_14_1_ADD_REPORT_TYPES"
             shortDescription="TC[14,1] Add report types to APFC configuration">
    <BaseMetaCommand metaCommandRef="pus14-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="1"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument name="N1"            argumentTypeRef="/dt/uint8"/>
        <Argument name="apfcd_entries" argumentTypeRef="apfcd_array_type"/>
    </ArgumentList>
    <CommandContainer name="TC_14_1">
        <EntryList>
            <ArgumentRefEntry argumentRef="N1"/>
            <ArgumentRefEntry argumentRef="apfcd_entries"/>
        </EntryList>
        <BaseContainer containerRef="pus14-tc"/>
    </CommandContainer>
</MetaCommand>
```

**Java** (`case 1 → addReportTypes(bb)`):
```java
int n1 = bb.get() & 0xFF;
for (int i = 0; i < n1; i++) {
    int apid = bb.getShort() & 0xFFFF;
    int n2 = bb.get() & 0xFF;
    if (n2 == 0) {
        // Spec §8.14.2.1c: N2=0 → add all services for this APID
        apfcc.computeIfAbsent(apid, k -> new ApfcDefinition(k));
        // Empty serviceSubtypes map = "pass all" (see shouldForward logic)
    } else {
        ApfcDefinition apfcd = apfcc.computeIfAbsent(apid, k -> new ApfcDefinition(k));
        for (int j = 0; j < n2; j++) {
            int svcType = bb.get() & 0xFF;
            int n3 = bb.get() & 0xFF;
            if (n3 == 0) {
                // Spec §8.14.2.1d: N3=0 → add all subtypes of this service type
                apfcd.serviceSubtypes.computeIfAbsent(svcType, k -> new LinkedHashSet<>());
                // Empty set = "pass all subtypes" for this service
            } else {
                Set<Integer> subtypes = apfcd.serviceSubtypes
                    .computeIfAbsent(svcType, k -> new LinkedHashSet<>());
                for (int k = 0; k < n3; k++) {
                    subtypes.add(bb.get() & 0xFF);
                }
            }
        }
    }
}
ack_completion(tc);
```

**Rejection conditions** (per spec):
- Max service type forward-control definitions already reached
- Max report type forward-control definitions already reached
- APID not controlled by this subservice

**Gaps**: None. Single MetaCommand covers all three instruction forms. N2=0 and N3=0 are zero-length arrays, which YAMCS renders as empty array inputs in the UI.

---

### TC[14,2] — Delete Report Types from the Application Process Forward-Control Configuration

**Spec**: §6.14.3.4.2

**Purpose**: Remove forwarding permissions. Contains EITHER:
1. One or more delete instructions using the same N1/N2/N3 structure as TC[14,1], OR
2. A single "empty the entire APFCC" instruction (no arguments)

**Delete instruction encoding** (mirrors TC[14,1] structure):

| Instruction Form | Encoding | Effect |
|---|---|---|
| Delete specific report type | `apid` + N2=1 + `service_type` + N3=1 + `subtype` | Remove one subtype from APFCC |
| Delete a service type | `apid` + N2=1 + `service_type` + **N3=0** | Remove entire STFCD for that service type |
| Delete an application process | `apid` + **N2=0** | Remove entire APFCD for that APID |

**XTCE**: ✅ **Two MetaCommand variants** (delete-entries + empty-APFCC)

The delete-entries variant reuses the same N1/N2/N3 nested argument types defined for TC[14,1]. The empty-APFCC variant is a zero-argument command.

```xml
<!-- TC[14,2]a — Delete entries: reuses apfcd_array_type from TC[14,1] argument types -->
<MetaCommand name="TC_14_2_DELETE_ENTRIES"
             shortDescription="TC[14,2] Delete report types from APFC configuration">
    <BaseMetaCommand metaCommandRef="pus14-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="2"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument name="N1"            argumentTypeRef="/dt/uint8"/>
        <Argument name="apfcd_entries" argumentTypeRef="apfcd_array_type"/>
    </ArgumentList>
    <CommandContainer name="TC_14_2_DELETE">
        <EntryList>
            <ArgumentRefEntry argumentRef="N1"/>
            <ArgumentRefEntry argumentRef="apfcd_entries"/>
        </EntryList>
        <BaseContainer containerRef="pus14-tc"/>
    </CommandContainer>
</MetaCommand>

<!-- TC[14,2]b — Empty the entire APFCC (no arguments) -->
<MetaCommand name="TC_14_2_EMPTY_APFCC"
             shortDescription="TC[14,2] Empty entire APFC configuration">
    <BaseMetaCommand metaCommandRef="pus14-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="2"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <CommandContainer name="TC_14_2_EMPTY">
        <EntryList/>
        <BaseContainer containerRef="pus14-tc"/>
    </CommandContainer>
</MetaCommand>
```

**Java** (`case 2 → deleteReportTypes(bb)`):
```java
if (bb.remaining() == 0) {
    // Empty-APFCC variant
    apfcc.clear();
    ack_completion(tc);
    return;
}
int n1 = bb.get() & 0xFF;
for (int i = 0; i < n1; i++) {
    int apid = bb.getShort() & 0xFFFF;
    int n2 = bb.get() & 0xFF;
    ApfcDefinition apfcd = apfcc.get(apid);
    if (apfcd == null) { nack(tc, 1, 4); return; }  // APID not in APFCC
    if (n2 == 0) {
        apfcc.remove(apid);  // Remove entire APFCD
    } else {
        for (int j = 0; j < n2; j++) {
            int svcType = bb.get() & 0xFF;
            int n3 = bb.get() & 0xFF;
            if (n3 == 0) {
                apfcd.serviceSubtypes.remove(svcType);  // Remove entire STFCD
                if (apfcd.serviceSubtypes.isEmpty()) apfcc.remove(apid);
            } else {
                Set<Integer> subtypes = apfcd.serviceSubtypes.get(svcType);
                if (subtypes == null) { nack(tc, 1, 4); return; }
                for (int k = 0; k < n3; k++) {
                    subtypes.remove(bb.get() & 0xFF);
                }
                if (subtypes.isEmpty()) apfcd.serviceSubtypes.remove(svcType);
                if (apfcd.serviceSubtypes.isEmpty()) apfcc.remove(apid);
            }
        }
    }
}
ack_completion(tc);
```

**Rejection conditions**: Referenced APID/service/subtype not in APFCC → NACK[1,4] per-instruction.

**Gaps**: The two TC variants (delete-entries and empty-APFCC) cannot be collapsed into one because the empty-APFCC case has no N1 field — a zero-byte payload is the discriminator.

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

**XTCE**: ✅ **Fully implementable** — YAMCS supports nested `ContainerRefEntry` + `RepeatEntry` where the inner count is a parameter decoded within each outer element.

The mechanism: `ParameterInstanceRef` defaults to `relativeTo = CURRENT_ENTRY_WITHIN_PACKET` and `instance = 0`, which calls `tmParams.getFromEnd(param, 0)` — the **most recently decoded** value of the parameter. Each outer repeat iteration decodes a fresh `N_subtypes`; the inner `RepeatEntry` count resolves to that just-decoded value, not a stale one from a previous iteration. This is confirmed in `yamcs-xtce/src/main/java/org/yamcs/xtce/ParameterInstanceRef.java` (line 53).

```xml
<!-- Innermost: one subtype ID -->
<SequenceContainer name="apfc_subtype_element">
    <EntryList>
        <ParameterRefEntry parameterRef="apfc_subtype_id"/>
    </EntryList>
</SequenceContainer>

<!-- Middle: one service type entry + its variable-length subtype array -->
<SequenceContainer name="apfc_service_element">
    <EntryList>
        <ParameterRefEntry parameterRef="apfc_service_type_id"/>
        <ParameterRefEntry parameterRef="apfc_N_subtypes"/>
        <ContainerRefEntry containerRef="apfc_subtype_element">
            <RepeatEntry>
                <Count>
                    <DynamicValue>
                        <!-- default instance=0, CURRENT_ENTRY_WITHIN_PACKET → most recently decoded value -->
                        <ParameterInstanceRef parameterRef="apfc_N_subtypes"/>
                    </DynamicValue>
                </Count>
            </RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
</SequenceContainer>

<!-- Outer TM packet: apid + N_services + N_services×service_element -->
<SequenceContainer name="TM_14_4" shortDescription="TM[14,4] APFC config content report">
    <EntryList>
        <ParameterRefEntry parameterRef="apfc_apid"/>
        <ParameterRefEntry parameterRef="apfc_N_services"/>
        <ContainerRefEntry containerRef="apfc_service_element">
            <RepeatEntry>
                <Count>
                    <DynamicValue>
                        <ParameterInstanceRef parameterRef="apfc_N_services"/>
                    </DynamicValue>
                </Count>
            </RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
    <BaseContainer containerRef="pus14-tm">
        <RestrictionCriteria>
            <Comparison parameterRef="/PUS/subtype" value="4"/>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Note on parameter naming**: `apfc_subtype_id`, `apfc_service_type_id`, and `apfc_N_subtypes` are shared parameters that accumulate multiple values in `tmParams` across repeat iterations. The `getFromEnd(param, 0)` semantic always picks the most recently decoded value, so inner repeat counts are always correct. All extracted values are stored as separate `ParameterValue` instances in the result list.

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
- One TM packet per APFCD; if table is large, may generate many packets
- No XTCE decoding limitation — full 3-level structure is expressible via nested `ContainerRefEntry` repeats

---

### TC[14,5] — Add Structure Identifiers to the HK Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.5.1

**Purpose**: Authorize specific housekeeping parameter report structures for forwarding. The packet carries N1 APID entries; each APID entry contains N_structs structure IDs to authorize. N_structs=0 means "authorize all structures for this APID" (spec §6.14.3.5.1 convention, same as TC[14,1]'s N2=0).

**Packet layout**:
```
N1 (uint8) — number of application process entries
  repeated N1 times:
    apid (uint16)
    N_structs (uint8)    ← 0 = "add all HK structures for this APID"
    repeated N_structs times:
      hk_structure_id (uint16)
```

**XTCE**: ✅ **Single MetaCommand** — 2-level nested arrays (N1 outer APIDs, N_structs inner struct IDs per APID), using the same sibling-member array-size reference pattern as TC[14,1].

```xml
<!-- Inner: array of N_structs HK structure IDs; N_structs is a sibling member -->
<ArrayArgumentType name="hk_struct_id_array_type" arrayTypeRef="/dt/uint16">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N_structs"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<!-- APID entry: apid + N_structs + struct_ids array -->
<AggregateArgumentType name="hk_apid_entry_type">
    <MemberList>
        <Member name="apid"       typeRef="/dt/uint16"/>
        <Member name="N_structs"  typeRef="/dt/uint8"/>
        <Member name="struct_ids" typeRef="hk_struct_id_array_type"/>
    </MemberList>
</AggregateArgumentType>

<!-- Outer array of N1 APID entries -->
<ArrayArgumentType name="hk_apid_array_type" arrayTypeRef="hk_apid_entry_type">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N1"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<MetaCommand name="TC_14_5_ADD_HK_STRUCTS"
             shortDescription="TC[14,5] Add HK structure identifiers to HK FCC">
    <BaseMetaCommand metaCommandRef="pus14-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="5"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument name="N1"           argumentTypeRef="/dt/uint8"/>
        <Argument name="apid_entries" argumentTypeRef="hk_apid_array_type"/>
    </ArgumentList>
    <CommandContainer name="TC_14_5">
        <EntryList>
            <ArgumentRefEntry argumentRef="N1"/>
            <ArgumentRefEntry argumentRef="apid_entries"/>
        </EntryList>
        <BaseContainer containerRef="pus14-tc"/>
    </CommandContainer>
</MetaCommand>
```

**Java** (`case 5 → addHkStructIds(bb)`):
```java
int n1 = bb.get() & 0xFF;
for (int i = 0; i < n1; i++) {
    int apid = bb.getShort() & 0xFFFF;
    int nStructs = bb.get() & 0xFF;
    if (nStructs == 0) {
        // N_structs=0: add all HK structures for this APID
        hkFcc.put(apid, null);  // null = pass-all mode
    } else {
        Set<Integer> structs = hkFcc.computeIfAbsent(apid, k -> new LinkedHashSet<>());
        for (int j = 0; j < nStructs; j++) {
            structs.add(bb.getShort() & 0xFFFF);
        }
    }
}
ack_completion(tc);
```

**Rejection conditions**: APID not controlled by subservice; max struct IDs reached.

**Gaps**: Subsampling rate is optional per spec (§6.14.3.2.1d). For initial simulator implementation, omit subsampling — all authorized structures are forwarded at their native rate. This is a valid simplification (subsampling is a declared capability, not mandatory).

---

### TC[14,6] — Delete Structure Identifiers from the HK Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.5.2

**Purpose**: Revoke specific HK structure forwarding permissions. Contains EITHER:
1. One or more delete instructions using the same N1/N_structs structure as TC[14,5], OR
2. An "empty HK FCC" instruction (no arguments)

**Delete instruction encoding** (same N=0 convention as TC[14,1/2]):

| Instruction Form | Encoding | Effect |
|---|---|---|
| Delete specific HK struct IDs | `apid` + N_structs>0 + `struct_ids[]` | Remove listed structs from HK FCC for APID |
| Delete an application process | `apid` + **N_structs=0** | Remove entire APID entry from HK FCC |

**XTCE**: ✅ **Two MetaCommand variants** (delete-entries + empty-HK-FCC)

The delete-entries variant reuses `hk_apid_array_type` from TC[14,5]. The empty-HK-FCC variant is a zero-argument command.

```xml
<!-- TC[14,6]a — Delete entries: reuses hk_apid_array_type from TC[14,5] -->
<MetaCommand name="TC_14_6_DELETE_HK_ENTRIES"
             shortDescription="TC[14,6] Delete HK structure identifiers from HK FCC">
    <BaseMetaCommand metaCommandRef="pus14-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="6"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument name="N1"           argumentTypeRef="/dt/uint8"/>
        <Argument name="apid_entries" argumentTypeRef="hk_apid_array_type"/>
    </ArgumentList>
    <CommandContainer name="TC_14_6_DELETE">
        <EntryList>
            <ArgumentRefEntry argumentRef="N1"/>
            <ArgumentRefEntry argumentRef="apid_entries"/>
        </EntryList>
        <BaseContainer containerRef="pus14-tc"/>
    </CommandContainer>
</MetaCommand>

<!-- TC[14,6]b — Empty the entire HK FCC -->
<MetaCommand name="TC_14_6_EMPTY_HK_FCC"
             shortDescription="TC[14,6] Empty HK FCC">
    <BaseMetaCommand metaCommandRef="pus14-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="6"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <CommandContainer name="TC_14_6_EMPTY">
        <EntryList/>
        <BaseContainer containerRef="pus14-tc"/>
    </CommandContainer>
</MetaCommand>
```

**Java** (`case 6 → deleteHkStructIds(bb)`):
```java
if (bb.remaining() == 0) {
    hkFcc.clear();
    ack_completion(tc);
    return;
}
int n1 = bb.get() & 0xFF;
for (int i = 0; i < n1; i++) {
    int apid = bb.getShort() & 0xFFFF;
    int nStructs = bb.get() & 0xFF;
    if (nStructs == 0) {
        hkFcc.remove(apid);  // Delete entire APID entry
    } else {
        Set<Integer> structs = hkFcc.get(apid);
        if (structs == null) { nack(tc, 1, 4); return; }
        for (int j = 0; j < nStructs; j++) {
            structs.remove(bb.getShort() & 0xFFFF);
        }
        if (structs.isEmpty()) hkFcc.remove(apid);
    }
}
ack_completion(tc);
```

**Rejection conditions**: APID not in HK FCC; struct ID not in definition for that APID.

**Gaps**: Two variants needed because the empty-HK-FCC case has no N1 field — zero remaining bytes is the discriminator (same pattern as TC[14,2]).

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

**XTCE**: ✅ **Single MetaCommand** — identical N1/N_structs nested array design as TC[14,5], using `diag_apid_array_type` (mirrors `hk_apid_array_type` with `diag_struct_id` uint16 elements). N_structs=0 = add all diagnostic structures for that APID.

```xml
<!-- Reuse same aggregate+array pattern as TC[14,5], renaming types for clarity -->
<ArrayArgumentType name="diag_struct_id_array_type" arrayTypeRef="/dt/uint16">
    <!-- same DimensionList as hk_struct_id_array_type, argumentRef="N_structs" -->
    ...
</ArrayArgumentType>
<AggregateArgumentType name="diag_apid_entry_type">
    <MemberList>
        <Member name="apid"       typeRef="/dt/uint16"/>
        <Member name="N_structs"  typeRef="/dt/uint8"/>
        <Member name="struct_ids" typeRef="diag_struct_id_array_type"/>
    </MemberList>
</AggregateArgumentType>
<!-- outer array + MetaCommand TC_14_9_ADD_DIAG_STRUCTS: identical to TC[14,5] with subtype=9 -->
```

**Java** (`case 9 → addDiagStructIds(bb)`): Mirror of TC[14,5] handler targeting `diagFcc` map; N_structs=0 sets `diagFcc.put(apid, null)` (pass-all mode).

**Gaps**: None beyond TC[14,5] gaps. If the simulator does not implement ST[04] (diagnostic parameter reports), this TC has no observable effect — can be implemented as a stub that updates the diag FCC table and ACKs.

---

### TC[14,10] — Delete Structure Identifiers from the Diagnostic Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.6.2

**Purpose**: Identical structure and semantics to TC[14,6] but for diagnostic FCC.

**XTCE**: ✅ **Two MetaCommand variants** — identical design as TC[14,6]: delete-entries (N1/N_structs nested, reuses `diag_apid_array_type`) + empty-diag-FCC (no-arg). N_structs=0 = delete entire APID entry from diag FCC.

**Java** (`case 10 → deleteDiagStructIds(bb)`): Mirror of TC[14,6] handler targeting `diagFcc`.

**Gaps**: Two variants needed for the same reason as TC[14,6]: zero-byte payload is the discriminator for the empty-diag-FCC case.

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

### Gap 1: TC[14,2] Empty-APFCC Variant — Resolved for TC[14,1], Minor for TC[14,2]

**Affects**: TC[14,2] only
**Severity**: Low
**Effort**: Negligible

TC[14,1] is fully expressible as a **single MetaCommand** using YAMCS's nested dynamic array support (`array-in-array-arg.xml` confirms that `ArgumentInstanceRef` in an `ArrayArgumentType` can reference a sibling member of the containing `AggregateArgumentType`). The N1/N2/N3 structure with N2=0 (all services) and N3=0 (all subtypes) covers all three spec instruction forms in one command.

TC[14,2] requires **two variants**: one for the N1/N2/N3 delete-entries structure (same nested array design as TC[14,1]) and one zero-argument "empty APFCC" command. This is unavoidable because the empty-APFCC case has no N1 field — a zero-byte payload is the discriminator. Two variants is not a functional gap; it is a faithful representation of the spec's two mutually exclusive request forms.

**Impact**: None for TC[14,1]. TC[14,2] requires two operator-visible commands (`TC_14_2_DELETE_ENTRIES` and `TC_14_2_EMPTY_APFCC`) — standard YAMCS practice for commands with distinct argument structures.

---

### Gap 2: TM[14,4] — Resolved

**Affects**: TM[14,4]
**Severity**: None — fully resolved
**Effort**: None

TM[14,4]'s 3-level nested structure (APFCDs → STFCDs → RTFCDs) IS fully expressible in XTCE using nested `ContainerRefEntry` + `RepeatEntry` containers. The key mechanism: `ParameterInstanceRef` defaults to `relativeTo = CURRENT_ENTRY_WITHIN_PACKET` (confirmed in `ParameterInstanceRef.java` line 53), which uses `tmParams.getFromEnd(param, 0)` = **most recently decoded value**. Each outer STFCD_ELEMENT iteration decodes a fresh `N_subtypes`; the inner `RepeatEntry` count resolves to that value automatically. No workaround needed.

---

### Gap 3: HK/Diag FCC TC Two-Form Requests — Resolved

**Affects**: TC[14,5], TC[14,6], TC[14,9], TC[14,10]
**Severity**: None — fully resolved

TC[14,5/9] are now single MetaCommands with N1/N_structs 2-level nested arrays (N_structs=0 = "add all structs for this APID"). TC[14,6/10] use 2 variants each (delete-entries + empty-FCC no-arg) for the same reason as TC[14,2]: the empty-FCC case has no N1 field, making zero-byte payload the only discriminator. These 2 variants are spec-faithful, not workarounds.

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
| TC[14,1] | TC | ✅ Single MetaCommand (N1/N2/N3 nested arrays) | ✅ Required | Medium | YAMCS supports sibling-member array size refs; N2=0/N3=0 encode "add all" |
| TC[14,2] | TC | ✅ 2 variants (delete-entries N1/N2/N3 + empty-APFCC no-arg) | ✅ Required | Medium | Delete entries reuses TC[14,1] nested array types; empty-APFCC is zero-byte discriminator |
| TC[14,3] | TC | ✅ Full (no args) | ✅ New Pus14Service | Low | Identical to TC[11,17] pattern |
| TM[14,4] | TM | ✅ Full (nested container repeats) | ✅ Required (emit) | Medium | 3-level nested XTCE structure; `CURRENT_ENTRY_WITHIN_PACKET` getFromEnd(0) picks most-recent N_subtypes per iteration |
| TC[14,5] | TC | ✅ Single MetaCommand (N1/N_structs nested) | ✅ Required | Low | N_structs=0 = add all structs; same sibling-member array-size pattern as TC[14,1] |
| TC[14,6] | TC | ✅ 2 variants (delete-entries + empty-HK-FCC no-arg) | ✅ Required | Low | Delete entries reuses hk_apid_array_type; empty-HK-FCC is zero-byte discriminator |
| TC[14,7] | TC | ✅ Full (no args) | ✅ New Pus14Service | Low | Same as TC[14,3] pattern |
| TM[14,8] | TM | ✅ Full | ✅ Required (emit) | Low | Flat 2-level; dynamic array; fully XTCE-expressible |
| TC[14,9] | TC | ✅ Single MetaCommand (N1/N_structs nested) | ✅ Required | Low | Mirror of TC[14,5] for diagnostic FCC; same design |
| TC[14,10] | TC | ✅ 2 variants (delete-entries + empty-diag-FCC no-arg) | ✅ Required | Low | Mirror of TC[14,6] for diagnostic FCC |
| TC[14,11] | TC | ✅ Full (no args) | ✅ New Pus14Service | Low | Mirror of TC[14,7] |
| TM[14,12] | TM | ✅ Full | ✅ Required (emit) | Low | Mirror of TM[14,8] for diagnostic FCC |

### Overall Verdict

**PUS 14 is ~99% implementable with XTCE alone**, covering all TC/TM packet structures. The only remaining items are architectural (Java service), not XTCE limitations:

1. **All TC commands**: fully expressible as XTCE MetaCommands — single commands for TC[14,1], TC[14,3], TC[14,5], TC[14,7], TC[14,9], TC[14,11]; two variants for TC[14,2], TC[14,6], TC[14,10] (delete-entries + empty-FCC no-arg)
2. **All TM packets**: fully decodeable in XTCE — including TM[14,4]'s 3-level nested structure via `ContainerRefEntry` nested `RepeatEntry` with `CURRENT_ENTRY_WITHIN_PACKET` semantics
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
