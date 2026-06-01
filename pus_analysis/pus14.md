# PUS ST[14] Real-Time Forwarding Control — Research & Implementation Guide

**Source**: ECSS-E-ST-70-41C §6.14 (pages 237–256)
**Target env**: yamcs-Pixxel-fork (Java simulator + YAMCS XTCE MDB)

---

## a) General PUS 14 Context

### Purpose

ST[14] **Real-Time Forwarding Control** provides the capability to control which on-board reports (TM packets) are forwarded to the ground via the real-time telemetry channel. The service acts as a gate: it defines per-application-process conditions that authorize or block forwarding of specific report types.

This is fundamentally different from all other PUS services: ST[14] does **not** generate telemetry autonomously. Instead, it maintains an in-memory **forwarding control table** that is consulted every time any other service generates a TM packet before that packet is placed on the downlink.

---

### Ground vs. On-board Responsibility (MCS is ground segment only)

| Responsibility | Where |
|---|---|
| Send APFCC configuration TCs (add, delete report types) — TC[14,1], TC[14,2] | **Ground (YAMCS MCS)** — XTCE encodes TC packets |
| Send HK FCC configuration TCs — TC[14,5], TC[14,6] | **Ground (YAMCS MCS)** — XTCE encodes TC packets |
| Send Diagnostic FCC configuration TCs — TC[14,9], TC[14,10] | **Ground (YAMCS MCS)** — XTCE encodes TC packets |
| Request APFCC/HK FCC/Diag FCC dump (TC[14,3], TC[14,7], TC[14,11]) | **Ground (YAMCS MCS)** — XTCE encodes no-arg TC packets |
| Receive and display dump reports (TM[14,4], TM[14,8], TM[14,12]) | **Ground (YAMCS MCS)** — XTCE decodes TM packets |
| Maintain the APFCC/HK FCC/Diag FCC tables at runtime | **On-board (satellite)** |
| Consult forwarding table and gate TM packets before downlink | **On-board (satellite)** |
| ACK/NACK TC execution (ST[01] reports) | **On-board (satellite)** |

**YAMCS/MCS implementation = XTCE only (`pus14.xml`). No Java changes to `yamcs-core` are needed for ST[14].**

The `Pus14Service.java` described in this document lives in the **simulator package** and emulates the satellite's on-board forwarding control behavior for ground testing. It is not part of the MCS.

> **Note**: The simulator's `shouldForward()` gate and all APFCC/HK FCC/Diag FCC table management runs entirely inside the simulator (on-board emulation). YAMCS MCS only sends TCs and receives TM reports — both purely via XTCE.

---

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

The forwarding decision is a three-tier hierarchy. All steps below execute **on-board**; YAMCS/MCS only observes the TM packets that the satellite chooses to downlink:

```
[ON-BOARD] 1. If APID is not in APFCC → BLOCK (no definition = no forwarding)

[ON-BOARD] 2. If APID is in APFCC and APFCC contains at least one STFCD, but
              NOT for this report's service type → BLOCK

[ON-BOARD] 3. If APID in APFCC, STFCD exists for this service type, STFCD has
              at least one RTFCD, but NOT for this report's subtype → BLOCK

[ON-BOARD] 4. Otherwise → ALLOW

Special cases (optional on-board capabilities):
  [ON-BOARD]  - If HK FCC capability enabled: block HK reports not in the HK FCC structure ID list
  [ON-BOARD]  - If Diag FCC capability enabled: block diagnostic reports not in the Diag FCC
  [ON-BOARD]  - If Event Blocking capability enabled: block events whose event_id is in the blocking list
```

**Key invariant**: An empty APFCD (APID entry exists but has no STFCDs) means "forward all reports for this APID". A missing APID entry means "block all".

### Architectural Note for Simulator (On-board Emulation)

