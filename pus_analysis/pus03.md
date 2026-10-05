# PUS ST[03] — Housekeeping Service
> ECSS-E-ST-70-41C §6.3 (pp. 78–110) and §8.3 (pp. 456–462+)

---

## Contents

| § | Section |
|---|---------|
| A | General Context |
| B | Complete TM/TC Message Catalogue |
| C | Per-Message Implementation Analysis |
| — | · TM[3,25] / TM[3,26] — HK/Diagnostic parameter reports (hardest) |
| — | · TM[3,10] / TM[3,12] — Structure reports |
| — | · TM[3,35] / TM[3,36] / TM[3,41] — Properties / definition reports |
| — | · TC[3,1/2] — Create structure |
| — | · TC[3,3/4] — Delete structure |
| — | · TC[3,5/6/7/8] — Enable/Disable periodic generation |
| — | · TC[3,9/11] — Request structure report |
| — | · TC[3,27/28] — One-shot reports |
| — | · TC[3,29/30] — Append parameters |
| — | · TC[3,31/32] — Modify collection interval |
| — | · TC[3,33/34] — Report periodic generation properties |
| — | · TC[3,37–44] — Parameter functional reporting configuration |
| D | XTCE / MDB Design Patterns |
| E | Native Java Service Design (Pus3Service) |
| F | Simulator Design (Pus3Service.java) |
| G | Integration Testing |
| H | Gaps and Shortcomings |
| I | Summary Table |

---

## A. General Context

### What is ST[03]?

PUS Service 3 (Housekeeping Service) provides control and adaptation of the spacecraft reporting plan. It assembles on-board parameter values into structured reports and downlinks them periodically or on demand.

**Three sub-services:**

| Sub-service | Purpose | Nominal/Contingency |
|-------------|---------|---------------------|
| Housekeeping reporting | Collect parameters into HK report structures; transmit periodically or on-request | Nominal ops |
| Diagnostic reporting | Same as HK but for contingency/anomaly investigation | Contingency |
| Parameter functional reporting configuration (PFRC) | Controls which HK/diag reports are active; allows mode-based switching | Mode management |

### Key Concepts

| Concept | Description |
|---------|-------------|
| **HK parameter report structure** | A named definition: collection interval + ordered list of simply-commutated parameters + ordered list of super-commutated parameter sets |
| **Simply commutated parameter** | One sample per collection interval |
| **Super commutated parameter set** | Multiple samples per collection interval; set has a repetition number N, sampled at interval/N sub-period |
| **Collection interval** | Integer multiple of minimum sampling interval; rate at which the structure is evaluated and reported |
| **Periodic generation action status** | `enabled` / `disabled` per structure; enables/disables periodic TM[3,25] or TM[3,26] for that structure |
| **Parameter functional reporting definition (PFRD)** | Named group of (report_def_id, type, enabled_status, collection_interval) entries; applied atomically to switch all referenced reports at once |

### Architecture Role — YAMCS as MCS (Ground Station Only)

**YAMCS is the ground station.** It does NOT run the on-board HK collection software. The spacecraft on-board software:
- Manages HK structure definitions
- Samples on-board parameters at the configured rates
- Transmits TM[3,25] / TM[3,26] periodically or on-request

YAMCS (MCS) role:
- **Sends TCs** (TC[3,1–44]) to the spacecraft to configure the HK service
- **Receives and decodes TMs** (TM[3,10/12/25/26/35/36/41]) from the spacecraft

This is the same deployment model as ST[02]. **No on-board simulation logic runs in yamcs-core.** However, unlike ST[02], ST[03] has a critically harder problem on the **TM receive side**: `TM[3,25/26]` payloads are structure-dependent and cannot be decoded without knowing the structure definition.

### The Central TM Decoding Problem

```
TM[3,25] = [ struct_id | param_value_1 | param_value_2 | ... ]
```

The number, types, and order of `param_value_N` fields are declared in the housekeeping parameter report structure identified by `struct_id`. They are "deduced" — the PUS packet is not self-describing.

Two deployment scenarios:

| Scenario | Description | XTCE approach |
|----------|-------------|---------------|
| **Static structures** (predefined at mission design time, never changed during ops) | Structure definitions are fixed; type/order of values per struct_id is known at MDB compile time | One `SequenceContainer` per struct_id with hard-coded field list |
| **Dynamic structures** (created/modified at runtime via TC[3,1/2/29/31]) | Structure content changes during operations | XTCE **cannot** adapt at runtime; requires native Java decoder |

**For Pixxel's deployment**: The typical pattern is to predefine all HK structures before launch. Dynamic creation (TC[3,1/2]) is used during AIVT or contingency, not routine ops. The recommended approach is:
1. **Use static XTCE containers** for all predefined HK structures (covers the nominal TM flow).
2. If dynamic structures are required, implement a **native `Pus3HkDecoder`** Java class (instance-level service) that tracks the live structure registry and decodes incoming TM[3,25/26] packets.

---

## B. Complete TM/TC Message Catalogue

### Housekeeping Reporting Sub-service

| Subtype | Dir | Name | Spec §8.3 |
|---------|-----|------|-----------|
| 1 | TC | Create housekeeping parameter report structure | 8.3.2.1 |
| 3 | TC | Delete housekeeping parameter report structures | 8.3.2.3 |
| 5 | TC | Enable periodic generation of HK parameter reports | 8.3.2.5 |
| 6 | TC | Disable periodic generation of HK parameter reports | 8.3.2.6 |
| 9 | TC | Report housekeeping parameter report structures | 8.3.2.9 |
| 10 | TM | Housekeeping parameter report structure report | 8.3.2.10 |
| 25 | TM | Housekeeping parameter report | 8.3.2.13 |
| 27 | TC | Generate one-shot report for HK parameter report structures | — |
| 29 | TC | Append parameters to a HK parameter report structure | — |
| 31 | TC | Modify collection interval of HK parameter report structures | — |
| 33 | TC | Report periodic generation properties of HK parameter report structures | — |
| 35 | TM | HK parameter report periodic generation properties report | — |

### Diagnostic Reporting Sub-service

