# PUS ST[13] Large Packet Transfer — Research & Implementation Guide

**Source**: ECSS-E-ST-70-41C §6.13 (pages 229–236), §8.13 (pages 526–528)
**Target env**: test_yamcs (Python simulator + UDP + simplified PUS secondary header)

---

## a) General PUS 13 Context

### Purpose
ST[13] handles large packets that exceed the CCSDS maximum packet size by splitting them into an ordered sequence of smaller fragments. Reconstruction happens at the receiving end.

### Ground vs. On-board Responsibility (MCS is ground segment only)

| Responsibility | Where |
|---|---|
| **Downlink** — split large TM data into parts, assign transaction_id/part_seq_num, emit TM[13,1/2/3] | **On-board (satellite)** |
| **Downlink** — receive, decode, and display individual TM[13,1/2/3] packets | **Ground (YAMCS MCS)** — XTCE decodes each part packet |
| **Uplink** — construct and send TC[13,9/10/11] fragments to the satellite | **Ground (YAMCS MCS)** — XTCE encodes each part TC |
| **Uplink** — buffer received parts, check sequence continuity, manage reception timer, reconstruct large command | **On-board (satellite)** |
| **Uplink abort** — emit TM[13,16] on timeout or sequence gap | **On-board (satellite)** |

**YAMCS/MCS implementation = XTCE only (`pus13.xml`). No Java changes to `yamcs-core` are needed for ST[13].**

The `pus13_simulator.py` described in this document emulates the satellite's on-board behavior (part buffering, reconstruction, timer management, TM[13,1/2/3] generation) for ground testing. It is not part of the MCS.

> **Uplink coordination note**: YAMCS does not automatically fragment large commands into ST[13] parts. The operator (or a test script) must send TC[13,9], TC[13,10], TC[13,11] as three explicit, sequential commands. Each TC is fully definable in XTCE — no `yamcs-core` Java is needed, but no automatic splitting is provided either.

### Two Subservices

**Large packet downlink subservice** (spacecraft → ground):
1. **[ON-BOARD]** Assigns a unique *large message transaction identifier*
2. **[ON-BOARD]** Splits the large packet into equal-sized parts (last part may be smaller)
3. **[ON-BOARD]** Assigns a sequential *part sequence number* (starts at 1) to each part
4. **[ON-BOARD]** Encapsulates each part: TM[13,1] (first), TM[13,2] (intermediate), TM[13,3] (last)
5. **[SAT → GROUND]** YAMCS receives each TM part packet; XTCE decodes transaction_id, part_seq_num, part data
6. **[ON-BOARD]** If reception timer expires before all parts transmitted → abort (TM[13,16] downlinked to ground)

**Large packet uplink subservice** (ground → spacecraft):
1. **[GROUND]** Operator/script assigns a unique *large message transaction identifier* and splits the large command
2. **[GROUND → SAT]** YAMCS sends TC[13,9] (first part), TC[13,10] (intermediate parts), TC[13,11] (last part) — each encoded via XTCE
3. **[ON-BOARD]** Buffers received parts, checks sequence continuity, manages reception timer
4. **[ON-BOARD]** When TC[13,11] received: reconstructs large command, executes it
5. **[ON-BOARD]** If reception timer expires or sequence gap detected → emits TM[13,16] abort report
6. **[SAT → GROUND]** YAMCS receives TM[13,16]; XTCE decodes transaction_id + failure_reason

### Key Concepts
| Concept | Description |
|---------|-------------|
| `large_message_transaction_identifier` | Unique counter per large packet, starts at 1 |
| `part_sequence_number` | Per-part position counter, starts at 1 |
| Part size (downlink) | Constrained by max TM packet length; all equal except last |
| Part size (uplink) | Constrained by max TC packet length; all equal except last |
| Reception timeout | Abort if next part not received within configured window |
| TM[13,16] | Uplink abort notification: transaction_id + failure_reason |

