# PUS ST[06] Memory Management — Analysis & Implementation Plan

**Spec reference**: ECSS-E-ST-70-41C §6.6 (requirements) and §8.6 (packet definitions)
**Required subtypes**: TC[6,1], TC[6,2], TC[6,3], TM[6,4], TC[6,5], TM[6,6], TC[6,7], TM[6,8],
TC[6,9], TM[6,10], TC[6,13], TC[6,14]

> **Scope note.** Only the subtypes listed above are in scope. The atomic load (TC[6,11]),
> abort-all-dumps (TC[6,12]), write-protect (TC[6,15]/[6,16]), check-object-memory-object
> (TC[6,17]/TM[6,18]), and all the by-reference / to-file variants (TC[6,19]–TC[6,22]) are
> **out of scope** and are only mentioned where they clarify the in-scope design.

---

## a) General Context

PUS ST[06] is the **Memory Management** service. It provides the capability to **load**, **dump**,
and **check** the contents of on-board memories, and to configure memory-level behaviour
(scrubbing, write protection). "Memory" is any physical or virtual on-board memory area — RAM,
mass memory, etc. (§6.6.1.1).

The service defines **four subservice types** (§6.6.1). The required subset touches three of them:

| Subservice | What it manages | In-scope subtypes |
|---|---|---|
| **Raw data** memory management (§6.6.3) | Memories addressed by **absolute byte address**; content type not implicitly known | TC[6,2] load, TC[6,5]/TM[6,6] dump, TC[6,9]/TM[6,10] check |
| **Structured (object) data** memory management (§6.6.4) | Memories addressed by **base + offset** (base = a memory-object identifier, e.g. a file or OBCP) | TC[6,1] load, TC[6,3]/TM[6,4] dump, TC[6,7]/TM[6,8] check |
| **Common** memory management (§6.6.5) | Cross-cutting (abort-all-dumps) | *out of scope* (TC[6,12]) |
| **Memory configuration** (§6.6.6) | Manage the memory as a whole — scrubbing, write-protect | TC[6,13]/[6,14] scrubbing (in scope); write-protect out of scope |

### Ground vs. on-board responsibility (MCS is ground segment only)

In this deployment **YAMCS is the ground station (MCS)**. Its job is to **encode** the ST[06]
telecommands and **decode** the ST[06] telemetry reports. All memory state and logic live on the
spacecraft; the **Java PUS simulator** emulates that on-board behaviour for integration testing.

| Responsibility | Where |
|---|---|
| Encode & uplink TC[6,1/2/3/5/7/9/13/14] | **Ground (YAMCS MCS)** — XTCE |
| Decode & display TM[6,4/6/8/10] dump/check reports | **Ground (YAMCS MCS)** — XTCE |
| Track PUS-1 acceptance/execution acks for every TC | **Ground (YAMCS MCS)** — ST[01] |
| Hold the actual memory contents (RAM, mass memory) | **On-board (satellite / simulator)** |
| Validate memory ID / alignment / bounds / write-protect and reject (PUS-1 NACK) | **On-board (satellite / simulator)** |
| Execute loads, extract dumps, compute checksums | **On-board (satellite / simulator)** |
| Build TM[6,4/6/8/10] responses | **On-board (satellite / simulator)** |
| Maintain scrubbing / write-protection status | **On-board (satellite / simulator)** |

**YAMCS/MCS implementation = XTCE only (`pus6.xml`). No Java changes to `yamcs-core` are needed
for the required ST[06] subset.** See the feasibility verdict in section (c).

### Key characteristics

| Property | Value |
|---|---|
| PUS service type | 6 |
| Sub-services in scope | Raw data, Structured (object) data, Memory configuration (scrubbing) |
| State maintained on-board | Memory byte arrays; scrubbing status; write-protection status |
| Background tasks | None required — dumps/checks in scope respond synchronously with a single TM report |
| New `dt.xml` types needed | None. Local `pus6.xml` types only (enumerated `memory_id`, `binary16` checksum, variable binary blocks) |
| Major XTCE challenges | (1) "deduced" `base` type; (2) variable-length "data to load"/"dumped data" octet-strings repeated N times — **both already solved in-repo** (see below) |