| Subtype | Dir | Name | Spec §8.3 |
|---------|-----|------|-----------|
| 2 | TC | Create diagnostic parameter report structure | 8.3.2.2 |
| 4 | TC | Delete diagnostic parameter report structures | 8.3.2.4 |
| 7 | TC | Enable periodic generation of diagnostic parameter reports | 8.3.2.7 |
| 8 | TC | Disable periodic generation of diagnostic parameter reports | 8.3.2.8 |
| 11 | TC | Report diagnostic parameter report structures | 8.3.2.11 |
| 12 | TM | Diagnostic parameter report structure report | 8.3.2.12 |
| 26 | TM | Diagnostic parameter report | 8.3.2.14 |
| 28 | TC | Generate one-shot report for diagnostic parameter report structures | — |
| 30 | TC | Append parameters to a diagnostic parameter report structure | — |
| 32 | TC | Modify collection interval of diagnostic parameter report structures | — |
| 34 | TC | Report periodic generation properties of diagnostic parameter report structures | — |
| 36 | TM | Diagnostic parameter report periodic generation properties report | — |

### Parameter Functional Reporting Configuration Sub-service

| Subtype | Dir | Name |
|---------|-----|------|
| 37 | TC | Apply parameter functional reporting configurations |
| 38 | TC | Create parameter functional reporting definition |
| 39 | TC | Delete parameter functional reporting definitions |
| 40 | TC | Report parameter functional reporting definitions |
| 41 | TM | Parameter functional reporting definition report |
| 42 | TC | Add parameter report definitions to a PFRD |
| 43 | TC | Remove parameter report definitions from a PFRD |
| 44 | TC | Modify periodic generation properties of report defs in a PFRD |

---

## C. Per-Message Implementation Analysis

### TM[3,25] — Housekeeping parameter report (Critical Path)

**Packet structure (§8.3.2.13, Figure 8-33):**

```
[ struct_id (uint16) | param_value_1 | param_value_2 | ... ]
          ↑
  "deduced repeated number of times" — depends on the structure definition
```

**MCS role**: Receive and decode from spacecraft.

**XTCE approach — static predefined structures:**

```xml
<!-- Base container for all HK reports (type==3, subtype==25) -->
<SequenceContainer name="pus3-hk-tm">
    <EntryList>
        <ParameterRefEntry parameterRef="hk_struct_id"/>
    </EntryList>
    <BaseContainer containerRef="/PUS/pus-tm">
        <RestrictionCriteria>
            <Comparison parameterRef="/PUS/type"    value="3"  />
            <Comparison parameterRef="/PUS/subtype" value="25" />
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>

<!-- One container per predefined HK structure, discriminated by hk_struct_id -->
<SequenceContainer name="hk-report-struct-1"
                   shortDescription="HK report structure 1 — power bus">
    <EntryList>
        <!-- Simply commutated parameters in declaration order -->
        <ParameterRefEntry parameterRef="bus_voltage"/>
        <ParameterRefEntry parameterRef="bus_current"/>
        <ParameterRefEntry parameterRef="battery_temp"/>
        <!-- Super commutated parameters: NFA groups interleaved by repetition -->
        <!-- If super-commuted: NFA repetitions are flattened in the byte stream -->
        <!-- Declare as repeated parameter entries to match the sample layout     -->
    </EntryList>
    <BaseContainer containerRef="pus3-hk-tm">
        <RestrictionCriteria>
            <Comparison parameterRef="hk_struct_id" value="1"/>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Constraint**: One container per struct_id. Every predefined structure requires its own XTCE container. Super-commutated sets appear in the byte stream as `NFA` repetitions of `(param_A, param_B)` interleaved — model each repetition as a flat sequence of parameter fields.

**XTCE feasibility**: ✅ Full for predefined/static structures. ❌ Cannot handle dynamically created structures.

**Java required?**: Only if dynamic structure creation via TC[3,1/2] is required during operations. In that case, implement a `Pus3HkDecoder` (instance-level service, similar to `PusEventDecoder`) that maintains the live structure registry and decodes TM[3,25/26] packets into YAMCS parameters at runtime.

---

### TM[3,26] — Diagnostic parameter report

**Same as TM[3,25]** but for diagnostic structures (subtype == 26). Uses identical packet layout (Figure 8-34). Same XTCE approach: one container per diagnostic struct_id, base container restricted on type==3 + subtype==26.

---

### TM[3,10] — Housekeeping parameter report structure report

**Packet structure (§8.3.2.10, Figure 8-30):**

```
struct_id (enum) | periodic_gen_action_status (enum, optional) | collection_interval (uint) |
N1 (uint) | parameter_id × N1 (enum) |
NFA (uint) |
  [ super_commutated_sample_repetition_number (uint) | N2 (uint) | parameter_id × N2 (enum) ] × NFA
```

**MCS role**: Receive structure definitions reported by spacecraft in response to TC[3,9].

**XTCE approach**: Complex nested variable-length structure. Use `ContainerRefEntry` + `RepeatEntry` pattern (same as ST[14] TM multi-level patterns).

```
Level 1: pus3-hk-struct-report: struct_id + periodic_status + collection_interval + N1 + N1×param_id + NFA
Level 2: inner-sc-set: repetition_number + N2 + N2×param_id
```

Two-level nesting: outer repeats N1 times for simply-commutated params (flat), inner repeats NFA times for super-commutated sets.

For the N1 simply-commutated block: since it's a flat array of a single enumerated type, use `ArrayParameterType` with a dynamic dimension referencing `N1`.

For the NFA super-commutated sets: use `ContainerRefEntry` + `RepeatEntry` on a sub-container, with the inner count resolved from the most-recently-decoded `N2` value.

**XTCE feasibility**: ✅ Fully expressible. Effort: medium (requires multi-level nested XTCE patterns). No Java needed.

**Important**: `periodic_gen_action_status` is marked "optional" in Figure 8-30 — present only if the sub-service provides the capability for managing periodic generation. In XTCE, use an `IncludeCondition` or pre-agree with the spacecraft that this field is always present (simplest approach).

---

### TM[3,12] — Diagnostic parameter report structure report

**Same structure as TM[3,10]** (Figure 8-32) but for diagnostic structures (subtype == 12). Identical XTCE approach. Reuse the same sub-container definitions, distinguishing only by the base container restriction (subtype == 12).

---

### TM[3,35] — HK parameter report periodic generation properties report

**Packet structure:**

```
N (uint) | [ struct_id (enum) | periodic_gen_action_status (enum) | collection_interval (uint) ] × N
```

**MCS role**: Receive in response to TC[3,33].

**XTCE approach**: Standard nested `ContainerRefEntry` + `RepeatEntry` pattern.

```xml
<!-- Inner container: one notification entry -->
<SequenceContainer name="pus3-hk-gen-props-entry">
    <EntryList>
        <ParameterRefEntry parameterRef="hk_struct_id"/>
        <ParameterRefEntry parameterRef="periodic_gen_status"/>
        <ParameterRefEntry parameterRef="collection_interval"/>
    </EntryList>