### CCSDS / PUS Header (test_yamcs simplified format)
```
[0-1]  word1: version=0 | type=TM(0)/TC(1) | sec_hdr=1 | APID(11b)
[2-3]  word2: seq_flags=3 | seq_count(14b)
[4-5]  word3: packet_data_length (total - 7)
[6]    service_type  = 13
[7]    service_subtype = 1 / 2 / 3 / 9 / 10 / 11 / 16
[8+]   application data (subtype-specific)
```

---

## b) Required TM/TC — Context, Implementation Plan, XTCE vs Java

### Mission-Specific Field Sizes (chosen for simulator)
The PUS spec says "unsigned integer" without specifying width — these are mission-defined:
| Field | Chosen type | Size |
|-------|-------------|------|
| `large_message_transaction_identifier` | uint16 | 2 bytes |
| `part_sequence_number` | uint16 | 2 bytes |
| `part` data (fixed) | binary | 64 bytes (512 bits) |
| `failure_reason` | uint8 (enumerated) | 1 byte |

---

### TM[13,1] — First Downlink Part Report

**Spec (§8.13.2.1, Figure 8-140)**:
| Field | Type |
|-------|------|
| large_message_transaction_identifier | unsigned integer |
| part_sequence_number | unsigned integer |
| part | fixed octet-string |

**Packet layout**:
```
[8-9]   transaction_id   uint16
[10-11] part_seq_num     uint16
[12-75] part             binary, 64 bytes fixed
```

**XTCE implementation**: Fully implementable
```xml
<SequenceContainer name="TM_13_1" shortDescription="TM[13,1] First downlink part report">
    <EntryList>
        <ParameterRefEntry parameterRef="transaction_id" />
        <ParameterRefEntry parameterRef="part_seq_num" />
        <ParameterRefEntry parameterRef="part_data" />
    </EntryList>
    <BaseContainer containerRef="PUS13Packet">
        <RestrictionCriteria>
            <Comparison parameterRef="service_subtype" value="1" />
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```
`part_data` uses `BinaryParameterType` with `<FixedValue>512</FixedValue>` (64 bytes × 8 bits).

**Simulator (on-board emulation)**: Python generates TM[13,1] at the start of each large packet downlink — emulating satellite-side fragmentation and transmission.

**Gaps**: None. Standard XTCE + YAMCS binary parameter support.

---

### TM[13,2] — Intermediate Downlink Part Report

**Spec (§8.13.2.2, Figure 8-141)**: Identical structure to TM[13,1].

**XTCE implementation**: Fully implementable — same as TM[13,1] with `service_subtype == 2`.

**Simulator (on-board emulation)**: Sends one or more TM[13,2] packets between TM[13,1] and TM[13,3], emulating on-board intermediate part transmission.

**Gaps**: None.

---

### TM[13,3] — Last Downlink Part Report

**Spec (§8.13.2.3, Figure 8-142)**:
| Field | Type |
|-------|------|
| large_message_transaction_identifier | unsigned integer |
| part_sequence_number | unsigned integer |
| part | **fixed octet-string of deduced size** |

Note from spec: "The size of the part field is deduced from the size of the telemetry packet that is transported."

**XTCE implementation**: Implementable with YAMCS trailing-binary support
- Define `part_last` as `BinaryParameterType` without fixed `SizeInBits`
- YAMCS infers the remaining bytes from the CCSDS `packet_data_length` field after extracting the preceding fixed fields
- XTCE encoding hint: use dynamic sizing or omit `SizeInBits` to consume remaining packet bytes

```xml
<BinaryParameterType name="part_last_type">
    <BinaryDataEncoding>
        <SizeInBits>
            <DynamicValue>
                <!-- Remaining bytes = (pkt_data_length + 1) * 8 - bits_already_consumed -->
                <!-- YAMCS fills to end of packet when no fixed size given -->
            </DynamicValue>
        </SizeInBits>
    </BinaryDataEncoding>
</BinaryParameterType>
```

**Simulator (on-board emulation)**: Sends the final (shorter) chunk; CCSDS length field reflects actual size — emulating satellite-side last-part transmission.

**Gaps**:
- Minor: YAMCS's exact XTCE syntax for "consume remaining bytes" should be verified against the running YAMCS version. If unsupported, use fixed 64-byte parameter with zero-padding as fallback (no functional loss for simulator testing).
- The deduced-size pattern is not universally portable XTCE — it is a YAMCS extension behavior.

