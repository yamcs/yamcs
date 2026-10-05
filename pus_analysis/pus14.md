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

### Architecture Files

| Layer | Purpose | Path |
|-------|---------|------|
| **Simulator (on-board emulation)** | Java service — APFCC/HK FCC/Diag FCC/Event Blocking FCC tables + `shouldForward()` gate | `simulator/src/main/java/org/yamcs/simulator/pus/Pus14Service.java` |
| **Simulator (on-board emulation)** | Register Pus14Service; add `shouldForward()` gate in `transmitRealtimeTM()` | `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java` |
| **MCS / YAMCS ground** | XTCE MDB (TC encoding + TM decoding) | `examples/pus/src/main/yamcs/mdb/pus14.xml` |
| **MCS / YAMCS ground** | Add MDB reference | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` |

---

## b) Required TM/TC — Context, Implementation Plan, XTCE vs Java

### Mission-Specific Field Sizes

| Field | Chosen type | Size |
|-------|-------------|------|
| `application_process_id` (APID) | uint11 | 11 bits (packed) |
| `service_type_id` | uint8 | 1 byte |
| `message_subtype_id` | uint8 | 1 byte |
| `hk_structure_id` | uint8 | 1 byte |
| `diagnostic_structure_id` | uint8 | 1 byte |
| `event_definition_id` | uint8 | 1 byte |
| Counts (N of items) | uint8 | 1 byte |

The Service 14 application/source-data APID is deliberately treated as a packed 11-bit field in
this implementation. The field that follows it starts immediately at the next bit; there is no
implicit five-bit pad to make the APID occupy two bytes. Consequently, all remaining fields in the
same nested entry can be non-byte-aligned. Only the complete application/source-data area is rounded
up to a whole number of octets, with unused trailing bits left as zero. The simulator therefore uses
`BitBuffer` for every Service 14 field after the first APID, rather than mixing bit and byte access.

**Cross-service audit (not changed by this Service 14 work)**: 16-bit APID assumptions also appear
in `pus11.md` (scheduled-command request/report identifiers), `pus15.md` (packet-store filters and
reports), `pus17.md` (the proposed targeted connection test), and `pus_simulator_architecture.md`
(an aggregate command example). Those uses need separate service-specific review; this change does
not silently reinterpret their wire formats.

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
    apid (uint11, packed)
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
        <Member name="apid"          typeRef="/dt/pus_report_apid"/>
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

**Simulator (on-board emulation)** (`case 1 → addReportTypes(tc)`) — emulates satellite-side APFCC update. All fields are read from one continuous MSB-first bit stream:
```java
BitBuffer bits = bitBuffer(tc.getUserDataBuffer());
int n1 = (int) bits.getBits(8);
for (int i = 0; i < n1; i++) {
    int apid = (int) bits.getBits(11);
    int n2 = (int) bits.getBits(8);
    if (n2 == 0) {
        // Spec §8.14.2.1c: N2=0 → add all services for this APID
        apfcc.computeIfAbsent(apid, k -> new ApfcDefinition(k));
        // Empty serviceSubtypes map = "pass all" (see shouldForward logic)
    } else {
        ApfcDefinition apfcd = apfcc.computeIfAbsent(apid, k -> new ApfcDefinition(k));
        for (int j = 0; j < n2; j++) {
            int svcType = (int) bits.getBits(8);
            int n3 = (int) bits.getBits(8);
            if (n3 == 0) {
                // Spec §8.14.2.1d: N3=0 → add all subtypes of this service type
                apfcd.serviceSubtypes.computeIfAbsent(svcType, k -> new LinkedHashSet<>());
                // Empty set = "pass all subtypes" for this service
            } else {
                Set<Integer> subtypes = apfcd.serviceSubtypes
                    .computeIfAbsent(svcType, k -> new LinkedHashSet<>());
                for (int k = 0; k < n3; k++) {
                    subtypes.add((int) bits.getBits(8));
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

<!-- TC[14,2]b — Empty the entire APFCC (operator-facing no-argument alias) -->
<MetaCommand name="TC_14_2_EMPTY_APFCC"
             shortDescription="TC[14,2] Empty entire APFC configuration">
    <BaseMetaCommand metaCommandRef="pus14-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="2"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <CommandContainer name="TC_14_2_EMPTY">
        <EntryList>
            <FixedValueEntry name="N1" binaryValue="00" sizeInBits="8"/>
        </EntryList>
        <BaseContainer containerRef="pus14-tc"/>
    </CommandContainer>
</MetaCommand>
```

**Simulator (on-board emulation)** (`case 2 → deleteReportTypes(bb)`) — emulates satellite-side APFCC deletion:
```java
BitBuffer bits = bitBuffer(tc.getUserDataBuffer());
int n1 = (int) bits.getBits(8);
if (n1 == 0) {
    // Empty-APFCC variant
    apfcc.clear();
    ack_completion(tc);
    return;
}
for (int i = 0; i < n1; i++) {
    int apid = (int) bits.getBits(11);
    int n2 = (int) bits.getBits(8);
    ApfcDefinition apfcd = apfcc.get(apid);
    if (apfcd == null) { nack(tc, 1, 4); return; }  // APID not in APFCC
    if (n2 == 0) {
        apfcc.remove(apid);  // Remove entire APFCD
    } else {
        for (int j = 0; j < n2; j++) {
            int svcType = (int) bits.getBits(8);
            int n3 = (int) bits.getBits(8);
            if (n3 == 0) {
                apfcd.serviceSubtypes.remove(svcType);  // Remove entire STFCD
                if (apfcd.serviceSubtypes.isEmpty()) apfcc.remove(apid);
            } else {
                Set<Integer> subtypes = apfcd.serviceSubtypes.get(svcType);
                if (subtypes == null) { nack(tc, 1, 4); return; }
                for (int k = 0; k < n3; k++) {
                    subtypes.remove((int) bits.getBits(8));
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

**Empty configuration**: The empty-APFCC instruction is encoded as the standard one-byte `N1=0`
payload. The operator-facing `TC_14_2_EMPTY_APFCC` alias has no arguments but inserts that fixed
octet in its XTCE command container. `TC_14_2_DELETE_ENTRIES` can also express the same wire form
with `N1=0` and an empty array.

---

### TC[14,3] — Report the Content of the Application Process Forward-Control Configuration

**Spec**: §6.14.3.4.3
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite emits one TM[14,4] containing all APFCDs. **[SAT → GROUND]** — the report is decoded by YAMCS via XTCE.

**Purpose**: Request a dump of the entire APFCC. No application data — zero-argument TC. Response:
one atomic TM[14,4] containing every APFCD in the table.

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

**Simulator (on-board emulation)** (`case 3 → reportApfcc(tc)`): Preflights the complete APFCC and
emits one TM[14,4] containing every APFCD. If the complete report exceeds a count field or the
simulator TM data limit, it emits no partial report and returns a completion failure.

**Gaps**: None. Standard no-argument TC.

---

### TM[14,4] — Application Process Forward-Control Configuration Content Report

**Spec**: §6.14.3.4.3
**Direction**: **[SAT → GROUND]** — generated on-board in response to TC[14,3]; decoded by YAMCS MCS via XTCE for display only.

**Purpose**: One packet containing every APFCD (application process), with the full hierarchy of
service types and subtypes allowed for forwarding. `N1=0` reports an empty APFCC.

**Packet structure**:
```
[N1: uint8]
  repeated N1 times:
    [apid: uint11, packed]
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

<!-- One APID group: apid + N_services + N_services×service_element -->
<SequenceContainer name="apfc_apid_element">
    <EntryList>
        <ParameterRefEntry parameterRef="apfc_apid"/>
        <ParameterRefEntry parameterRef="apfc_N_services"/>
        <ContainerRefEntry containerRef="apfc_service_element">
            <RepeatEntry><Count><DynamicValue>
                <ParameterInstanceRef parameterRef="apfc_N_services"/>
            </DynamicValue></Count></RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
</SequenceContainer>

<!-- Complete report: N1 + N1×APID group -->
<SequenceContainer name="TM_14_4" shortDescription="TM[14,4] APFC config content report">
    <EntryList>
        <ParameterRefEntry parameterRef="apfc_N_apids"/>
        <ContainerRefEntry containerRef="apfc_apid_element">
            <RepeatEntry>
                <Count>
                    <DynamicValue>
                        <ParameterInstanceRef parameterRef="apfc_N_apids"/>
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

**Simulator (on-board emulation)** — the payload size includes the outer count and every APID group:
```java
long bitSize = 8; // N1
for (ApfcDefinition apfcd : apfcc.values()) {
    bitSize += 11 + 8;
    for (StfcDefinition stfc : apfcd.services()) {
        bitSize += 8 + 8 + stfc.subtypes().size() * 8L;
    }
}
PusTmPacket pkt = newPacket(4, (bitSize + 7) / 8);
BitBuffer bits = bitBuffer(pkt.getUserDataBuffer());
bits.putBits(apfcc.size(), 8);
for (ApfcDefinition apfcd : apfcc.values()) {
    bits.putBits(apfcd.apid(), 11);
    bits.putBits(apfcd.services().size(), 8);
    for (StfcDefinition stfc : apfcd.services()) {
        bits.putBits(stfc.serviceType(), 8);
        bits.putBits(stfc.subtypes().size(), 8);
        for (int subtype : stfc.subtypes()) bits.putBits(subtype, 8);
    }
}
pusSimulator.transmitRealtimeTM(pkt);
```

**Gaps**:
- No XTCE decoding limitation — full 3-level structure is expressible via nested `ContainerRefEntry` repeats
- The complete configuration is atomic. If it cannot fit one report, the simulator returns
  `COMPL_ERR_REPORT_TOO_LARGE`; it never splits or truncates the configuration.
- APID groups are contiguous. Zero padding, if needed, occurs only after the last group at the end
  of the source-data field.

---

### TC[14,5] — Add Structure Identifiers to the HK Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.5.1
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite parses it, updates the HK FCC table, and issues ACK/NACK.

**Purpose**: Authorize specific housekeeping parameter report structures for forwarding. The packet carries N1 APID entries; each APID entry contains N_structs structure IDs to authorize. N_structs=0 means "authorize all structures for this APID" (spec §6.14.3.5.1 convention, same as TC[14,1]'s N2=0).

**Packet layout**:
```
N1 (uint8) — number of application process entries
  repeated N1 times:
    apid (uint11, packed)
    N_structs (uint8)    ← 0 = "add all HK structures for this APID"
    repeated N_structs times:
      hk_structure_id (uint8)
```

**XTCE**: ✅ **Single MetaCommand** — 2-level nested arrays (N1 outer APIDs, N_structs inner struct IDs per APID), using the same sibling-member array-size reference pattern as TC[14,1].

```xml
<!-- Inner: array of N_structs HK structure IDs; N_structs is a sibling member -->
<ArrayArgumentType name="hk_struct_id_array_type" arrayTypeRef="/dt/uint8">
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
        <Member name="apid"       typeRef="/dt/pus_report_apid"/>
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

**Simulator (on-board emulation)** (`case 5 → addHkStructIds(bb)`) — emulates satellite-side HK FCC update. The 8-bit structure identifiers remain packed immediately after the preceding fields even when their starting bit is not byte-aligned:
```java
BitBuffer bits = bitBuffer(bb);
int n1 = (int) bits.getBits(8);
for (int i = 0; i < n1; i++) {
    int apid = (int) bits.getBits(11);
    int nStructs = (int) bits.getBits(8);
    if (nStructs == 0) {
        // N_structs=0: add all HK structures for this APID
        hkFcc.put(apid, null);  // null = pass-all mode
    } else {
        Set<Integer> structs = hkFcc.computeIfAbsent(apid, k -> new LinkedHashSet<>());
        for (int j = 0; j < nStructs; j++) {
            structs.add((int) bits.getBits(8));
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
        <EntryList>
            <FixedValueEntry name="N1" binaryValue="00" sizeInBits="8"/>
        </EntryList>
        <BaseContainer containerRef="pus14-tc"/>
    </CommandContainer>
</MetaCommand>
```

**Simulator (on-board emulation)** (`case 6 → deleteHkStructIds(bb)`) — emulates satellite-side HK FCC deletion:
```java
BitBuffer bits = bitBuffer(tc.getUserDataBuffer());
int n1 = (int) bits.getBits(8);
if (n1 == 0) {
    hkFcc.clear();
    ack_completion(tc);
    return;
}
for (int i = 0; i < n1; i++) {
    int apid = (int) bits.getBits(11);
    int nStructs = (int) bits.getBits(8);
    if (nStructs == 0) {
        hkFcc.remove(apid);  // Delete entire APID entry
    } else {
        Set<Integer> structs = hkFcc.get(apid);
        if (structs == null) { nack(tc, 1, 4); return; }
        for (int j = 0; j < nStructs; j++) {
            structs.remove((int) bits.getBits(8));
        }
        if (structs.isEmpty()) hkFcc.remove(apid);
    }
}
ack_completion(tc);
```

**Rejection conditions**: APID not in HK FCC; struct ID not in definition for that APID.

**Empty configuration**: Both command variants use the standard `N1` field. The no-argument alias
inserts `N1=0`; the delete-entries command carries a caller-supplied `N1` and APID array.

---

### TC[14,7] — Report the Content of the HK Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.5.3
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite emits one TM[14,8] containing all HK FCC definitions. **[SAT → GROUND]** — TM[14,8] is decoded by YAMCS via XTCE.

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

**Simulator (on-board emulation)** (`case 7 → reportFcc(tc, hkFcc, 8)`): Preflights and emits one
atomic report containing all APID entries.

**Gaps**: None.

---

### TM[14,8] — HK Parameter Report Forward-Control Configuration Content Report

**Spec**: §6.14.3.5.3
**Direction**: **[SAT → GROUND]** — generated on-board in response to TC[14,7]; decoded by YAMCS MCS via XTCE for display only.

**Purpose**: One packet containing every HK FCC APID definition. `N1=0` reports an empty HK FCC.

**Packet structure**:
```
[N1: uint8]
  repeated N1 times:
    [apid: uint11, packed]
    [N_structs: uint8]
      repeated N_structs times:
        [hk_structure_id: uint8]
        [subsampling_rate: uint8]  ← optional; omitted by this simulator
```

**XTCE**: ✅ **Fully implementable** — this is an outer APID repeat whose entries contain a
dynamic array of 8-bit structure identifiers. Each entry can start at a non-byte-aligned bit
position after the packed APID and count.

```xml
<!-- ParameterTypeSet -->
<IntegerParameterType name="hk_struct_id_type" signed="false">
  <IntegerDataEncoding sizeInBits="8"/>
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
<SequenceContainer name="hk_fcc_apid_element">
  <EntryList>
    <ParameterRefEntry parameterRef="hk_fcc_apid"/>
    <ParameterRefEntry parameterRef="hk_fcc_n_structs"/>
    <ParameterRefEntry parameterRef="hk_fcc_struct_ids">
      <IncludeCondition>
        <Comparison parameterRef="hk_fcc_n_structs" comparisonOperator="&gt;" value="0"/>
      </IncludeCondition>
    </ParameterRefEntry>
  </EntryList>
</SequenceContainer>

<SequenceContainer name="TM_14_8" shortDescription="TM[14,8] HK FCC content report">
  <EntryList>
    <ParameterRefEntry parameterRef="hk_fcc_n_apids"/>
    <ContainerRefEntry containerRef="hk_fcc_apid_element">
      <RepeatEntry><Count><DynamicValue>
        <ParameterInstanceRef parameterRef="hk_fcc_n_apids"/>
      </DynamicValue></Count></RepeatEntry>
    </ContainerRefEntry>
  </EntryList>
  <BaseContainer containerRef="pus14-tm">
    <RestrictionCriteria>
      <Comparison parameterRef="/PUS/subtype" value="8"/>
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>
```

**Simulator (on-board emulation)** — one bitstream contains the outer count and every APID group:
```java
long bitSize = 8;
for (Set<Integer> structIds : hkFcc.values()) {
    bitSize += 11 + 8 + structIds.size() * 8L;
}
PusTmPacket pkt = newPacket(8, (bitSize + 7) / 8);
BitBuffer bits = bitBuffer(pkt.getUserDataBuffer());
bits.putBits(hkFcc.size(), 8);
for (var entry : hkFcc.entrySet()) {
    bits.putBits(entry.getKey(), 11);
    bits.putBits(entry.getValue().size(), 8);
    for (int sid : entry.getValue()) bits.putBits(sid, 8);
}
pusSimulator.transmitRealtimeTM(pkt);
```

**Gaps**: If subsampling rates are added later, each entry becomes `uint8 + uint8` (aggregate type)
— a minor extension. The entry may start at a non-byte boundary and must remain in the same packed
bit stream.

---

### TC[14,9] — Add Structure Identifiers to the Diagnostic Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.6.1
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite parses it, updates the Diagnostic FCC table, and issues ACK/NACK.

**Purpose**: Identical structure and semantics to TC[14,5] but for diagnostic parameter reports (ST[04] structures).

**XTCE**: ✅ **Single MetaCommand** — identical N1/N_structs nested array design as TC[14,5], using `diag_apid_array_type` (mirrors `hk_apid_array_type` with `diag_struct_id` uint8 elements). N_structs=0 = add all diagnostic structures for that APID.

```xml
<!-- Reuse same aggregate+array pattern as TC[14,5], renaming types for clarity -->
<ArrayArgumentType name="diag_struct_id_array_type" arrayTypeRef="/dt/uint8">
    <!-- same DimensionList as hk_struct_id_array_type, argumentRef="N_structs" -->
    ...
</ArrayArgumentType>
<AggregateArgumentType name="diag_apid_entry_type">
    <MemberList>
        <Member name="apid"       typeRef="/dt/pus_report_apid"/>
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

**Empty configuration**: The no-argument alias inserts the standard `N1=0` octet. The
delete-entries command uses the same `N1` field and can also encode an empty configuration request.

---

### TC[14,11] — Report the Content of the Diagnostic Parameter Report Forward-Control Configuration

**Spec**: §6.14.3.6.3
**Direction**: **[GROUND → SAT]** — YAMCS MCS encodes and uplinks this TC via XTCE. **[ON-BOARD]** — satellite emits one TM[14,12] containing all Diagnostic FCC definitions. **[SAT → GROUND]** — TM[14,12] is decoded by YAMCS via XTCE.

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

**Simulator (on-board emulation)** (`case 11 → reportFcc(tc, diagFcc, 12)`): Preflights and emits
one atomic report containing all APID entries.

**Gaps**: None.

---

### TM[14,12] — Diagnostic Parameter Report Forward-Control Configuration Content Report

**Spec**: §6.14.3.6.3
**Direction**: **[SAT → GROUND]** — generated on-board in response to TC[14,11]; decoded by YAMCS MCS via XTCE for display only.

**Purpose**: Identical structure to TM[14,8] but for diagnostic structure identifiers. `N1=0`
reports an empty Diagnostic FCC.

**Packet structure**:
```
[N1: uint8]
  repeated N1 times:
    [apid: uint11, packed]
    [N_structs: uint8]
      repeated N_structs times:
        [diag_structure_id: uint8]
```

**XTCE**: ✅ **Same as TM[14,8]** — an outer repeated APID container with an inner dynamic array
whose size resolves from the most recently decoded `N_structs`.

**Simulator (on-board emulation)**: Uses the same atomic packed-bit report builder as TM[14,8],
with subtype 12 and `diagFcc` as the source table.

**Gaps**: None.

---

### TC[14,13] — Delete Event Definition Identifiers from the Event Report Blocking FCC

**Spec**: §6.14.3.7.1

**Packet layout**:
```
N1 (uint8)
  repeated N1 times:
    apid (uint11, packed)
    N2 (uint8)                 ← 0 = delete the APID blocking definition
    event_definition_id[N2]   (uint8 each)
```

`N1=0` empties the complete event-blocking configuration. XTCE exposes a nested-array delete
command and a no-argument convenience alias that inserts the fixed `N1=0` octet. The simulator
rejects unknown APIDs and event IDs with distinct completion errors.

### TC[14,14] — Add Event Definition Identifiers to the Event Report Blocking FCC

**Spec**: §6.14.3.7.2

The layout is the same as TC[14,13]. `N2=0` creates an APID definition with no explicit IDs, which
means block every event definition for that APID. A populated list blocks only the listed event
IDs. Adding explicit IDs to an existing block-all definition is rejected instead of silently
narrowing it.

### TC[14,15] / TM[14,16] — Report the Event Report Blocking FCC

**Spec**: §6.14.3.7.3

TC[14,15] has no application data. Its single atomic response is:
```
N1 (uint8)
  repeated N1 times:
    apid (uint11, packed)
    N2 (uint8)
    event_definition_id[N2] (uint8 each)
```

`N1=0` reports an empty event-blocking configuration; `N2=0` reports a block-all definition for
the related APID. The MDB decodes it with an outer repeated APID container and a dynamic inner ID
array. The simulator applies this table only to ST[05] event report subtypes 1–4, after the APFCC
gate has authorized the packet. It reads the event definition ID from the first source-data octet.

As with TM[14,4], [14,8], and [14,12], a report that cannot fit one packet produces no partial
TM[14,16] and completes TC[14,15] with `COMPL_ERR_REPORT_TOO_LARGE`.

---

## c) Gaps & Shortcomings Summary

### Gap 1: Empty-Configuration Command Encoding — Resolved

**Affects**: TC[14,2], TC[14,6], TC[14,10], TC[14,13]
**Severity**: None — fully resolved

TC[14,1] is fully expressible as a **single MetaCommand** using YAMCS's nested dynamic array support (`array-in-array-arg.xml` confirms that `ArgumentInstanceRef` in an `ArrayArgumentType` can reference a sibling member of the containing `AggregateArgumentType`). The N1/N2/N3 structure with N2=0 (all services) and N3=0 (all subtypes) covers all three spec instruction forms in one command.

Every delete request carries `N1`. The empty-table wire form is the single octet `N1=0`. Separate
no-argument MetaCommands remain as operator conveniences, but each inserts that fixed octet rather
than producing a zero-byte application-data field.

**Impact**: The delete-entries commands and convenience aliases now encode the same standard wire
shape for an empty configuration.

---

### Gap 2: TM[14,4] — Resolved

**Affects**: TM[14,4]
**Severity**: None — fully resolved
**Effort**: None

TM[14,4]'s 3-level nested structure (APFCDs → STFCDs → RTFCDs) is fully expressible in XTCE using nested `ContainerRefEntry` + `RepeatEntry` containers. The outer repeat is driven by `N1`; each APID entry has its own `N2`, and each service entry has its own `N3`. `ParameterInstanceRef` resolves the most recently decoded count for each nested iteration.

---

### Gap 3: HK/Diag FCC TC Two-Form Requests — Resolved

**Affects**: TC[14,5], TC[14,6], TC[14,9], TC[14,10]
**Severity**: None — fully resolved

TC[14,5/9] use N1/N_structs nested arrays (`N_structs=0` = all structures for that APID).
TC[14,6/10] retain delete-entries and no-argument convenience commands, with the latter inserting
the standard `N1=0` octet.

---

### Gap 4: Forwarding Interceptor — Resolved

**Affects**: Simulator only — all other `Pus*Service` classes (Pus5Service, Pus11Service, etc.)
**Severity**: None — implemented
**Layer**: **Simulator (on-board emulation)** — this is entirely within the simulator; no YAMCS MCS or `yamcs-core` changes are needed.

ST[14]'s on-board forwarding filter is applied centrally inside
`PusSimulator.transmitRealtimeTM()` so individual services do not need forwarding hooks:

```java
public void transmitRealtimeTM(PusTmPacket pkt) {
    if (pus14Service != null && !pus14Service.shouldForward(pkt)) {
        return;  // blocked by ST[14] configuration — emulating satellite-side gate
    }
    // ... existing CRC append and send logic ...
}
```

`Pus14Service.shouldForward(PusTmPacket)` reads APID/type/subtype from the packet header, applies the
APFCC, then applies the event-blocking FCC to ST[05] event reports. Initial startup remains
**pass-all** for simulator usability rather than the standard's strict block-all state.

---

### Gap 5: Service 14 Simulator Implementation — Resolved

**Affects**: Simulator only — all subtypes
**Severity**: None — implemented
**Layer**: **Simulator (on-board emulation)** — `Pus14Service.java` is a simulator class emulating satellite-side FCC management. No `yamcs-core` changes are needed.

`Pus14Service.java` is implemented. Its key data structures are:

```java
// Application Process Forward-Control Configuration
Map<Integer, ApfcDefinition> apfcc = new LinkedHashMap<>();

// HK Forward-Control Configuration
Map<Integer, Set<Integer>> hkFcc = new LinkedHashMap<>();

// Diagnostic Forward-Control Configuration
Map<Integer, Set<Integer>> diagFcc = new LinkedHashMap<>();

// Event Report Blocking Forward-Control Configuration
Map<Integer, Set<Integer>> eventBlockingFcc = new LinkedHashMap<>();

// Inner class
class ApfcDefinition {
    int apid;
    // Empty map = pass all services; empty subtype set = pass all subtypes of that service.
    Map<Integer, Set<Integer>> serviceSubtypes = new LinkedHashMap<>();
}

public boolean shouldForward(PusTmPacket pkt) {
    int apid    = pkt.getAPID();
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
| TC[14,2] | TC | ✅ 2 variants (delete entries or fixed `N1=0` alias) | ✅ Implemented | Medium | Both variants retain the N1 field |
| TC[14,3] | TC | ✅ Full (no args) | ✅ Implemented | Low | Produces one atomic TM[14,4] |
| TM[14,4] | TM | ✅ Full (nested container repeats) | ✅ Implemented | Medium | `N1` APID groups, each with nested N2/N3 repeats |
| TC[14,5] | TC | ✅ Single MetaCommand (N1/N_structs nested) | ✅ Required (parse TC, update HK FCC) | Low | N_structs=0 = add all structs; same sibling-member array-size pattern as TC[14,1] |
| TC[14,6] | TC | ✅ 2 variants (delete entries or fixed `N1=0` alias) | ✅ Implemented | Low | Both variants retain the N1 field |
| TC[14,7] | TC | ✅ Full (no args) | ✅ Implemented | Low | Produces one atomic TM[14,8] |
| TM[14,8] | TM | ✅ Full | ✅ Implemented | Low | `N1` APID groups with uint8 structure-ID arrays |
| TC[14,9] | TC | ✅ Single MetaCommand (N1/N_structs nested) | ✅ Required (parse TC, update Diag FCC) | Low | Mirror of TC[14,5] for diagnostic FCC; same design |
| TC[14,10] | TC | ✅ 2 variants (delete-entries + empty-diag-FCC no-arg) | ✅ Required (parse TC, update Diag FCC) | Low | Mirror of TC[14,6] for diagnostic FCC |
| TC[14,11] | TC | ✅ Full (no args) | ✅ Implemented | Low | Produces one atomic TM[14,12] |
| TM[14,12] | TM | ✅ Full | ✅ Implemented | Low | `N1` APID groups with uint8 structure-ID arrays |
| TC[14,13] | TC | ✅ Delete entries + fixed `N1=0` alias | ✅ Implemented | Low | Event IDs are uint8; N2=0 removes an APID definition |
| TC[14,14] | TC | ✅ N1/N2 nested arrays | ✅ Implemented | Low | N2=0 blocks all events for the APID |
| TC[14,15] | TC | ✅ Full (no args) | ✅ Implemented | Low | Produces one atomic TM[14,16] |
| TM[14,16] | TM | ✅ Full | ✅ Implemented | Low | `N1` APID groups with uint8 event-ID arrays |

### Overall Verdict

**For the MCS scope (YAMCS ground segment): ST[14] is XTCE-only. No Java changes to `yamcs-core` are needed.**

All TC/TM packet structures for ST[14] are fully expressible in XTCE:

1. **All TC commands**: fully expressible as XTCE MetaCommands, including TC[14,13–15]. Empty-table convenience commands insert a fixed `N1=0` octet.
2. **All TM packets**: fully decodeable in XTCE — TM[14,4], [14,8], [14,12], and [14,16] all use an outer `N1`-driven APID repeat.
3. **YAMCS MCS role**: encode TC packets for uplink; decode TM dump reports from downlink. YAMCS performs no forwarding filtering of its own — that is entirely an on-board responsibility.

**For the simulator (on-board emulation)**: Java implementation is required to emulate the satellite's forwarding control logic:

3. **Forwarding interceptor**: one `PusSimulator.java` edit — inserting `shouldForward()` check inside `transmitRealtimeTM()` — a clean cross-cutting concern, not per-service changes
4. **`Pus14Service.java`** — map/set operations for APFCC/HK FCC/Diag FCC/Event Blocking FCC; no timing or periodic tasks needed

**Required artifacts by layer:**

| Layer | Artifact | Purpose |
|-------|----------|---------|
| **MCS / YAMCS ground** | `mdb/pus14.xml` | XTCE TC encoding (TC[14,1–3/5–7/9–11/13–15]) and TM decoding (TM[14,4/8/12/16]) |
| **MCS / YAMCS ground** | `yamcs.pus.yaml` update | Load `mdb/pus14.xml` into the Mission Database |
| **Simulator (on-board emulation)** | `Pus14Service.java` | Maintains all four FCCs; handles TC execution; emits atomic TM dump reports; provides `shouldForward()` gate |
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

## Implementation Files

| Layer | File | Action |
|-------|------|--------|
| **Simulator (on-board emulation)** | `simulator/src/main/java/org/yamcs/simulator/pus/Pus14Service.java` | Implemented — all four FCC data structures, TC handler, atomic reports, and `shouldForward()` |
| **Simulator (on-board emulation)** | `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java` | Edit — register Pus14Service; add `shouldForward()` gate in `transmitRealtimeTM()` — emulates satellite downlink filtering |
| **MCS / YAMCS ground** | `examples/pus/src/main/yamcs/mdb/pus14.xml` | Implemented — TC[14,1–3/5–7/9–11/13–15] encoding and TM[14,4/8/12/16] decoding |
| **MCS / YAMCS ground** | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` | Edit — add `mdb/pus14.xml` to MDB list |

### Reference Files
- `simulator/src/main/java/org/yamcs/simulator/pus/AbstractPusService.java` — base class
- `simulator/src/main/java/org/yamcs/simulator/pus/PusTmPacket.java` — packet structure
- `examples/pus/src/main/yamcs/mdb/pus5.xml` — XTCE pattern reference
- `pus_simulator_architecture.md` — full architecture reference

---

## e) Testing Methodology

Reflects the actual implementation: `Pus14Service.java` and
`examples/pus/src/main/yamcs/mdb/pus14.xml`. Command paths, argument names, and packed-bit layouts below
are taken directly from those files, not the pseudocode in section b).

### e.1 Start the instance

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests   # first build only
mvn -pl examples/pus yamcs:run
```
Web UI: `http://localhost:8090`, instance `pus`. Commands live under `/PUS14/...`, TM containers
under the same `/PUS14/` subsystem (see e.3).

### e.2 Command reference — valid inputs

All commands are under `/PUS14/`. The nested `N1`/`n2`/`n3`/`n_structs` count fields must be
supplied explicitly alongside their corresponding arrays — YAMCS does not infer them from array
length (same convention as PUS12's `N`/`pmon_ids`).

| Command | Subtype | Valid example args |
|---|---|---|
| `TC_14_1_ADD_REPORT_TYPES` (specific report type) | TC[14,1] | `{"N1": 1, "apfcd_entries": [{"apid": 1, "n2": 1, "service_types": [{"service_type": 3, "n3": 1, "subtypes": [25]}]}]}` — authorizes HK reports (type=3, subtype=25) for apid=1 |
| `TC_14_1_ADD_REPORT_TYPES` (all subtypes of a service) | TC[14,1] | `{"N1": 1, "apfcd_entries": [{"apid": 1, "n2": 1, "service_types": [{"service_type": 5, "n3": 0, "subtypes": []}]}]}` — authorizes all ST[05] event subtypes |
| `TC_14_1_ADD_REPORT_TYPES` (all services of an APID) | TC[14,1] | `{"N1": 1, "apfcd_entries": [{"apid": 1, "n2": 0, "service_types": []}]}` — authorizes everything for apid=1 (equivalent to pass-all) |
| `TC_14_2_DELETE_ENTRIES` | TC[14,2] | `{"N1": 1, "apfcd_entries": [{"apid": 1, "n2": 1, "service_types": [{"service_type": 3, "n3": 1, "subtypes": [25]}]}]}` — revokes just HK forwarding, leaving other authorized services intact |
| `TC_14_2_EMPTY_APFCC` | TC[14,2] | `{}` (no arguments) — clears the whole APFCC, reverting to pass-all default |
| `TC_14_3_REPORT_APFCC` | TC[14,3] | `{}` (no arguments) |
| `TC_14_5_ADD_HK_STRUCTS` | TC[14,5] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_structs": 1, "struct_ids": [100]}]}` |
| `TC_14_6_DELETE_HK_ENTRIES` | TC[14,6] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_structs": 1, "struct_ids": [100]}]}` |
| `TC_14_6_EMPTY_HK_FCC` | TC[14,6] | `{}` (no arguments) |
| `TC_14_7_REPORT_HK_FCC` | TC[14,7] | `{}` (no arguments) |
| `TC_14_9_ADD_DIAG_STRUCTS` | TC[14,9] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_structs": 1, "struct_ids": [200]}]}` |
| `TC_14_10_DELETE_DIAG_ENTRIES` | TC[14,10] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_structs": 1, "struct_ids": [200]}]}` |
| `TC_14_10_EMPTY_DIAG_FCC` | TC[14,10] | `{}` (no arguments) |
| `TC_14_11_REPORT_DIAG_FCC` | TC[14,11] | `{}` (no arguments) |
| `TC_14_13_DELETE_EVENT_ENTRIES` | TC[14,13] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_events": 1, "event_ids": [2]}]}` |
| `TC_14_13_EMPTY_EVENT_FCC` | TC[14,13] | `{}` (encodes fixed `N1=0`) |
| `TC_14_14_ADD_EVENT_ENTRIES` | TC[14,14] | `{"N1": 1, "apid_entries": [{"apid": 1, "n_events": 1, "event_ids": [2]}]}` |
| `TC_14_15_REPORT_EVENT_FCC` | TC[14,15] | `{}` (no arguments) |

Rejection conditions to exercise (all respond NACK completion, not NACK start — the command is
accepted then rejected during execution; see the `Pus14Service` completion error codes): deleting/
narrowing an APID not present in the APFCC (`COMPL_ERR_APID_NOT_IN_APFCC` = 5), deleting a service
type not present in that APFCD (`COMPL_ERR_SVC_NOT_IN_APFCD` = 6), deleting an APID not present in
the HK FCC (`COMPL_ERR_APID_NOT_IN_HK_FCC` = 7) or the Diagnostic FCC
(`COMPL_ERR_APID_NOT_IN_DIAG_FCC` = 8). An unrecognized subtype gets NACK **start**
(`START_ERR_INVALID_PUS_SUBTYPE`) instead, since the command is rejected before execution begins.
Event-FCC failures use codes 9–11, and an atomic report that exceeds a count or TM-size limit uses
`COMPL_ERR_REPORT_TOO_LARGE` = 12.

### e.3 TMs to check

| Container | Subtype | Triggered by | Layout |
|---|---|---|---|
| `/PUS14/TM_14_4` | TM[14,4] | `TC_14_3_REPORT_APFCC` | `N1:u8`, then N1 × `{apid:u11, n_services:u8, services…}`; all fields are contiguous bits |
| `/PUS14/TM_14_8` | TM[14,8] | `TC_14_7_REPORT_HK_FCC` | `N1:u8`, then N1 × `{apid:u11, n_structs:u8, struct_ids:u8[n_structs]}` |
| `/PUS14/TM_14_12` | TM[14,12] | `TC_14_11_REPORT_DIAG_FCC` | Same layout as TM[14,8], for the Diagnostic FCC |
| `/PUS14/TM_14_16` | TM[14,16] | `TC_14_15_REPORT_EVENT_FCC` | `N1:u8`, then N1 × `{apid:u11, n_events:u8, event_ids:u8[n_events]}` |

If any configuration is empty, its report TC produces exactly one TM packet whose source data is
the single octet `N1=0`.

Also watch the standard PUS-1 verification containers (`/PUS/pus-tc-ack-*`) for ACK/NACK
start/completion of every TC[14,x] above — see e.4 step 4 for why these should never silently
disappear regardless of APFCC configuration.

### e.4 Suggested manual test walkthrough

1. **Baseline (pass-all)**: with a freshly started instance and no TC[14,1] sent yet, confirm HK
   reports (type=3/subtype=25) and events (type=5) keep flowing normally — `apfcc` is empty, so
   `shouldForward()` returns `true` for every APID.
2. **Narrow the gate**: send `TC_14_1_ADD_REPORT_TYPES` with the "specific report type" args from
   e.2, authorizing *only* HK reports (type=3/subtype=25) for apid=1. Confirm HK reports keep
   arriving, but events (type=5) and diagnostic reports (type=3/subtype=26) stop.
3. **Verify the dump**: add two APID groups, send `TC_14_3_REPORT_APFCC`, and check that one
   `/PUS14/TM_14_4` reports `N1=2` followed by both packed APID definitions.
4. **Verify the PUS-1/ST14 exemption**: with the restrictive APFCC from step 2 still active (which
   does *not* authorize type=1 or type=14), confirm ACK/NACK verification reports for every command
   you send, and the `TM_14_4` report itself, still arrive — `shouldForward()` special-cases
   `type == 1 || type == 14` before consulting the APFCC at all.
5. **Widen back**: send `TC_14_1_ADD_REPORT_TYPES` again with the "all subtypes of a service" args
   from e.2 for service_type=5 (events), and confirm events resume while HK stays restricted to
   subtype 25 only — adding is additive, not a reset (see `addReportTypes`).
6. **Delete + rejection**: send `TC_14_2_DELETE_ENTRIES` targeting an `apid` never added (e.g. 2)
   and confirm a NACK completion with code 5 (`COMPL_ERR_APID_NOT_IN_APFCC`).
7. **Empty the table**: send `TC_14_2_EMPTY_APFCC` (no args) and confirm HK and events both return
   to flowing freely (pass-all default restored).
8. **HK/Diag FCC is bookkeeping only**: send `TC_14_5_ADD_HK_STRUCTS` then `TC_14_7_REPORT_HK_FCC`,
   confirm `/PUS14/TM_14_8` reflects the added struct id — but also confirm actual HK TM
   (type=3/subtype=25) is completely unaffected by this table (see Gap 6): forwarding is gated only
   by the APFCC, never by `hkFcc`/`diagFcc`.
9. **Event blocking**: add event ID 2 for APID 1 with `TC_14_14_ADD_EVENT_ENTRIES`; confirm event 2
   stops while event 1 continues, and confirm TM[14,16] reports the definition. Then send
   `TC_14_13_EMPTY_EVENT_FCC` and confirm both events flow again.

### e.5 Caveats specific to this simulator

- **Single fixed APID**: every TC and TM in this simulator uses `MAIN_APID = 1`
  (`PusSimulator.newPacket` / the `pus-tc` argument assignment), so in practice every APFCC/HK
  FCC/Diag FCC entry created during testing will have `apid=1`. The per-APID dimension of ST[14] is
  exercised structurally (both the XTCE and the Java support arbitrary APIDs) but not observably
  multi-APID in this environment.
- **TM[9,2] time packets bypass the gate entirely**: `Pus9Service` sends `PusTmTimePacket` via
  `pusSimulator.tmLink.sendImmediate(...)`, not `transmitRealtimeTM()`, so CUC time packets are
  never subject to any APFCC configuration — they keep flowing even under a fully restrictive setup.
- **HK FCC / Diagnostic FCC don't gate anything yet**: only the APFCC (TC[14,1-4]) is consulted by
  `shouldForward()`. TC[14,5-12] maintain real in-memory tables and answer real dump reports, but
  have no effect on which TM actually reaches the ground until ST[03]/ST[04] carry structure IDs
  that `shouldForward()` could cross-check against (see Gap 6).