</SequenceContainer>

<!-- Outer TM[3,35] container -->
<SequenceContainer name="TM_3_35">
    <EntryList>
        <ParameterRefEntry parameterRef="n_entries"/>
        <ContainerRefEntry containerRef="pus3-hk-gen-props-entry">
            <RepeatEntry>
                <Count>
                    <DynamicValue><ParameterInstanceRef parameterRef="n_entries"/></DynamicValue>
                </Count>
            </RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
    <BaseContainer containerRef="pus3-tm">
        <RestrictionCriteria>
            <Comparison parameterRef="/PUS/subtype" value="35"/>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**XTCE feasibility**: ✅ Fully expressible. No Java needed.

---

### TM[3,36] — Diagnostic parameter report periodic generation properties report

**Same structure as TM[3,35]** but subtype == 36 and using diagnostic struct IDs. Identical XTCE approach.

---

### TM[3,41] — Parameter functional reporting definition report

**Packet structure:**

```
func_def_id (enum) |
N (uint) |
[ report_nature (enum: HK=0/DIAG=1) | report_def_id (enum) | periodic_gen_status (enum) | collection_interval (uint) ] × N
```

**XTCE approach**: Same `ContainerRefEntry` + `RepeatEntry` pattern as TM[3,35]. The `report_nature` field distinguishes HK vs diagnostic report entries within the same PFRD.

**XTCE feasibility**: ✅ Fully expressible. No Java needed.

---

### TC[3,1] — Create housekeeping parameter report structure

**Packet structure (§8.3.2.1, Figure 8-21):**

```
struct_id (enum) | collection_interval (uint) |
N1 (uint) | parameter_id × N1 (enum) |
NFA (uint) |
[ super_comm_sample_repetition_number (uint) | N2 (uint) | parameter_id × N2 (enum) ] × NFA
```

**MCS role**: Send to spacecraft to define a new HK structure on-board.

**XTCE approach**: Complex nested TC with two levels of variable-length arrays (flat N1 array + NFA × N2 nested array).

For the `N1` simply-commutated parameter list: use `ArrayArgumentType` with `ArgumentInstanceRef` for `N1`.

For the `NFA` super-commutated sets: use nested `AggregateArgumentType`:

```xml
<!-- Level 2: one super-commutated parameter set entry -->
<AggregateArgumentType name="sc_param_id_array_type">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N2"/>   <!-- sibling member -->
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</AggregateArgumentType>

<AggregateArgumentType name="sc_set_type">
    <MemberList>
        <Member name="repetition_number" typeRef="/dt/uint8"/>
        <Member name="N2"                typeRef="/dt/uint8"/>
        <Member name="param_ids"         typeRef="sc_param_id_array_type"/>
    </MemberList>
</AggregateArgumentType>

<!-- Level 1: array of NFA super-commutated sets -->
<ArrayArgumentType name="sc_sets_type" arrayTypeRef="sc_set_type">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="NFA"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<MetaCommand name="TC_3_1_CREATE_HK_STRUCTURE">
    ...
    <ArgumentList>
        <Argument name="struct_id"           argumentTypeRef="hk_struct_id_type"/>
        <Argument name="collection_interval" argumentTypeRef="/dt/uint16"/>
        <Argument name="N1"                  argumentTypeRef="/dt/uint8"/>
        <Argument name="param_ids"           argumentTypeRef="param_id_array_type"/>
        <Argument name="NFA"                 argumentTypeRef="/dt/uint8"/>
        <Argument name="sc_sets"             argumentTypeRef="sc_sets_type"/>
    </ArgumentList>
    ...
</MetaCommand>
```

**XTCE feasibility**: ✅ Fully expressible using the sibling-member `ArgumentInstanceRef` nested array pattern (confirmed working in YAMCS — see pus_simulator_architecture.md §Nested Dynamic Array Patterns TC Side). The `N2` field inside `sc_set_type` is resolved as a sibling member by `sc_param_id_array_type`.

**Java required?**: No. Pure XTCE MetaCommand.

---

### TC[3,2] — Create diagnostic parameter report structure

**Same packet structure as TC[3,1]** (Figure 8-22) but for diagnostic structures (subtype == 2). Identical XTCE approach. Reuse the same argument types, only the MetaCommand name and base container reference differ.

---

### TC[3,3] — Delete housekeeping parameter report structures

**Packet structure (§8.3.2.3, Figure 8-23):**

```
N (uint) | struct_id × N (enum)
```

**XTCE approach**: Standard `N` + `ArrayArgumentType` pattern (same as TC[5,5/6]).

```xml
<MetaCommand name="TC_3_3_DELETE_HK_STRUCTURES">
    <ArgumentList>
        <Argument name="N"          argumentTypeRef="/dt/uint8"/>
        <Argument name="struct_ids" argumentTypeRef="hk_struct_id_array_type"/>
    </ArgumentList>
    ...
</MetaCommand>
```

**XTCE feasibility**: ✅ Trivially expressible. No Java needed.

---

### TC[3,4] — Delete diagnostic parameter report structures

**Same as TC[3,3]** (Figure 8-24) but for diagnostic structures. Same XTCE approach.

---

### TC[3,5] — Enable periodic generation of HK parameter reports

**Packet structure (§8.3.2.5, Figure 8-25):**