---

### TC[13,9] — Uplink the First Part

**Spec (§8.13.2.4, Figure 8-143)**:
| Field | Type |
|-------|------|
| large_message_transaction_identifier | unsigned integer |
| part_sequence_number | unsigned integer |
| part | fixed octet-string |

**XTCE implementation**: Fully implementable
```xml
<MetaCommand name="TC_13_9" shortDescription="TC[13,9] Uplink the first part">
    <ArgumentList>
        <Argument name="transaction_id" argumentTypeRef="uint16_arg" />
        <Argument name="part_seq_num"   argumentTypeRef="uint16_arg" />
        <Argument name="part_data"      argumentTypeRef="binary64_arg" />
    </ArgumentList>
    <CommandContainer name="TC_13_9">
        <EntryList>
            <ArgumentRefEntry argumentRef="transaction_id" />
            <ArgumentRefEntry argumentRef="part_seq_num" />
            <ArgumentRefEntry argumentRef="part_data" />
        </EntryList>
    </CommandContainer>
    <BaseMetaCommand metaCommandRef="pus13-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="service_subtype" argumentValue="9" />
        </ArgumentAssignmentList>
    </BaseMetaCommand>
</MetaCommand>
```
`binary64_arg` uses `BinaryArgumentType` with `<FixedValue>512</FixedValue>`.

**Simulator (on-board emulation)**: Receives TC[13,9], buffers the first part, starts the reception timer — emulating satellite-side uplink buffering.

**Gaps**: None.

---

### TC[13,10] — Uplink an Intermediate Part

**Spec (§8.13.2.5, Figure 8-144)**: Identical structure to TC[13,9].

**XTCE implementation**: Fully implementable — same as TC[13,9] with `service_subtype == 10`.

**Gaps**: None.

---

### TC[13,11] — Uplink the Last Part

**Spec (§8.13.2.6, Figure 8-145)**:
| Field | Type |
|-------|------|
| large_message_transaction_identifier | unsigned integer |
| part_sequence_number | unsigned integer |
| part | **fixed octet-string of deduced size** |

Note: "The size of the part field is deduced from the size of the large telecommand packet that is transported."

**XTCE implementation**: Partially implementable — practical workaround required
- YAMCS XTCE TC arguments require a statically declared size; there is no built-in "variable-to-end-of-packet" for TC arguments
- **Practical solution**: Define `part_data` as fixed 64-byte argument (same as TC[13,9/10]); the last actual payload bytes are embedded at the start; the operator zeros-pads or the simulator ignores trailing zeros
- **Alternative**: Add a mission-specific `part_actual_length` uint16 argument before `part_data` so the simulator knows the true boundary — this deviates from PUS spec but is pragmatic

**Simulator (on-board emulation)**: On receipt of TC[13,11], ends the uplink operation and reconstructs the large packet — emulating satellite-side command reassembly.

**Gaps**:
- **Shortcoming**: True spec-compliant deduced-size for TC is not achievable in standard XTCE or YAMCS TC encoding. The fixed-size workaround means the ground always sends 64 bytes even if the last part is shorter.
- No loss of functional testability — the simulator can ignore trailing zeros.

---

### TM[13,16] — Large Packet Uplink Abort Report

**Spec (§8.13.2.7, Figure 8-146)**:
| Field | Type |
|-------|------|
| large_message_transaction_identifier | unsigned integer |
| failure_reason | enumerated |

**Packet layout**:
```
[8-9]  transaction_id   uint16
[10]   failure_reason   uint8 (enumerated)
```

**Failure reason enumeration** (mission-defined values):
| Value | Label |
|-------|-------|
| 1 | ReceptionTimeout |
| 2 | SequenceDiscontinuity |
| 3 | InternalError |