### Spec-defined message types (§8.6.2) and scope

| Subtype | Dir | Name | In scope |
|---|---|---|---|
| 1 | TC | Load object memory data | **YES** |
| 2 | TC | Load raw memory data areas | **YES** |
| 3 | TC | Dump object memory data | **YES** |
| 4 | TM | Dumped object memory data report | **YES** |
| 5 | TC | Dump raw memory data | **YES** |
| 6 | TM | Dumped raw memory data report | **YES** |
| 7 | TC | Check object memory data | **YES** |
| 8 | TM | Checked object memory data report | **YES** |
| 9 | TC | Check raw memory data | **YES** |
| 10 | TM | Checked raw memory data report | **YES** |
| 11 | TC | Load raw memory atomic data area (non-interruptible) | no |
| 12 | TC | Abort all memory dumps | no |
| 13 | TC | Enable the scrubbing of a memory | **YES** |
| 14 | TC | Disable the scrubbing of a memory | **YES** |
| 15 | TC | Enable the write protection of a memory | no |
| 16 | TC | Disable the write protection of a memory | no |
| 17 | TC | Check an object memory object | no |
| 18 | TM | Checked object memory object report | no |
| 19 | TC | Load raw memory data areas by reference | no |
| 20 | TC | Dump raw memory data areas to file | no |
| 21 | TC | Load object memory data areas by reference | no |
| 22 | TC | Dump object memory data areas to file | no |

**No TM confirmation exists for the load TCs (TC[6,1], TC[6,2]) or the scrubbing TCs
(TC[6,13], TC[6,14])** — their outcome is reported only through the standard PUS-1 execution
verification (acceptance + completion). Only the dump and check TCs produce ST[06] data reports.

### Mission conventions (agreed)

ST[06] is inherently hardware-specific (memory map, addressing, alignment). These conventions
fix the free parameters so the wire format is expressible in XTCE and testable in the simulator.

| # | Convention | Rationale |
|---|---|---|
| M1 | **`memory_id` is an enumerated set**: `RAM=0`, `MASS_MEMORY=1`. Present in every message. | §6.6.3.2c/§6.6.4.2 require memory identifiers when >1 memory is managed. Enumeration gives MDB-level safety. |
| M2 | **Raw ops target RAM** (absolute addressing); **object ops target MASS_MEMORY** (base+offset). | Cleanly separates the raw vs structured subservices onto distinct memories. |
| M3 | **`base` (structured) is `uint16`** = memory-object identifier. | Spec §6.6.4.3 marks `base` as "deduced"; XTCE cannot express per-request type variation. Same resolution ST[20] used for its "deduced" value. |
| M4 | **Checksumming enabled**; `checksum` is a 16-bit field, **CRC-16/CCITT**. | Spec §6.6.3.1/§6.6.4.1 make checksumming a declared capability. Reuses the ST[21] `pus21.md` CRC convention. |
| M5 | **4-byte access alignment; `start_address`/`offset`/`length` are `uint32`, multiples of 4.** | §6.6.3.2b absolute addressing + the alignment-rejection rules (§6.6.3.3e/§6.6.3.4f/§6.6.3.5f). |
| M6 | **Per-instruction data carries a `length:uint16` octet prefix**; `data`/`dumped_data` is a variable binary of `length` octets. `N:uint8` = number of instructions per request. | The "variable octet-string" / "deduced size" fields (§8.6.2 figures) become explicitly length-prefixed — the validated `pus13.xml`/`pus19.xml` pattern (no unverified "trailing binary" behaviour, no zero-padding waste). |

---

## How the hard XTCE cases are solved (in-repo precedent)

Two structural challenges appear across ST[06]; both already have **validated, shipped**
solutions in this repository, so **no new `yamcs-core` code is required**.