```
N (uint) | struct_id × N (enum)
```

Identical wire format to TC[3,3/4]. Standard `N` + `ArrayArgumentType`.

**XTCE feasibility**: ✅ Trivially expressible. No Java needed.

---

### TC[3,6/7/8] — Disable HK / Enable diag / Disable diag periodic generation

**All have the same wire format** as TC[3,5]: `N (uint) | struct_id × N`. Only the subtype and the struct_id enumeration (HK vs diagnostic) differ.

**XTCE feasibility**: ✅ Four separate MetaCommands using the same array argument pattern. No Java needed.

---

### TC[3,9] — Report housekeeping parameter report structures

**Packet structure (§8.3.2.9, Figure 8-29):**

```
N (uint) | struct_id × N (enum)
```

**XTCE feasibility**: ✅ Same N + array pattern. Response is TM[3,10]. No Java needed.

---

### TC[3,11] — Report diagnostic parameter report structures

**Same as TC[3,9]** but for diagnostic (Figure 8-31). Response is TM[3,12].

**XTCE feasibility**: ✅ Same pattern. No Java needed.

---

### TC[3,27] — Generate one-shot report for HK parameter report structures

**Packet structure:**

```
N (uint) | struct_id × N (enum)
```

Triggers immediate TM[3,25] for each struct_id regardless of periodic generation status.

**XTCE feasibility**: ✅ Same N + array pattern as TC[3,9]. No Java needed.

---

### TC[3,28] — Generate one-shot report for diagnostic parameter report structures

**Same as TC[3,27]** for diagnostic. Triggers immediate TM[3,26].

**XTCE feasibility**: ✅ Same pattern. No Java needed.

---

### TC[3,29] — Append parameters to a HK parameter report structure

**Packet structure:**

```
struct_id (enum) |
N1 (uint) | parameter_id × N1 (enum) |         ← simply commutated params to add
NFA (uint) |                                     ← super commutated sets to add
[ repetition_number (uint) | N2 (uint) | parameter_id × N2 (enum) ] × NFA
```

**Constraint from spec**: Rejected if:
- The structure's periodic generation status is "enabled"
- The structure contains super-commutated params and simply-commutated params are in the add list (mixed structure type constraint)
- A parameter is already present in the structure
- Unknown parameter ID
- Resource limit exceeded

**XTCE approach**: Same nested argument structure as TC[3,1] but without the `collection_interval` field. Reuse the `sc_set_type` aggregate and `sc_sets_type` array from TC[3,1].

**XTCE feasibility**: ✅ Fully expressible. No Java needed.

---

### TC[3,30] — Append parameters to a diagnostic parameter report structure

**Same as TC[3,29]** for diagnostic. Same nested argument structure.

---

### TC[3,31] — Modify collection interval of HK parameter report structures

**Packet structure:**

```
N (uint) | [ struct_id (enum) | collection_interval (uint) ] × N
```

**XTCE approach**: Use `AggregateArgumentType` + `ArrayArgumentType` pattern.

```xml
<AggregateArgumentType name="hk_interval_entry_type">
    <MemberList>
        <Member name="struct_id"           typeRef="hk_struct_id_type"/>
        <Member name="collection_interval" typeRef="/dt/uint16"/>
    </MemberList>
</AggregateArgumentType>

<ArrayArgumentType name="hk_interval_array_type" arrayTypeRef="hk_interval_entry_type">
    <!-- DynamicValue from top-level arg N -->
</ArrayArgumentType>

<MetaCommand name="TC_3_31_MODIFY_HK_INTERVAL">
    <ArgumentList>
        <Argument name="N"       argumentTypeRef="/dt/uint8"/>
        <Argument name="entries" argumentTypeRef="hk_interval_array_type"/>
    </ArgumentList>
</MetaCommand>
```

**XTCE feasibility**: ✅ Fully expressible. No Java needed.

---

### TC[3,32] — Modify collection interval of diagnostic parameter report structures

**Same as TC[3,31]** for diagnostic. Same argument pattern.

---

### TC[3,33] — Report periodic generation properties of HK parameter report structures

**Packet structure:**

```
N (uint) | struct_id × N (enum)
```

Response is TM[3,35]. Same N + array pattern as TC[3,9].

**XTCE feasibility**: ✅ Standard pattern. No Java needed.

---

### TC[3,34] — Report periodic generation properties of diagnostic parameter report structures

**Same as TC[3,33]** for diagnostic. Response is TM[3,36].

---

### TC[3,37] — Apply parameter functional reporting configurations

**Packet structure:**

```
config_execution_flag (enum: exclusive=1 / non-exclusive=0) |
N (uint) |
[ pfrd_id (enum) ] × N
```

If `exclusive`, all currently-enabled HK/diagnostic reports are disabled before applying.

**XTCE approach**:

```xml
<EnumeratedArgumentType name="exec_flag_type">
    <EnumerationList>
        <Enumeration value="0" label="NON_EXCLUSIVE"/>
        <Enumeration value="1" label="EXCLUSIVE"/>
    </EnumerationList>
</EnumeratedArgumentType>

<MetaCommand name="TC_3_37_APPLY_PFRC">
    <ArgumentList>
        <Argument name="exec_flag" argumentTypeRef="exec_flag_type"/>
        <Argument name="N"         argumentTypeRef="/dt/uint8"/>
        <Argument name="pfrd_ids"  argumentTypeRef="pfrd_id_array_type"/>
    </ArgumentList>
</MetaCommand>
```

**XTCE feasibility**: ✅ Straightforward. No Java needed.

---

### TC[3,38] — Create parameter functional reporting definition

**Packet structure:**

```
pfrd_id (enum) |
N (uint) |
[ report_nature (enum: HK=0/DIAG=1) | report_def_id (enum) | periodic_gen_status (enum) | collection_interval (uint) ] × N
```

**XTCE approach**: `AggregateArgumentType` for the per-entry tuple + `ArrayArgumentType` for the N entries.