**XTCE implementation**: Fully implementable
```xml
<EnumeratedParameterType name="failure_reason_type">
    <IntegerDataEncoding encoding="unsigned" sizeInBits="8" />
    <EnumerationList>
        <Enumeration value="1" label="ReceptionTimeout" />
        <Enumeration value="2" label="SequenceDiscontinuity" />
        <Enumeration value="3" label="InternalError" />
    </EnumerationList>
</EnumeratedParameterType>

<SequenceContainer name="TM_13_16" shortDescription="TM[13,16] Uplink abort report">
    <EntryList>
        <ParameterRefEntry parameterRef="transaction_id" />
        <ParameterRefEntry parameterRef="failure_reason" />
    </EntryList>
    <BaseContainer containerRef="PUS13Packet">
        <RestrictionCriteria>
            <Comparison parameterRef="service_subtype" value="16" />
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Simulator (on-board emulation)**: Sends TM[13,16] when:
- Reception timer expires (no TC[13,10/11] within timeout window after TC[13,9])
- Part sequence number gap detected

YAMCS receives TM[13,16] and decodes it via XTCE (ground display only — no abort logic runs in YAMCS).

**Gaps**: None.

---

## c) Gaps & Shortcomings Summary

| Subtype | Dir | XTCE Only? | Notes |
|---------|-----|-----------|-------|
| TM[13,1] | TM | ✅ Yes | Fixed-size binary parameter, standard XTCE |
| TM[13,2] | TM | ✅ Yes | Same as TM[13,1] |
| TM[13,3] | TM | ⚠️ Mostly | Trailing deduced-size binary — YAMCS-specific, needs verification; fixed-size fallback available |
| TC[13,9] | TC | ✅ Yes | Fixed binary argument, standard XTCE |
| TC[13,10] | TC | ✅ Yes | Same as TC[13,9] |
| TC[13,11] | TC | ⚠️ Workaround | No true deduced-size for TC args in XTCE; use fixed max-size with zero-padding |
| TM[13,16] | TM | ✅ Yes | Enumerated parameter, standard XTCE |

### Overall Verdict
**PUS 13 is ~90% implementable with XTCE alone (MCS scope).** The two gap areas are:
1. **TM[13,3] deduced part size** — YAMCS may support trailing binary natively; if not, fixed-size fallback is functionally equivalent for simulator testing
2. **TC[13,11] deduced part size** — No standard XTCE solution; fixed max-size argument is the practical workaround (no Java code required)

**Zero Java code changes are needed to the YAMCS server (`yamcs-core`).** Required artifacts by layer:

| Layer | Artifact | Purpose |
|-------|----------|---------|
| **MCS / YAMCS ground** | `mdb/pus13.xml` | XTCE TC encoding (TC[13,9/10/11]) and TM decoding (TM[13,1/2/3], TM[13,16]) |
| **MCS / YAMCS ground** | `yamcs.pus-test.yaml` update | Load `mdb/pus13.xml` into the Mission Database |
| **Simulator (on-board emulation)** | `simulators/pus13_simulator.py` | Emulates satellite: generates TM[13,1/2/3] downlink sequences; receives/reconstructs uplinked TC[13,9/10/11]; sends TM[13,16] on timeout or sequence gap |

> **Key finding**: All on-board logic (fragmentation, buffering, reconstruction, timer management, abort signalling) lives in the simulator. YAMCS MCS only encodes outgoing TC parts and decodes incoming TM parts — both purely via XTCE.

---

## Implementation Files (when building)

| Layer | File | Action |
|-------|------|--------|
| **MCS / YAMCS ground** | `test_yamcs/src/main/yamcs/mdb/pus13.xml` | Create — XTCE containers + commands for TC encoding and TM decoding |
| **MCS / YAMCS ground** | `test_yamcs/src/main/yamcs/etc/yamcs.pus-test.yaml` | Update — add `mdb/pus13.xml` to MDB list |
| **Simulator (on-board emulation)** | `test_yamcs/simulators/pus13_simulator.py` | Create — Python sim emulating satellite: TM[13,1/2/3] generation, TC[13,9/10/11] receive + reconstruct, TM[13,16] on abort |

### Reference Files
- `test_yamcs/src/main/yamcs/mdb/pus20.xml` — XTCE structure pattern
- `test_yamcs/simulators/pus20_simulator.py` — Python simulator pattern
- `test_yamcs/src/main/yamcs/mdb/pus_dt.xml` — shared data types (uint8/16/32)