**1. Variable-length data block inside a repeated instruction list (TC side).**
Load requests are `N × { …, length, data(length octets), checksum }`. A flat `ArrayArgumentType`
cannot express this (stride would be wrong once any non-last `data` is non-empty). The solved
pattern is `examples/pus/src/main/yamcs/mdb/pus19.xml` `ea_add_entry_type`:

```xml
<BinaryArgumentType name="ld_data_arg_type">          <!-- variable data block -->
    <BinaryDataEncoding>
        <SizeInBits>
            <DynamicValue>
                <ArgumentInstanceRef argumentRef="length"/>   <!-- sibling member -->
                <LinearAdjustment slope="8"/>                 <!-- octets -> bits -->
            </DynamicValue>
        </SizeInBits>
    </BinaryDataEncoding>
</BinaryArgumentType>
<AggregateArgumentType name="ld_raw_instr_type">
    <MemberList>
        <Member typeRef="/dt/uint32" name="start_address"/>
        <Member typeRef="/dt/uint16" name="length"/>
        <Member typeRef="ld_data_arg_type" name="data"/>
        <Member typeRef="binary16"   name="checksum"/>
    </MemberList>
</AggregateArgumentType>
<ArrayArgumentType name="ld_raw_instr_array_type" arrayTypeRef="ld_raw_instr_type"> … sized by N … </ArrayArgumentType>
```

The `argumentRef="length"` resolves to the sibling `length` member of the enclosing aggregate —
exactly how `pus19.xml` embeds a variable-length request TC per array element.

**2. Variable-length data block inside a repeated report (TM side).**
Dump reports are `N × { …, length, dumped_data(length octets), checksum }`. The solved pattern is
`pus19.xml` `ea_def_entry` + `TM_19_11` (and `pus13.xml` `part_data_last_type`): a
`BinaryParameterType` sized by a `length` parameter decoded earlier **in the same entry**, wrapped
in a `ContainerRefEntry` + `RepeatEntry`:

```xml
<BinaryParameterType name="dmp_data_type">
    <BinaryDataEncoding>
        <SizeInBits>
            <DynamicValue>
                <ParameterInstanceRef parameterRef="dmp_length"/>  <!-- getFromEnd(0): latest in this entry -->
                <LinearAdjustment slope="8"/>
            </DynamicValue>
        </SizeInBits>
    </BinaryDataEncoding>
</BinaryParameterType>

<SequenceContainer name="dmp_raw_entry">   <!-- one instruction's worth of dumped data -->
    <EntryList>
        <ParameterRefEntry parameterRef="dmp_start_address"/>
        <ParameterRefEntry parameterRef="dmp_length"/>
        <ParameterRefEntry parameterRef="dmp_data"/>
        <ParameterRefEntry parameterRef="dmp_checksum"/>
    </EntryList>
</SequenceContainer>

<SequenceContainer name="TM_6_6">
    <EntryList>
        <ParameterRefEntry parameterRef="memory_id"/>
        <ParameterRefEntry parameterRef="dmp_count"/>
        <ContainerRefEntry containerRef="dmp_raw_entry">
            <RepeatEntry><Count><DynamicValue><ParameterInstanceRef parameterRef="dmp_count"/></DynamicValue></Count></RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
    <BaseContainer containerRef="pus6-tm"> … subtype==6 … </BaseContainer>
</SequenceContainer>
```

**3. Flat fixed-stride arrays** (dump/check *requests*, and the *checked* reports, whose entries
carry no variable data) use the simpler `ArrayParameterType`/`ArrayArgumentType` over an aggregate,
sized by N with an `IncludeCondition` for N=0 — exactly `pus20.xml` `ParamEntriesType` /
`SetParamEntriesType`.

---

## b) Per-Subtype Analysis

All packets share the base containers/commands from `pus.xml` (mirroring `pus20.xml`):