```xml
<AggregateArgumentType name="pfrd_entry_type">
    <MemberList>
        <Member name="report_nature"          typeRef="report_nature_type"/>
        <Member name="report_def_id"          typeRef="struct_id_type"/>
        <Member name="periodic_gen_status"    typeRef="gen_status_type"/>
        <Member name="collection_interval"    typeRef="/dt/uint16"/>
    </MemberList>
</AggregateArgumentType>
```

**XTCE feasibility**: ✅ Fully expressible. No Java needed.

---

### TC[3,39] — Delete parameter functional reporting definitions

**Packet structure:**

```
N (uint) | pfrd_id × N (enum)
```

Standard array pattern.

---

### TC[3,40] — Report parameter functional reporting definitions

**Packet structure:**

```
N (uint) | pfrd_id × N (enum)
```

Standard array pattern. Response is TM[3,41].

---

### TC[3,42] — Add parameter report definitions to a PFRD

**Packet structure:**

```
pfrd_id (enum) |
N (uint) |
[ report_nature (enum) | report_def_id (enum) | periodic_gen_status (enum) | collection_interval (uint) ] × N
```

Same entry structure as TC[3,38] without the outer pfrd creation. Reuse `pfrd_entry_type`.

---

### TC[3,43] — Remove parameter report definitions from a PFRD

**Packet structure:**

```
pfrd_id (enum) |
N (uint) |
[ report_nature (enum) | report_def_id (enum) ] × N
```

Same pattern but only nature+id per entry (no status/interval).

---

### TC[3,44] — Modify periodic generation properties of report defs in a PFRD

**Packet structure:**

```
pfrd_id (enum) |
N (uint) |
[ report_nature (enum) | report_def_id (enum) | periodic_gen_status (enum) | collection_interval (uint) ] × N
```

Identical entry structure to TC[3,42]. Reuse `pfrd_entry_type`.

---

## D. XTCE / MDB Design Patterns

### Container hierarchy

```
/PUS/pus-tm  (apid + type + subtype + ...)
  └── pus3-tm  (type == 3)                   ← thin base, no payload
        ├── pus3-hk-tm    (subtype == 25)     ← extracts hk_struct_id
        │     ├── hk-report-struct-1          ← (hk_struct_id == 1): bus voltage, current, temp, ...
        │     ├── hk-report-struct-2          ← (hk_struct_id == 2): ADCS quaternion, rates, ...
        │     └── ...
        ├── pus3-diag-tm  (subtype == 26)     ← extracts diag_struct_id
        │     └── ...
        ├── TM_3_10       (subtype == 10)     ← HK structure report
        ├── TM_3_12       (subtype == 12)     ← Diag structure report
        ├── TM_3_35       (subtype == 35)     ← HK gen properties report
        ├── TM_3_36       (subtype == 36)     ← Diag gen properties report
        └── TM_3_41       (subtype == 41)     ← PFRD report
```

### Shared parameter types to declare in `pus3.xml`

```xml
<!-- HK structure ID -->
<EnumeratedParameterType name="hk_struct_id_type">
    <EnumerationList>
        <Enumeration value="1" label="STRUCT_POWER_BUS"/>
        <Enumeration value="2" label="STRUCT_ADCS"/>
        ...
    </EnumerationList>
</EnumeratedParameterType>

<!-- Parameter IDs (on-board parameter identifiers) -->
<EnumeratedParameterType name="param_id_type">
    <EnumerationList>
        <Enumeration value="0x1001" label="PARAM_BUS_VOLTAGE"/>
        <Enumeration value="0x1002" label="PARAM_BUS_CURRENT"/>
        ...
    </EnumerationList>
</EnumeratedParameterType>

<!-- Periodic generation status -->
<EnumeratedParameterType name="gen_status_type">
    <EnumerationList>
        <Enumeration value="0" label="DISABLED"/>
        <Enumeration value="1" label="ENABLED"/>
    </EnumerationList>
</EnumeratedParameterType>
```

### pus3.xml placement in yamcs.pus.yaml

```yaml
mdb:
  - type: "xtce"
    spec: "mdb/dt.xml"
  - type: "xtce"
    spec: "mdb/pus.xml"
  - type: "xtce"
    spec: "mdb/pus3.xml"     # ← add here (before pus5.xml which depends on it for parameter type reuse)
  - type: "xtce"
    spec: "mdb/pus5.xml"
  ...
```

---

## E. Native Java Service Design — Pus3Service

### When is a native Java service needed?

| Scenario | Java needed? |
|----------|-------------|
| Static predefined HK structures only, no runtime creation | **No** — pure XTCE sufficient |
| Dynamic HK structure creation via TC[3,1/2] during ops | **Yes** — need live structure registry + runtime TM decoder |
| One-shot report generation (TC[3,27/28]) relayed to spacecraft | **No** — just a TC, spacecraft handles it |

For **Pixxel's typical operational profile** (predefined structures, dynamic creation only during AIVT): **pure XTCE is the right choice for the nominal case.** A Java service is only needed for dynamic structure support.

### If dynamic decoding is required: Pus3HkDecoder pattern

This would be an **instance-level service** (not processor-level), similar to `PusEventDecoder`. It would:

1. Subscribe to the `tm_realtime` stream
2. For TM[3,25/26]: look up `struct_id` in an in-memory structure registry, decode parameter values by walking the field type list, emit `ParameterValue` objects to the parameter processor
3. For TM[3,10/12]: parse the incoming structure definition and update the registry
4. Persist the registry in `MementoDb` (key `pus3.structures`) for restart survival

```java
public class Pus3HkDecoder extends AbstractYamcsService implements StreamSubscriber {

    // Map from struct_id → ordered list of (paramName, sizeInBits, encoding)
    private final Map<Integer, List<FieldDef>> structureRegistry = new ConcurrentHashMap<>();

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        byte[] pkt = (byte[]) tuple.getColumn("packet");
        int svcType    = pkt[7] & 0xFF;  // PUS service type
        int svcSubtype = pkt[8] & 0xFF;  // PUS subtype
        if (svcType != 3) return;

        if (svcSubtype == 25 || svcSubtype == 26) {
            decodeHkReport(pkt, svcSubtype, tuple);
        } else if (svcSubtype == 10 || svcSubtype == 12) {
            parseStructureDefinition(pkt, svcSubtype);
        }
    }

    private void decodeHkReport(byte[] pkt, int subtype, Tuple tuple) {
        int structId = ((pkt[11] & 0xFF) << 8) | (pkt[12] & 0xFF);  // APP_DATA_OFFSET = 11
        List<FieldDef> fields = structureRegistry.get(structId);
        if (fields == null) { log.warn("Unknown HK struct_id {}", structId); return; }
        // decode fields starting at byte 13 and emit ParameterValues
        ...
    }
}
```