In a flight system, ST[14] filtering happens at the downlink level — the **flight software** (on-board) decides which packets to send based on the APFCC. The YAMCS Java simulator emulates this on-board behavior:
- **[ON-BOARD emulation]** The simulator (`Pus14Service.java`) maintains the APFCC in memory — mirroring satellite RAM state
- **[ON-BOARD emulation]** Every outgoing TM packet from any service is checked against the APFCC before calling `pusSimulator.transmitRealtimeTM(packet)` — this mirrors the satellite's downlink gate
- This requires a **cross-cutting concern** inside the simulator: other services call a `shouldForward(apid, type, subtype)` gate method before transmitting
- Alternatively, the simulator can intercept in `PusSimulator.transmitRealtimeTM()` directly

YAMCS MCS does **not** perform any of this filtering — it only sends TCs to configure the satellite's forwarding rules and decodes the TM dump reports that the satellite returns.

### Architecture Files (to be created)

| Layer | Purpose | Path |
|-------|---------|------|
| **Simulator (on-board emulation)** | Java service — APFCC/HK FCC/Diag FCC tables + `shouldForward()` gate | `simulator/src/main/java/org/yamcs/simulator/pus/Pus14Service.java` |
| **Simulator (on-board emulation)** | Register Pus14Service; add `shouldForward()` gate in `transmitRealtimeTM()` | `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java` |
| **MCS / YAMCS ground** | XTCE MDB (TC encoding + TM decoding) | `examples/pus/src/main/yamcs/mdb/pus14.xml` |
| **MCS / YAMCS ground** | Add MDB reference | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` |

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
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite parses it, updates APFCC, and issues ACK/NACK.

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

**Simulator (on-board emulation)** (`case 1 → addReportTypes(bb)`) — emulates satellite-side APFCC update:
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
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite parses it, updates APFCC, and issues ACK/NACK.

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

**Simulator (on-board emulation)** (`case 2 → deleteReportTypes(bb)`) — emulates satellite-side APFCC deletion:
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
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite iterates APFCC and emits one TM[14,4] per APFCD. **[SAT → GROUND]** — TM[14,4] packets downlinked and decoded by YAMCS via XTCE.

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

**Simulator (on-board emulation)** (`case 3 → reportApfcc(tc)`): Iterates `apfcc.entries()`; for each APFCD builds and sends one TM[14,4] — emulating satellite-side dump generation.

**Gaps**: None. Standard no-argument TC.

---

### TM[14,4] — Application Process Forward-Control Configuration Content Report

**Spec**: §6.14.3.4.3
**Direction**: **[SAT → GROUND]** — generated on-board in response to TC[14,3]; decoded by YAMCS MCS via XTCE for display only.

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

**Simulator (on-board emulation)** — `sendApfcReport(ApfcDefinition apfcd)` emulates satellite building and downlinking TM[14,4]:
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
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite parses it, updates the HK FCC table, and issues ACK/NACK.

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

**Simulator (on-board emulation)** (`case 5 → addHkStructIds(bb)`) — emulates satellite-side HK FCC update:
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
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite parses it, updates the HK FCC table, and issues ACK/NACK.

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

**Simulator (on-board emulation)** (`case 6 → deleteHkStructIds(bb)`) — emulates satellite-side HK FCC deletion:
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
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite iterates HK FCC and emits one TM[14,8] per APID. **[SAT → GROUND]** — TM[14,8] packets downlinked and decoded by YAMCS via XTCE.

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

**Simulator (on-board emulation)** (`case 7 → reportHkFcc(tc)`): Iterates HK FCC entries; for each APID emits one TM[14,8] — emulating satellite-side dump generation.

**Gaps**: None.

---

### TM[14,8] — HK Parameter Report Forward-Control Configuration Content Report

**Spec**: §6.14.3.5.3
**Direction**: **[SAT → GROUND]** — generated on-board in response to TC[14,7]; decoded by YAMCS MCS via XTCE for display only.

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

**Simulator (on-board emulation)** — `sendHkFccReport(int apid, List<Integer> structIds)` emulates satellite building and downlinking TM[14,8]:
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
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite parses it, updates the Diagnostic FCC table, and issues ACK/NACK.

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

**Simulator (on-board emulation)** (`case 9 → addDiagStructIds(bb)`): Mirror of TC[14,5] handler targeting `diagFcc` map — emulates satellite-side Diag FCC update; N_structs=0 sets `diagFcc.put(apid, null)` (pass-all mode).

**Gaps**: None beyond TC[14,5] gaps. If the simulator does not implement ST[04] (diagnostic parameter reports), this TC has no observable effect — can be implemented as a stub that updates the diag FCC table and ACKs.

---

### TC[14,10] — Delete Structure Identifiers from the Diagnostic Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.6.2
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite parses it, updates the Diagnostic FCC table, and issues ACK/NACK.

**Purpose**: Identical structure and semantics to TC[14,6] but for diagnostic FCC.

**XTCE**: ✅ **Two MetaCommand variants** — identical design as TC[14,6]: delete-entries (N1/N_structs nested, reuses `diag_apid_array_type`) + empty-diag-FCC (no-arg). N_structs=0 = delete entire APID entry from diag FCC.

**Simulator (on-board emulation)** (`case 10 → deleteDiagStructIds(bb)`): Mirror of TC[14,6] handler targeting `diagFcc` — emulates satellite-side Diag FCC deletion.

**Gaps**: Two variants needed for the same reason as TC[14,6]: zero-byte payload is the discriminator for the empty-diag-FCC case.

---

### TC[14,11] — Report the Content of the Diagnostic Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.6.3
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite iterates Diag FCC and emits one TM[14,12] per APID. **[SAT → GROUND]** — TM[14,12] packets downlinked and decoded by YAMCS via XTCE.

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

**Simulator (on-board emulation)** (`case 11 → reportDiagFcc(tc)`): Iterates `diagFcc`; emits TM[14,12] per APID — emulating satellite-side dump generation.

**Gaps**: None.

---

### TM[14,12] — Diagnostic Parameter Report Forward-Control Configuration Content Report

**Spec**: §6.14.3.6.3
**Direction**: **[SAT → GROUND]** — generated on-board in response to TC[14,11]; decoded by YAMCS MCS via XTCE for display only.

**Purpose**: Identical structure to TM[14,8] but for diagnostic structure identifiers.

**Packet structure**:
```
[apid: uint16]
[N_structs: uint8]
  repeated N_structs times:
    [diag_structure_id: uint16]