```xml
<SequenceContainer name="pus6-tm">   <!-- type==6, all ST[06] TM -->
    <EntryList/>
    <BaseContainer containerRef="/PUS/pus-tm">
        <RestrictionCriteria><Comparison parameterRef="/PUS/type" value="6"/></RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>

<MetaCommand name="pus6-tc" abstract="true">
    <BaseMetaCommand metaCommandRef="/PUS/pus-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="apid" argumentValue="1"/>
            <ArgumentAssignment argumentName="type" argumentValue="6"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <CommandContainer name="pus6-tc"><EntryList/></CommandContainer>
</MetaCommand>
```

Shared local types (in `pus6.xml`):

```xml
<!-- TM side -->
<EnumeratedParameterType name="memory_id_type">
    <IntegerDataEncoding encoding="unsigned" sizeInBits="8"/>
    <EnumerationList>
        <Enumeration value="0" label="RAM"/>
        <Enumeration value="1" label="MASS_MEMORY"/>
    </EnumerationList>
</EnumeratedParameterType>
<BinaryParameterType name="binary16"><BinaryDataEncoding><SizeInBits><FixedValue>16</FixedValue></SizeInBits></BinaryDataEncoding></BinaryParameterType>
<!-- TC side: matching EnumeratedArgumentType memory_id_arg_type + binary16_arg (16-bit checksum) -->
```

Byte layouts below assume the full PUS secondary header (as in the running YAMCS+simulator stack):
`[0..5]` CCSDS primary header, `[6]` service type = 6, `[7]` subtype, then the PUS secondary-header
fields the base container already extracts, then the application data shown.

---

### Raw data memory management subservice

#### TC[6,2] — Load raw memory data areas  *(§6.6.3.3 / §8.6.2.2, Fig 8-67)*

**Purpose**: Ground writes one or more raw data areas into an absolute-addressed memory (RAM).

**Application data**:
```
memory_id : enum(uint8)                       (RAM)
N         : uint8                              number of load instructions
N × {
    start_address : uint32                     4-byte aligned
    length        : uint16                     octet count of data
    data          : binary(length octets)
    checksum      : binary(16 bits)            CRC-16/CCITT over data (M4)
}
```

**Execution flow**:
```
[GROUND → SAT] TC[6,2] uplinked (XTCE-encoded)  → PUS-1 acceptance ack
[ON-BOARD]     validate memory_id, write access, alignment, bounds (§6.6.3.3e)
[ON-BOARD]     write each area; verify checksum (§6.6.3.3j); abort on error
[SAT → GROUND] PUS-1 completion ack (OK / failed-execution notification)
```