**MementoDb persistence pattern** (same as Pus5Service):

```java
// On startup: load persisted registry
MementoDb db = MementoDb.getInstance(yamcsInstance);
db.getJsonObject("pus3.hk_structures").ifPresent(this::restoreRegistry);

// On TC[3,1] processed (TM[3,10] received): update and persist
db.putJsonObject("pus3.hk_structures", serializeRegistry());
```

### If NOT using dynamic decoding: No Java in yamcs-core

For static structures, register no `Pus3Service` in `processor.yaml`. XTCE handles everything. TC encoding and TM decoding are both fully XTCE-driven.

---

## F. Simulator Design — Pus3Service.java

The simulator implements the **on-board** ST[03] service. It must:

1. Maintain a structure registry (predefined at startup + created by TC[3,1/2])
2. Send periodic TM[3,25/26] for each enabled structure at its collection interval
3. Handle all TCs and send PUS-1 ACK/NACK responses

### Data model

```java
public class HkStructure {
    int structId;
    int collectionIntervalMs;
    boolean periodicEnabled;
    List<Integer> simplyCommutatedParams;        // ordered param IDs
    List<SuperCommSet> superCommutatedSets;       // ordered super-comm sets

    public static class SuperCommSet {
        int repetitionNumber;                    // N samples per collection interval
        List<Integer> paramIds;                  // ordered param IDs in each set
    }
}
```

### Registration in PusSimulator.java

```java
// In constructor:
pus3Service = new Pus3Service(this);

// In doStart():
pus3Service.start();                             // starts periodic TM thread

// In executePendingCommands():
case 3 -> pus3Service.executeTc(commandPacket);
```

### Periodic TM generation pattern

```java
@Override
public void start() {
    // Start one scheduler entry per structure; reschedule when interval changes
    for (HkStructure s : structures.values()) {
        if (s.periodicEnabled) scheduleStructure(s);
    }
}

private void scheduleStructure(HkStructure s) {
    ScheduledFuture<?> f = pusSimulator.executor.scheduleAtFixedRate(
        () -> sendHkReport(s),
        0, s.collectionIntervalMs, TimeUnit.MILLISECONDS);
    periodicTasks.put(s.structId, f);
}

private void sendHkReport(HkStructure s) {
    // Calculate payload size: 2B struct_id + sum of param value sizes
    PusTmPacket pkt = newPacket(25, payloadSize);
    ByteBuffer bb = pkt.getUserDataBuffer();
    bb.putShort((short) s.structId);
    for (int paramId : s.simplyCommutatedParams) {
        bb.putInt(readParamValue(paramId));   // mission-specific param fetch
    }
    for (SuperCommSet sc : s.superCommutatedSets) {
        for (int rep = 0; rep < sc.repetitionNumber; rep++) {
            for (int paramId : sc.paramIds) {
                bb.putInt(readParamValue(paramId));
            }
        }
    }
    pusSimulator.transmitRealtimeTM(pkt);
}
```

### TC handler

```java
@Override
public void executeTc(PusTcPacket tc) {
    ack_start(tc);
    ByteBuffer bb = tc.getUserDataBuffer();
    switch (tc.getSubtype()) {
        case 1  -> handleCreateHkStructure(tc, bb);
        case 2  -> handleCreateDiagStructure(tc, bb);
        case 3  -> handleDeleteHkStructures(tc, bb);
        case 4  -> handleDeleteDiagStructures(tc, bb);
        case 5  -> handleEnableHkPeriodicGen(tc, bb);
        case 6  -> handleDisableHkPeriodicGen(tc, bb);
        case 7  -> handleEnableDiagPeriodicGen(tc, bb);
        case 8  -> handleDisableDiagPeriodicGen(tc, bb);
        case 9  -> handleReportHkStructures(tc, bb);
        case 11 -> handleReportDiagStructures(tc, bb);
        case 25 -> ack_completion(tc); // HK report is TM, not a TC
        case 27 -> handleOneShot(tc, bb, false);
        case 28 -> handleOneShot(tc, bb, true);
        case 29 -> handleAppendHkParams(tc, bb);
        case 30 -> handleAppendDiagParams(tc, bb);
        case 31 -> handleModifyHkInterval(tc, bb);
        case 32 -> handleModifyDiagInterval(tc, bb);
        case 33 -> handleReportHkGenProps(tc, bb);
        case 34 -> handleReportDiagGenProps(tc, bb);
        case 37 -> handleApplyPfrc(tc, bb);
        case 38 -> handleCreatePfrd(tc, bb);
        case 39 -> handleDeletePfrds(tc, bb);
        case 40 -> handleReportPfrds(tc, bb);
        case 42 -> handleAddPfrdEntries(tc, bb);
        case 43 -> handleRemovePfrdEntries(tc, bb);
        case 44 -> handleModifyPfrdGenProps(tc, bb);
        default -> { nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE); return; }
    }
}
```

### handleCreateHkStructure parse pattern

```java
private void handleCreateHkStructure(PusTcPacket tc, ByteBuffer bb) {
    int structId           = bb.getShort() & 0xFFFF;
    int collectionInterval = bb.getShort() & 0xFFFF;
    int N1 = bb.get() & 0xFF;
    List<Integer> simplyParams = new ArrayList<>();
    for (int i = 0; i < N1; i++) simplyParams.add(bb.getShort() & 0xFFFF);
    int NFA = bb.get() & 0xFF;
    List<HkStructure.SuperCommSet> scSets = new ArrayList<>();
    for (int f = 0; f < NFA; f++) {
        int repNum = bb.get() & 0xFF;
        int N2     = bb.get() & 0xFF;
        List<Integer> scParams = new ArrayList<>();
        for (int j = 0; j < N2; j++) scParams.add(bb.getShort() & 0xFFFF);
        scSets.add(new HkStructure.SuperCommSet(repNum, scParams));
    }
    if (hkStructures.containsKey(structId)) {
        nack_start(tc, COMPL_ERR_STRUCT_ALREADY_EXISTS); return;
    }
    HkStructure s = new HkStructure(structId, collectionInterval, false, simplyParams, scSets);
    hkStructures.put(structId, s);
    ack_completion(tc);
}
```