```

**XTCE**: ✅ **Same as TM[14,8]** — flat 2-level structure, fully expressible using dynamic array with `diag_struct_id_array_type`.

**Simulator (on-board emulation)**: Mirror of TM[14,8] emitter with subtype=12 and `diagFcc` data — emulates satellite building and downlinking the Diagnostic FCC content report.

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

### Gap 4: Forwarding Interceptor Requires Cross-Cutting Simulator Change

**Affects**: Simulator only — all other `Pus*Service` classes (Pus5Service, Pus11Service, etc.)
**Severity**: High (simulator scope)
**Effort**: Medium
**Layer**: **Simulator (on-board emulation)** — this is entirely within the simulator; no YAMCS MCS or `yamcs-core` changes are needed.

ST[14]'s on-board forwarding filter must be emulated across all outgoing TM packets in the simulator — not just its own. The cleanest implementation is to insert the filter check inside `PusSimulator.transmitRealtimeTM()` to mirror the satellite's downlink gate:

```java
public void transmitRealtimeTM(PusTmPacket pkt) {
    if (pus14Service != null && !pus14Service.shouldForward(pkt)) {
        return;  // blocked by ST[14] configuration — emulating satellite-side gate
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

**Affects**: Simulator only — all subtypes
**Severity**: Medium (simulator scope)
**Effort**: Medium
**Layer**: **Simulator (on-board emulation)** — `Pus14Service.java` is a simulator class emulating satellite-side FCC management. No `yamcs-core` changes are needed.

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

**Affects**: Simulator only — TC[14,5], TC[14,6], TC[14,7], TM[14,8], TC[14,9]–TM[14,12]
**Severity**: Low (optional capabilities per spec)
**Layer**: **Simulator (on-board emulation)** — this gap is internal to the simulator's on-board logic. XTCE definitions for TC/TM are unaffected.

The HK and Diagnostic FCC features require that ST[03] (housekeeping) and ST[04] (diagnostic) services exist in the simulator and use structure identifiers. The PUS simulator currently implements ST[03] HK without structure IDs (sends periodic HK as a fixed APID/type/subtype combination).

**Implication**: TC[14,5/6/7/8] can be implemented as stubs that maintain the in-memory HK FCC table and respond to TC[14,7] with TM[14,8], but the actual filtering of ST[03] reports against the HK FCC is a no-op until ST[03] is updated to use structure IDs. This is a simulator-only gap; the XTCE definitions remain complete.

**Recommended initial approach**: Implement TC/TM for HK FCC management (XTCE + simulator Java), but leave the `shouldForwardHkReport(apid, structId)` check as a TODO. This is fully conformant — the capability is declared as optional.

---

## Summary Table

| Subtype | Dir | MCS: XTCE Coverage | Simulator Java (on-board emulation) | Effort | Notes |
|---------|-----|-------------------|-------------------------------------|--------|-------|
| TC[14,1] | TC | ✅ Single MetaCommand (N1/N2/N3 nested arrays) | ✅ Required (parse TC, update APFCC) | Medium | YAMCS supports sibling-member array size refs; N2=0/N3=0 encode "add all" |
| TC[14,2] | TC | ✅ 2 variants (delete-entries N1/N2/N3 + empty-APFCC no-arg) | ✅ Required (parse TC, update APFCC) | Medium | Delete entries reuses TC[14,1] nested array types; empty-APFCC is zero-byte discriminator |
| TC[14,3] | TC | ✅ Full (no args) | ✅ New Pus14Service (iterate APFCC, emit TM[14,4]) | Low | Identical to TC[11,17] pattern |
| TM[14,4] | TM | ✅ Full (nested container repeats) | ✅ Required (emit from APFCC) | Medium | 3-level nested XTCE structure; `CURRENT_ENTRY_WITHIN_PACKET` getFromEnd(0) picks most-recent N_subtypes per iteration |
| TC[14,5] | TC | ✅ Single MetaCommand (N1/N_structs nested) | ✅ Required (parse TC, update HK FCC) | Low | N_structs=0 = add all structs; same sibling-member array-size pattern as TC[14,1] |
| TC[14,6] | TC | ✅ 2 variants (delete-entries + empty-HK-FCC no-arg) | ✅ Required (parse TC, update HK FCC) | Low | Delete entries reuses hk_apid_array_type; empty-HK-FCC is zero-byte discriminator |
| TC[14,7] | TC | ✅ Full (no args) | ✅ New Pus14Service (iterate HK FCC, emit TM[14,8]) | Low | Same as TC[14,3] pattern |
| TM[14,8] | TM | ✅ Full | ✅ Required (emit from HK FCC) | Low | Flat 2-level; dynamic array; fully XTCE-expressible |
| TC[14,9] | TC | ✅ Single MetaCommand (N1/N_structs nested) | ✅ Required (parse TC, update Diag FCC) | Low | Mirror of TC[14,5] for diagnostic FCC; same design |
| TC[14,10] | TC | ✅ 2 variants (delete-entries + empty-diag-FCC no-arg) | ✅ Required (parse TC, update Diag FCC) | Low | Mirror of TC[14,6] for diagnostic FCC |
| TC[14,11] | TC | ✅ Full (no args) | ✅ New Pus14Service (iterate Diag FCC, emit TM[14,12]) | Low | Mirror of TC[14,7] |
| TM[14,12] | TM | ✅ Full | ✅ Required (emit from Diag FCC) | Low | Mirror of TM[14,8] for diagnostic FCC |

### Overall Verdict

**For the MCS scope (YAMCS ground segment): ST[14] is XTCE-only. No Java changes to `yamcs-core` are needed.**

All TC/TM packet structures for ST[14] are fully expressible in XTCE:

1. **All TC commands**: fully expressible as XTCE MetaCommands — single commands for TC[14,1], TC[14,3], TC[14,5], TC[14,7], TC[14,9], TC[14,11]; two variants for TC[14,2], TC[14,6], TC[14,10] (delete-entries + empty-FCC no-arg)
2. **All TM packets**: fully decodeable in XTCE — including TM[14,4]'s 3-level nested structure via `ContainerRefEntry` nested `RepeatEntry` with `CURRENT_ENTRY_WITHIN_PACKET` semantics
3. **YAMCS MCS role**: encode TC packets for uplink; decode TM dump reports from downlink. YAMCS performs no forwarding filtering of its own — that is entirely an on-board responsibility.

**For the simulator (on-board emulation)**: Java implementation is required to emulate the satellite's forwarding control logic:

3. **Forwarding interceptor**: one `PusSimulator.java` edit — inserting `shouldForward()` check inside `transmitRealtimeTM()` — a clean cross-cutting concern, not per-service changes
4. **New `Pus14Service.java`** — straightforward map/set operations for APFCC/HK FCC/Diag FCC; no timing or periodic tasks needed

**Required artifacts by layer:**

| Layer | Artifact | Purpose |
|-------|----------|---------|
| **MCS / YAMCS ground** | `mdb/pus14.xml` | XTCE TC encoding (TC[14,1/2/3/5/6/7/9/10/11]) and TM decoding (TM[14,4/8/12]) |
| **MCS / YAMCS ground** | `yamcs.pus.yaml` update | Load `mdb/pus14.xml` into the Mission Database |
| **Simulator (on-board emulation)** | `Pus14Service.java` | Emulates satellite: maintains APFCC/HK FCC/Diag FCC in memory; handles TC execution; emits TM dump reports; provides `shouldForward()` gate |
| **Simulator (on-board emulation)** | `PusSimulator.java` edit | Register `Pus14Service`; insert `shouldForward()` gate in `transmitRealtimeTM()` to emulate satellite downlink filtering |

> **Key finding**: All forwarding control logic (APFCC/HK FCC/Diag FCC management, `shouldForward()` gate, TC parsing, TM dump generation) lives in the simulator (on-board emulation). YAMCS MCS only encodes outgoing configuration TCs and decodes incoming FCC dump TM reports — both purely via XTCE. Zero changes to `yamcs-core` are required.

**Zero changes to existing service classes** (Pus5Service, Pus11Service, etc.) are required beyond the single interceptor hook in `PusSimulator.transmitRealtimeTM()`.

---

## d) Native MCS Implementation — Java vs XTCE-only

### Verdict: XTCE-only

ST[14] is **XTCE-only on the ground side**. No Java exists or is needed in `yamcs-core` for ST[14]. This is stated in §a):

> *YAMCS/MCS implementation = XTCE only (`pus14.xml`). No Java changes to `yamcs-core` are needed for ST[14].*

All TC sends are encoded as XTCE MetaCommands. All TM receives (TM[14,4], TM[14,8], TM[14,12]) are XTCE parameter containers. The on-board forwarding control logic (APFCC/HK FCC/Diag FCC management, `shouldForward()` gate) lives **entirely in the simulator** — it emulates satellite-side behavior. YAMCS MCS only sends configuration TCs and decodes FCC dump TM reports — both purely via XTCE.

---

### Per-message table (MCS ground side only)

| Message | MCS Role | XTCE Sufficient? | Java Required? | Notes |
|---------|----------|-----------------|----------------|-------|
| TC[14,1] | Send | **Yes** | No | Single MetaCommand with N1/N2/N3 nested arrays; N2=0/N3=0 encode "add all" |
| TC[14,2] | Send | **Yes** | No | 2 variants: delete-entries (reuses TC[14,1] types) + empty-APFCC (no args) |
| TC[14,3] | Send | **Yes** | No | No args — identical to TC[11,17] pattern |
| TM[14,4] | Receive | **Yes** | No | 3-level nested `ContainerRefEntry` repeats; `CURRENT_ENTRY_WITHIN_PACKET` picks most-recent `N_subtypes` per iteration |
| TC[14,5] | Send | **Yes** | No | Single MetaCommand, N1/N_structs 2-level nested; N_structs=0 = "add all" |
| TC[14,6] | Send | **Yes** | No | 2 variants: delete-entries + empty-HK-FCC (no args) |
| TC[14,7] | Send | **Yes** | No | No args |
| TM[14,8] | Receive | **Yes** | No | Flat 2-level dynamic array; fully XTCE-expressible |
| TC[14,9] | Send | **Yes** | No | Mirror of TC[14,5] for diagnostic FCC |
| TC[14,10] | Send | **Yes** | No | Mirror of TC[14,6] for diagnostic FCC |
| TC[14,11] | Send | **Yes** | No | No args; mirror of TC[14,7] |
| TM[14,12] | Receive | **Yes** | No | Mirror of TM[14,8] for diagnostic FCC |

---

### Contrast with ST[05] and ST[11]

| | ST[05] | ST[11] | ST[14] |
|--|--------|--------|--------|
| Native Java needed in yamcs-core? | **Yes** — `PusEventDecoder` | **No** | **No** |
| Why Java for TM? | TM[5,1–4] must be promoted to YAMCS native events (events stream) — no XTCE mechanism | N/A | N/A |
| Existing yamcs-core Java | `Pus5Service`, `PusEventDecoder` | `PusCommandPostprocessor.buildScheduledTc()` (already present) | None needed |
| XTCE for TC? | Yes | Yes | Yes |
| XTCE for TM? | Partial (params decoded, events need Java) | Full | Full |
| On-board Java (simulator only) | `Pus5Service` in simulator | `Pus11Service` in simulator | `Pus14Service` in simulator (to be created) |

---

### When would yamcs-core Java be needed?

Only if YAMCS itself acted as a forwarding filter — i.e., if YAMCS should gate TM packets before archiving them based on an APFCC. That is explicitly **not** the design here: the forwarding control table lives on-board (or in the simulator), and YAMCS MCS archives whatever packets the satellite chooses to downlink.

If a future requirement added MCS-side filtering (e.g., suppressing certain TM packets before they reach the parameter archive), a `PusTmFilter` service in `yamcs-core` would be needed. That is not a ST[14] requirement — it would be a YAMCS architectural extension.

---

## Implementation Files (when building)

| Layer | File | Action |
|-------|------|--------|
| **Simulator (on-board emulation)** | `simulator/src/main/java/org/yamcs/simulator/pus/Pus14Service.java` | Create — APFCC/HK FCC/Diag FCC data structures + TC handler + `shouldForward()` — emulates satellite-side forwarding control |
| **Simulator (on-board emulation)** | `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java` | Edit — register Pus14Service; add `shouldForward()` gate in `transmitRealtimeTM()` — emulates satellite downlink filtering |
| **MCS / YAMCS ground** | `examples/pus/src/main/yamcs/mdb/pus14.xml` | Create — XTCE containers + commands (3+4+2+3 MetaCommands, 4 SequenceContainers) — TC encoding and TM decoding only |
| **MCS / YAMCS ground** | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` | Edit — add `mdb/pus14.xml` to MDB list |

### Reference Files
- `simulator/src/main/java/org/yamcs/simulator/pus/AbstractPusService.java` — base class
- `simulator/src/main/java/org/yamcs/simulator/pus/PusTmPacket.java` — packet structure
- `examples/pus/src/main/yamcs/mdb/pus5.xml` — XTCE pattern reference
- `pus_simulator_architecture.md` — full architecture reference