**XTCE**: nested aggregate array + variable binary — the pus19 TC pattern (challenge #1 above).
**Verdict**: XTCE-only. No dedicated TM (PUS-1 completion only).
**Simulator**: `writeRaw()` into the RAM byte array; reject → `nack_start`/`nack_completion`.

---

#### TC[6,5] — Dump raw memory data  *(§6.6.3.4 / §8.6.2.5, Fig 8-70)*

**Purpose**: Ground requests a dump of one or more raw memory areas; on-board replies TM[6,6].

**Application data**:
```
memory_id : enum(uint8)
N         : uint8
N × { start_address : uint32 ; length : uint32 }     fixed 8-byte stride
```

**XTCE**: flat `ArrayArgumentType` over a 2-member aggregate, sized by N (pus20 pattern).
**Verdict**: XTCE-only. Triggers **TM[6,6]** (declare an `ExecutionVerifier` `ContainerRef` →
`TM_6_6`, as `pus20.xml` does for TC[20,1]→TM[20,2]).
**Simulator**: read each area from RAM, compute CRC, build TM[6,6], `ack_completion`.

---

#### TM[6,6] — Dumped raw memory data report  *(§8.6.2.6, Fig 8-71)*

**Source data**:
```
memory_id : enum(uint8)
N         : uint8
N × {
    start_address : uint32
    length        : uint16
    dumped_data   : binary(length octets)
    checksum      : binary(16 bits)
}
```

**XTCE**: nested `ContainerRefEntry` + `RepeatEntry` with a variable `BinaryParameterType` sized by
the per-entry `length` — the pus19/pus13 TM pattern (challenge #2 above).
**Verdict**: XTCE-only.
**Simulator**: `sendDumpedRawReport()` builds the packet via `newPacket(6, …)`.

---

#### TC[6,9] — Check raw memory data  *(§6.6.3.5 / §8.6.2.9, Fig 8-74)*

**Purpose**: Ground asks the satellite to checksum raw areas *without* downlinking their contents
(§6.6.3.5 NOTE2) — used to detect suspected-faulty memory. Replies TM[6,10].

**Application data**: identical shape to TC[6,5]: `memory_id, N, N × {start_address:u32, length:u32}`.

**XTCE**: flat array (pus20 pattern).
**Verdict**: XTCE-only. Triggers **TM[6,10]**.
**Simulator**: compute CRC of each area, build TM[6,10], `ack_completion`.

---

#### TM[6,10] — Checked raw memory data report  *(§8.6.2.10, Fig 8-75)*

**Source data**:
```
memory_id : enum(uint8)
N         : uint8
N × { start_address : uint32 ; length : uint32 ; checksum : binary(16 bits) }   fixed 10-byte stride
```

**XTCE**: **flat** `ArrayParameterType` over a 3-member aggregate (fixed stride — no variable data,
so no nested container needed). pus20 pattern.
**Verdict**: XTCE-only.

---

### Structured (object) data memory management subservice

The structured subservice differs from raw in two ways: (1) addressing is **base + offset** where
`base` (M3: `uint16` memory-object id) appears once per request, *before* `N`; (2) it targets
MASS_MEMORY (M2).

#### TC[6,1] — Load object memory data  *(§6.6.4.4 / §8.6.2.1, Fig 8-66)*

**Application data**:
```
memory_id : enum(uint8)                        (MASS_MEMORY)
base      : uint16                              memory-object identifier
N         : uint8
N × { offset : uint32 ; length : uint16 ; data : binary(length) ; checksum : binary16 }
```

**XTCE**: same nested aggregate-array + variable-binary pattern as TC[6,2]; `base` is a plain
`uint16` argument between `memory_id` and `N`.
**Verdict**: XTCE-only. No dedicated TM (PUS-1 completion only).
**Simulator**: write into the object store keyed by `(memory_id, base)` at `offset`.

---

#### TC[6,3] — Dump object memory data  *(§6.6.4.5 / §8.6.2.3, Fig 8-68)* → TM[6,4]

**Application data**: `memory_id, base:u16, N, N × {offset:u32, length:u32}`. Flat array.
**Verdict**: XTCE-only. Triggers **TM[6,4]**.

#### TM[6,4] — Dumped object memory data report  *(§8.6.2.4, Fig 8-69)*

**Source data**: `memory_id, base:u16, N, N × {offset:u32, length:u16, dumped_data:binary(length), checksum:binary16}`.
**XTCE**: nested `ContainerRefEntry` + `RepeatEntry` + variable binary (as TM[6,6]), preceded by the
fixed `base` field.
**Verdict**: XTCE-only.

---

#### TC[6,7] — Check object memory data  *(§6.6.4.6 / §8.6.2.7, Fig 8-72)* → TM[6,8]

**Application data**: `memory_id, base:u16, N, N × {offset:u32, length:u32}`. Flat array.
**Verdict**: XTCE-only. Triggers **TM[6,8]**.

#### TM[6,8] — Checked object memory data report  *(§8.6.2.8, Fig 8-73)*

**Source data**: `memory_id, base:u16, N, N × {offset:u32, length:u32, checksum:binary16}`. Flat
fixed-stride array (pus20 pattern).
**Verdict**: XTCE-only.

---

### Memory configuration subservice (scrubbing)

#### TC[6,13] — Enable the scrubbing of a memory  *(§6.6.6.1.4 / §8.6.2.13, Fig 8-77)*

**Application data**: `memory_id : enum(uint8)` — a single instruction, one field.

**XTCE**: trivial MetaCommand with one enumerated argument (the simplest ST[06] TC).
**Verdict**: XTCE-only. No dedicated TM (PUS-1 completion only).
**Simulator**: reject if the memory cannot be scrubbed (§6.6.6.1.4d), else set
`scrubbingStatus[memory_id] = enabled`; `ack_completion`.

#### TC[6,14] — Disable the scrubbing of a memory  *(§6.6.6.1.5 / §8.6.2.14, Fig 8-78)*

Identical shape to TC[6,13]; sets `scrubbingStatus[memory_id] = disabled`.
**Verdict**: XTCE-only.

---

## c) Gaps / Mission Conventions

| # | Subtypes | Gap | Layer | Resolution |
|---|---|---|---|---|
| 1 | 6,1 / 6,3 / 6,4 / 6,7 / 6,8 | **"Deduced" `base` type** — §6.6.4.3 says the base's format depends on the memory object; XTCE cannot express per-request type variation. | MCS (XTCE) | **M3**: fix `base` to `uint16` memory-object id. Document in ICD. Same approach as ST[20]'s deduced value. |
| 2 | 6,1 / 6,2 / 6,4 / 6,6 | **Variable-length data / dumped-data octet-string repeated N times.** A flat array cannot express per-entry variable stride. | MCS (XTCE) | **M6**: explicit `length:uint16` prefix + variable binary sized `length*8` (validated pus19/pus13 pattern). *Not a workaround* — genuinely variable, no padding. |
| 3 | 6,1 / 6,2 | **"Deduced size" checksum field** — spec's octet-string sizing note. | MCS (XTCE) | **M4**: fix checksum to a 16-bit field (`binary16`), CRC-16/CCITT. |
| 4 | 6,2 / 6,1 | **Partial / ordered per-instruction execution** (§6.6.3.3g/i, §6.6.4.4g) — process valid instructions in order, abort on the first write/checksum error. Pure on-board logic. | On-board (sim) | Simulator emulates in Java; XTCE only carries the wire format. YAMCS sees the outcome via the PUS-1 completion ack. |
| 5 | all | **Rejection rules** — invalid memory id, no write/read access, write-protected, misaligned, out-of-bounds, packet exceeds max CCSDS size (§6.6.3.x / §6.6.4.x). | On-board (sim) | Simulator validates and returns PUS-1 `nack_start`/`nack_completion` with an error code. `memory_id` enumeration (M1) additionally gives ground-side MDB safety. |
| 6 | 6,13 / 6,14 | **Scrubbing mechanism is memory-dependent** (§6.6.6.1.3 NOTE2) — the standard only defines the *status*. | On-board (sim) | Simulator keeps a boolean `scrubbingStatus` per memory; no actual scrubbing emulated. |

### Overall feasibility verdict

**YES — the required ST[06] subset is XTCE-only for the MCS. No `yamcs-core` Java changes are
needed.** This matches ST[20]/ST[13]/ST[19]: YAMCS is the ground station, so it only needs the
encode/decode contract in the MDB. The two structural challenges (variable-length data blocks;
deduced `base`) are resolved by documented mission conventions and the **already-shipped**
pus19/pus13/pus20 XTCE patterns — no new native decoders, verifiers, or postprocessors.

> If YAMCS were ever the *on-board* node (not the case here), the memory store, per-instruction
> execution, checksumming, and rejection logic would need a native `PusTcHandler` (`Pus6Service`
> in `yamcs-core`, per `pus_native_arch.md`). For the ground-station scope that logic lives in the
> **simulator** instead.

### Two-layer artifact table

| Layer | Artifact | Status |
|---|---|---|
| **MCS / ground** | `examples/pus/src/main/yamcs/mdb/pus6.xml` — all 12 subtypes | To create |
| **MCS / ground** | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` — register `mdb/pus6.xml` | To edit |
| **Simulator (on-board emulation)** | `simulator/src/main/java/org/yamcs/simulator/pus/Pus6Service.java` | To create |
| **Simulator (on-board emulation)** | `simulator/.../pus/PusSimulator.java` — instantiate + `case 6 ->` dispatch | To edit |
| **Testing** | `examples/pus/tests/test-pus6.py` | To create |
| **`yamcs-core` Java** | — | **None required** |

---

## d) Implementation Checklist

1. **`examples/pus/src/main/yamcs/mdb/pus6.xml`** (new). Structure after `pus20.xml`:
   - `TelemetryMetaData`: `memory_id_type` (enum), `binary16`, per-entry params + variable
     `BinaryParameterType`s; flat `ArrayParameterType`s for TM[6,8]/[6,10]; `pus6-tm` base +
     `dmp_*_entry` sub-containers; containers `TM_6_4`, `TM_6_6`, `TM_6_8`, `TM_6_10`.
   - `CommandMetaData`: `memory_id_arg_type` (enum), variable `BinaryArgumentType`s + aggregate/
     array types for loads, flat arrays for dump/check; abstract `pus6-tc`; MetaCommands for
     TC[6,1/2/3/5/7/9/13/14]. Dump/check TCs get a `VerifierSet` `ExecutionVerifier` `ContainerRef`
     to their TM report (pus20 TC[20,1] precedent).
2. **`examples/pus/src/main/yamcs/etc/yamcs.pus.yaml`** — add `- type: "xtce"  spec: "mdb/pus6.xml"`
   to the `mdb:` list (after `dt.xml`/`pus.xml`, per `pus_simulator_architecture.md`).
3. **`simulator/.../pus/Pus6Service.java`** (new, `extends AbstractPusService`, pusType 6):
   - State: `Map<Integer,byte[]> rawMemories` (seed RAM), `Map<Long,byte[]> objectStore`
     (key = `(memory_id<<16)|base`), `Map<Integer,Boolean> scrubbingStatus`,
     `Map<Integer,Boolean> writeProtect` (default false).
   - `executeTc` dispatch: `case 1,2 -> load…`, `3,5 -> dump…`, `7,9 -> check…`,
     `13,14 -> scrubbing…`, default `nack_start(INVALID_PUS_SUBTYPE)`.
   - Validation helpers: memory-id known, 4-byte alignment, bounds, write-protect, CRC-16/CCITT
     (copy the routine from the ST[21] convention). Add error-code constants (invalid memory id,
     misaligned, out-of-bounds, write-protected, checksum mismatch).
   - Dump/check build `TM[6,4/6/8/10]` via `newPacket(...)` then `ack_completion`.
4. **`simulator/.../pus/PusSimulator.java`** — construct `pus6Service = new Pus6Service(this)` and
   add `case 6 -> pus6Service.executeTc(commandPacket);`.
5. **`examples/pus/tests/test-pus6.py`** — cover:
   - **Load→dump round-trip**: TC[6,2] load bytes → TC[6,5] dump → assert `dumped_data` equals what
     was loaded; same for object TC[6,1]→TC[6,3].
   - **Check**: TC[6,9]/[6,7] → TM[6,10]/[6,8]; assert reported checksum matches a locally computed
     CRC-16/CCITT.
   - **Scrubbing**: TC[6,13]/[6,14] → `CommandComplete OK`.
   - **NACK cases**: unknown `memory_id`, misaligned address/length, out-of-bounds, N>1 with mixed
     valid/invalid → correct PUS-1 completion status.

### Verification (per `pus_native_arch.md` §14)

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests
mvn -pl examples/pus yamcs:run          # http://localhost:8090, instance "pus"
python3 examples/pus/tests/test-pus6.py # YAMCS must be running
```

*(User verifies manually; the assistant does not self-run builds/tests/the simulator.)*