---

## G. Integration Testing

### Setup

```bash
mvn install -DskipTests -pl simulator,yamcs-core,examples/pus
cd examples/pus
mvn yamcs:run
```

### What to verify

#### 1. TC path — command history

| TC | Expected CommandComplete | Notes |
|----|--------------------------|-------|
| TC[3,5] enable struct_id=1 | OK | Confirm periodic TM[3,25] begins arriving |
| TC[3,6] disable struct_id=1 | OK | Confirm TM[3,25] stops |
| TC[3,27] one-shot struct_id=1 | OK | Confirm single TM[3,25] arrives even when disabled |
| TC[3,9] report struct_id=1 | OK | Confirm TM[3,10] arrives with correct structure |
| TC[3,5] unknown struct_id=99 | NOK | Failed start-of-execution notification |

#### 2. TM path — parameter decoding

For each predefined HK structure:
- Navigate to *Monitoring → Parameters* → `/PUS3/hk-report-struct-N`
- Verify parameter values match injected simulator values
- Verify struct_id parameter decoded correctly
- Verify super-commutated parameter values appear in repetition order

#### 3. Periodic rate test

```python
# TC[3,5] enable struct 1 at collection_interval=5 (5 × min_sampling_interval)
# Subscribe to hk-report-struct-1 container
# Assert TM arrives within expected period
# TC[3,31] modify interval to 10
# Assert new TM rate matches updated interval
```

### Automated test pattern

```python
# tests/test-pus03.py
from yamcs.client import YamcsClient
import queue, time

INSTANCE  = "pus"
PROCESSOR = "realtime"
HOST      = "localhost:8090"

def run_tests():
    client   = YamcsClient(HOST)
    proc     = client.get_processor(INSTANCE, PROCESSOR)
    cmd_conn = proc.create_command_connection()

    tm_queue = queue.Queue()
    sub = proc.create_container_subscription(
        "/PUS3/hk-report-struct-1",
        on_data=lambda c: tm_queue.put(c),
    )

    def issue(cmd_path, args=None, timeout=5.0):
        cmd = cmd_conn.issue(cmd_path, args=args or {})
        cmd.await_acknowledgment("Acknowledge_Sent", timeout=timeout)
        return cmd.await_acknowledgment("CommandComplete", timeout=timeout)

    # Enable struct 1 — periodic TM should start
    compl = issue("/PUS3/TC_3_5_ENABLE_HK", args={"N": 1, "struct_ids": [1]})
    assert compl.status == "OK"
    pkt = tm_queue.get(timeout=5)
    assert pkt is not None

    # One-shot on disabled struct: disable first, then one-shot
    issue("/PUS3/TC_3_6_DISABLE_HK", args={"N": 1, "struct_ids": [1]})
    time.sleep(0.5)   # drain residual packets
    while not tm_queue.empty(): tm_queue.get()
    issue("/PUS3/TC_3_27_ONESHOT_HK", args={"N": 1, "struct_ids": [1]})
    pkt = tm_queue.get(timeout=5)
    assert pkt is not None, "One-shot TM not received"

    # Unknown struct_id → NOK
    compl = issue("/PUS3/TC_3_5_ENABLE_HK", args={"N": 1, "struct_ids": [99]})
    assert compl.status == "NOK"

    sub.cancel()
    print("All PUS-3 tests passed")

if __name__ == "__main__":
    run_tests()
```

### Debugging checklist

| Symptom | Where to look |
|---------|---------------|
| TM[3,25] not decoded (all params 0) | XTCE container offset mismatch — check hk_struct_id field size and param field sizes |
| TM[3,25] container not matched | hk_struct_id value in packet doesn't match XTCE restriction comparison value |
| No periodic TM after TC[3,5] | Simulator `scheduleStructure()` not called; check `periodicEnabled` flag update |
| TM[3,10] struct report parameters wrong | N1 / NFA count parsing error in simulator `sendHkStructureReport()`; check byte alignment |
| TC[3,1] with NFA>0 NOK unexpectedly | Super-commutated parsing error — off-by-one on N2 inner loop |

---

## H. Gaps and Shortcomings

### Gap 1 — TM[3,25/26] dynamic decoding not supported by XTCE (High — if dynamic structures needed)

**Problem**: XTCE container definitions are compile-time. If the mission creates structures at runtime via TC[3,1/2], the incoming TM[3,25/26] packets cannot be decoded by the static XTCE containers.

**Impact**: Parameters from dynamically-created structures will not appear in YAMCS parameter tables or archive. Raw packet bytes are archived but not decoded.

**Workaround**: Implement `Pus3HkDecoder` Java instance service (see §E above). Effort: High (requires runtime XTCE bypass, custom parameter publishing, MementoDb registry).

**Mitigation for Pixxel**: Define all HK structures as predefined. Use TC[3,1/2] only during AIVT with a dedicated test mode and accept that new structures won't be decoded without MDB update + restart.

---

### Gap 2 — Super-commutated parameters: interleaved sample ordering in TM[3,25/26] (Medium)

**Problem**: Super-commutated parameters appear in TM[3,25] as `NFA` sets of `(value_A_1, value_B_1, value_A_2, value_B_2, ...)` — each of the `N` samples is a complete set of all super-commutated parameters. XTCE array containers will decode these as a flat sequence, not as labeled time-series samples.

**Impact**: The time relationship between super-commutated samples is not expressed in XTCE. Each sample value is decoded as a separate parameter occurrence rather than a timestamped sub-interval sample.

**Workaround**: Declare separate parameter names per super-commutated sample slot (`param_voltage_sample_1`, `param_voltage_sample_2`, etc.) for each repetition position. No semantic richness but correct decoding.

---

### Gap 3 — TM[3,10/12] structure report: `periodic_gen_action_status` optional field (Low)

**Problem**: Per spec Figure 8-30, `periodic_gen_action_status` is "optional" in TM[3,10] — present only if the sub-service provides the capability for managing periodic generation.

**Impact**: XTCE cannot conditionally include a field based on a capability declaration. The field either always appears or never appears in the packet.

**Workaround**: Pre-agree with the spacecraft that the field is always present (most spacecraft FSW includes it). Model it as always-present in XTCE. If truly absent, align field offsets accordingly and skip it in the MDB.

---

### Gap 4 — PFRC (TC[3,37–44]) subservice state not tracked on ground (Low)

**Problem**: The parameter functional reporting configuration sub-service (TC[3,37–44]) manages named "functional reporting definitions" that map to collections of HK/diag report enable/disable configurations. YAMCS does not track PFRD state on the ground side.

**Impact**: Ground operators cannot query the live PFRD state without sending TC[3,40]. There is no ground-side mirror of which PFRD was last applied.

**Workaround**: Use TC[3,40] to query current PFRD definitions. For ground-side state tracking, implement a lightweight `Pus3StateTracker` instance service that listens to TC[3,38/39/42/43/44] uplink commands and maintains a local PFRD map (informational only, not used for TM decoding).

---

### Gap 5 — No `Pus1Verifier` for TC[3,9/11/27/28/33/34/40] response validation (Low)

**Problem**: These TCs trigger TM responses (TM[3,10/12/25/26/35/36/41]). Command verification via `Pus1Verifier` or `ContainerVerifier` is not wired up in the MDB MetaCommand definitions.

**Workaround**: Add `ContainerVerifier` entries in the MetaCommand `CommandVerifierSet` for the response containers. The verifier matches on `struct_id` == the requested struct_id.

---

## I. Summary Table

### TC Messages (YAMCS sends to spacecraft)

| TC | XTCE Only? | Java in yamcs-core? | Wire format complexity | Key note |
|----|-----------|---------------------|----------------------|----------|
| TC[3,1] Create HK struct | ✅ Yes | No | Nested 2-level array | Reuse sibling-member nested arg pattern |
| TC[3,2] Create diag struct | ✅ Yes | No | Same as TC[3,1] | |
| TC[3,3] Delete HK | ✅ Yes | No | N + array | |
| TC[3,4] Delete diag | ✅ Yes | No | N + array | |
| TC[3,5] Enable HK gen | ✅ Yes | No | N + array | |
| TC[3,6] Disable HK gen | ✅ Yes | No | N + array | |
| TC[3,7] Enable diag gen | ✅ Yes | No | N + array | |
| TC[3,8] Disable diag gen | ✅ Yes | No | N + array | |
| TC[3,9] Report HK structs | ✅ Yes | No | N + array | Response: TM[3,10] |
| TC[3,11] Report diag structs | ✅ Yes | No | N + array | Response: TM[3,12] |
| TC[3,27] One-shot HK | ✅ Yes | No | N + array | Response: TM[3,25] |
| TC[3,28] One-shot diag | ✅ Yes | No | N + array | Response: TM[3,26] |
| TC[3,29] Append HK params | ✅ Yes | No | struct_id + N1 flat + NFA nested | |
| TC[3,30] Append diag params | ✅ Yes | No | Same as TC[3,29] | |
| TC[3,31] Modify HK interval | ✅ Yes | No | N × (id + interval) | |
| TC[3,32] Modify diag interval | ✅ Yes | No | Same as TC[3,31] | |
| TC[3,33] Report HK gen props | ✅ Yes | No | N + array | Response: TM[3,35] |
| TC[3,34] Report diag gen props | ✅ Yes | No | N + array | Response: TM[3,36] |
| TC[3,37] Apply PFRC | ✅ Yes | No | exec_flag + N + pfrd_ids | |
| TC[3,38] Create PFRD | ✅ Yes | No | pfrd_id + N × (nature+id+status+interval) | |
| TC[3,39] Delete PFRDs | ✅ Yes | No | N + array | |
| TC[3,40] Report PFRDs | ✅ Yes | No | N + array | Response: TM[3,41] |
| TC[3,42] Add PFRD entries | ✅ Yes | No | Same as TC[3,38] | |
| TC[3,43] Remove PFRD entries | ✅ Yes | No | pfrd_id + N × (nature+id) | |
| TC[3,44] Modify PFRD gen props | ✅ Yes | No | Same as TC[3,42] | |

### TM Messages (YAMCS receives from spacecraft)

| TM | XTCE Only? | Java in yamcs-core? | Key note |
|----|-----------|---------------------|----------|
| TM[3,25] HK parameter report | **Partial** | **Yes, if dynamic structs** | One container/struct for static; Java decoder for dynamic |
| TM[3,26] Diag parameter report | **Partial** | **Yes, if dynamic structs** | Same as TM[3,25] |
| TM[3,10] HK structure report | ✅ Yes | No | Multi-level nested ContainerRefEntry + RepeatEntry |
| TM[3,12] Diag structure report | ✅ Yes | No | Same as TM[3,10] |
| TM[3,35] HK gen properties | ✅ Yes | No | ContainerRefEntry + RepeatEntry (1-level) |
| TM[3,36] Diag gen properties | ✅ Yes | No | Same as TM[3,35] |
| TM[3,41] PFRD report | ✅ Yes | No | ContainerRefEntry + RepeatEntry (1-level) |

### Overall Assessment

**All TC encoding is pure XTCE** — no Java changes to yamcs-core are required for the uplink path.

**TM[3,25/26] decoding** is the only path that may require Java:
- ✅ **Static predefined structures**: fully XTCE, no Java needed, covers the standard nominal ops case.
- ⚠️ **Dynamic runtime structures**: requires `Pus3HkDecoder` Java instance service; significant effort.

**Recommendation for Pixxel**: Start with static XTCE-only implementation covering all predefined HK structures. Defer dynamic decoding until operationally required. The simulator (`Pus3Service.java`) should support the full TC[3,1–44] set to enable AIVT testing even before the dynamic MCS decoder is built.
