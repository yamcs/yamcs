# PUS ST[20] On-Board Parameter Management — Analysis & Implementation Plan

**Spec reference**: ECSS-E-ST-70-41C §6.20 (requirements) and §8.20 (packet definitions)
**Required subtypes**: TC[20,1], TM[20,2], TC[20,3]

---

## a) General Context

PUS ST[20] is the **On-Board Parameter Management** service. It provides capabilities for
managing on-board parameters, including reading current values and setting new values.

---

### Ground vs. On-board Responsibility (MCS is ground segment only)

| Responsibility | Where |
|---|---|
| Send TC[20,1] to request parameter values | **Ground (YAMCS MCS)** — XTCE encodes TC packet |
| Send TC[20,3] to set parameter values | **Ground (YAMCS MCS)** — XTCE encodes TC packet |
| Receive and display TM[20,2] parameter value reports | **Ground (YAMCS MCS)** — XTCE decodes TM packet |
| Maintain the on-board parameter store (param_id → value mapping) | **On-board (satellite)** |
| Validate param_ids and reject invalid requests (NACK via PUS-1) | **On-board (satellite)** |
| Apply set-parameter commands and update the parameter store | **On-board (satellite)** |
| Generate TM[20,2] responses with current parameter values | **On-board (satellite)** |

**YAMCS/MCS implementation = XTCE only (`pus20.xml`). No Java changes to `yamcs-core` are needed for ST[20].**

The `pus20_simulator.py` described in this document lives in the **simulator package** and emulates the satellite's on-board parameter management behavior for ground testing. It is not part of the MCS.

---

### Key characteristics

| Property | Value |
|----------|-------|
| PUS service type | 20 |
| Sub-service | Parameter management subservice |
| State maintained | On-board parameter store (param_id → value mapping) |
| Background tasks | Optional: periodic unsolicited TM[20,2] reports |
| New pus_dt.xml types needed | None (uint8/uint16/uint32 sufficient with mission convention) |
| Major XTCE limitation | "Deduced" value type — value width/format varies per param_id |

### Spec-defined message types (§8.20.2)

| Subtype | Direction | Name | In scope |
|---------|-----------|------|----------|
| 1 | TC | Report parameter values | YES |
| 2 | TM | Parameter value report | YES |
| 3 | TC | Set parameter values | YES |
| 4 | TC | Change raw memory parameter definitions | no |
| 5 | TC | Change object memory parameter definitions | no |
| 6 | TC | Report parameter definitions | no |
| 7 | TM | Parameter definition report | no |

### Parameter store model

Each parameter is identified by a 16-bit `param_id`. The service maintains a flat store:

```
param_store: { param_id (uint16) → value (uint32) }
```

- Parameter IDs are predefined and unique within the spacecraft context.
- The spec defines values as "deduced" (type depends on param_id). The mission fixes all
  values to uint32 to enable XTCE expression.
- Invalid param_ids (not in the store) cause a failed-start PUS-1 acknowledgement per §6.20.4.1d.

---

## b) Per-Subtype Context and Implementation Plan

---

### TC[20,1] — Report parameter values

**Spec §6.20.4.1 + §8.20.2.1**

**Purpose**: Ground requests values for one or more on-board parameters.

**Execution flow**:

```
[GROUND → SAT]  TC[20,1] uplinked by YAMCS (XTCE-encoded)
[ON-BOARD]      TC arrives → validate param_ids → lookup values in parameter store
[ON-BOARD]      Build TM[20,2] response with current values → transmit
[SAT → GROUND]  TM[20,2] downlinked, decoded by YAMCS via XTCE
```

**Packet structure (Figure 8-219)**:

```
CCSDS primary header (6 bytes)
  service_type    = 20  (1 byte)
  service_subtype = 1   (1 byte)
  N               (uint8)   — number of parameters requested
  N × param_id    (uint16)  — parameter identifiers
```

**Byte layout** (simplified PUS, no full PUS secondary header):

```
[0-1]  CCSDS word1: version(3b)=0, type(1b)=TC, sec_hdr(1b)=1, APID(11b)
[2-3]  CCSDS word2: seq_flags=3, seq_count
[4-5]  CCSDS word3: packet_data_length
[6]    service_type = 20
[7]    service_subtype = 1
[8]    N (uint8)
[9+]   N × param_id (uint16, big-endian)
```

**XTCE implementation** (`pus20.xml` — already implemented):

```xml
<ArrayArgumentType arrayTypeRef="/dt/uint16" name="ParamIdArrayType">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="num_params" />
                    <LinearAdjustment intercept="-1" />
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<MetaCommand name="TC_20_1" shortDescription="TC[20,1] Report parameter values">
    <ArgumentList>
        <Argument argumentTypeRef="/dt/uint8"      name="num_params" />
        <Argument argumentTypeRef="ParamIdArrayType" name="param_ids" />
    </ArgumentList>
    <CommandContainer name="TC_20_1">
        <EntryList>
            <!-- CCSDS primary header fixed fields -->
            <FixedValueEntry name="ccsds-version"   binaryValue="00"   sizeInBits="3" />
            <FixedValueEntry name="ccsds-type"       binaryValue="01"   sizeInBits="1" />
            <FixedValueEntry name="ccsds-sec-hdr"    binaryValue="01"   sizeInBits="1" />
            <FixedValueEntry name="ccsds-apid"       binaryValue="00C8" sizeInBits="11" />
            <FixedValueEntry name="ccsds-seq-flags"  binaryValue="03"   sizeInBits="2" />
            <FixedValueEntry name="ccsds-seq-count"  binaryValue="0000" sizeInBits="14" />
            <FixedValueEntry name="ccsds-length"     binaryValue="0000" sizeInBits="16" />
            <FixedValueEntry name="service-type"     binaryValue="14"   sizeInBits="8" />
            <FixedValueEntry name="service-subtype"  binaryValue="01"   sizeInBits="8" />
            <ArgumentRefEntry argumentRef="num_params" />
            <ArgumentRefEntry argumentRef="param_ids" />
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

**Simulator (on-board emulation)** (`pus20_simulator.py` — already implemented):

Emulates the satellite-side behavior: receives TC[20,1], looks up the requested param_ids in the on-board parameter store, and transmits TM[20,2] back to ground.

```python
def parse_tc_20_1(data):
    n = data[8]
    return [struct.unpack_from(">H", data, 9 + i*2)[0] for i in range(n)]

def handle_tc(data, addr, tm_sock):
    param_ids = parse_tc_20_1(data)
    result = [(pid, PARAM_STORE.get(pid, 0)) for pid in param_ids]
    pkt = build_tm_20_2(result)
    tm_sock.sendto(pkt, (TM_HOST, TM_PORT))
```

**XTCE status**: Fully compatible. Dynamic uint16 array driven by N count.

**Simulator status**: Implemented. Responds with TM[20,2] containing all requested values.

---

### TM[20,2] — Parameter value report

**Spec §6.20.4.1 + §8.20.2.2**

**Purpose**: Reports current values of requested parameters (response to TC[20,1]). Generated on-board; decoded on the ground by YAMCS via XTCE.

**Packet structure (Figure 8-220)**:

```
CCSDS primary header (6 bytes)
  service_type    = 20  (1 byte)
  service_subtype = 2   (1 byte)
  N               (uint8)    — number of parameter entries
  N × { param_id (uint16), value ("deduced") }
```

**Byte layout** (simplified PUS):

```
[0-1]  CCSDS word1: version=0, type=TM, sec_hdr=1, APID=200
[2-3]  CCSDS word2: seq_flags=3, seq_count
[4-5]  CCSDS word3: packet_data_length
[6]    service_type = 20
[7]    service_subtype = 2
[8]    N (uint8)
[9+]   N × { param_id:uint16, param_value:uint32 }  (6 bytes each)
```

**XTCE implementation** (`pus20.xml` — already implemented):

```xml
<AggregateParameterType name="ParamEntryType">
    <MemberList>
        <Member typeRef="/dt/uint16" name="param_id" />
        <Member typeRef="/dt/uint32" name="param_value" />
    </MemberList>
</AggregateParameterType>

<ArrayParameterType arrayTypeRef="ParamEntryType" name="ParamEntriesType">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ParameterInstanceRef parameterRef="num_params" />
                    <LinearAdjustment intercept="-1" />
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayParameterType>

<SequenceContainer name="TM_20_2" shortDescription="TM[20,2] Parameter Value Report">
    <EntryList>
        <ParameterRefEntry parameterRef="num_params" />
        <ArrayParameterRefEntry parameterRef="param_entries" />
    </EntryList>
    <BaseContainer containerRef="PUS20Packet">
        <RestrictionCriteria>
            <ComparisonList>
                <Comparison parameterRef="pus_apid"        value="200" />
                <Comparison parameterRef="service_type"    value="20" />
                <Comparison parameterRef="service_subtype" value="2" />
            </ComparisonList>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Simulator (on-board emulation)** (`pus20_simulator.py` — already implemented):

Emulates the satellite-side behavior: builds and transmits TM[20,2] with the values retrieved from the on-board parameter store.

```python
def build_tm_20_2(params):
    n = len(params)
    user_data = struct.pack(">B", n)
    for pid, val in params:
        user_data += struct.pack(">HI", pid & 0xFFFF, val & 0xFFFFFFFF)
    secondary = struct.pack(">BB", 20, 2)
    payload = secondary + user_data
    pkt_data_len = len(payload) - 1
    word1 = (1 << 11) | APID
    word2 = (0b11 << 14) | _next_seq()
    return struct.pack(">HHH", word1, word2, pkt_data_len) + payload
```

**XTCE status**: Compatible with mission convention (all param values fixed to uint32).

**Simulator status**: Implemented.

---

### TC[20,3] — Set parameter values

**Spec §6.20.4.2 + §8.20.2.3**

**Purpose**: Ground sets new values for one or more on-board parameters.

**Execution flow**:

```
[GROUND → SAT]  TC[20,3] uplinked by YAMCS (XTCE-encoded)
[ON-BOARD]      TC arrives → validate param_ids → update values in parameter store
[ON-BOARD]      Emit PUS-1 acknowledgement (success or failure per param)
[SAT → GROUND]  PUS-1 ACK/NACK downlinked, received and displayed by YAMCS
```

**Packet structure (Figure 8-221)**:

```
CCSDS primary header (6 bytes)
  service_type    = 20  (1 byte)
  service_subtype = 3   (1 byte)
  N               (uint8)    — number of set instructions
  N × { param_id (uint16), value ("deduced") }
```

**Byte layout** (simplified PUS):

```
[0-5]  CCSDS primary header
[6]    service_type = 20
[7]    service_subtype = 3
[8]    N (uint8)
[9+]   N × { param_id:uint16, value:uint32 }  (6 bytes each)
```

**XTCE implementation** (not yet in `pus20.xml` — minor addition required):

Add to `<ArgumentTypeSet>`:
```xml
<AggregateArgumentType name="SetParamEntryType">
    <MemberList>
        <Member typeRef="/dt/uint16" name="param_id" />
        <Member typeRef="/dt/uint32" name="param_value" />
    </MemberList>
</AggregateArgumentType>

<ArrayArgumentType arrayTypeRef="SetParamEntryType" name="SetParamEntriesType">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="num_params" />
                    <LinearAdjustment intercept="-1" />
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>
```

Add to `<MetaCommandSet>`:
```xml
<MetaCommand name="TC_20_3" shortDescription="TC[20,3] Set parameter values">
    <ArgumentList>
        <Argument argumentTypeRef="/dt/uint8"          name="num_params" />
        <Argument argumentTypeRef="SetParamEntriesType" name="param_entries" />
    </ArgumentList>
    <CommandContainer name="TC_20_3">
        <EntryList>
            <FixedValueEntry name="ccsds-version"   binaryValue="00"   sizeInBits="3" />
            <FixedValueEntry name="ccsds-type"       binaryValue="01"   sizeInBits="1" />
            <FixedValueEntry name="ccsds-sec-hdr"    binaryValue="01"   sizeInBits="1" />
            <FixedValueEntry name="ccsds-apid"       binaryValue="00C8" sizeInBits="11" />
            <FixedValueEntry name="ccsds-seq-flags"  binaryValue="03"   sizeInBits="2" />
            <FixedValueEntry name="ccsds-seq-count"  binaryValue="0000" sizeInBits="14" />
            <FixedValueEntry name="ccsds-length"     binaryValue="0000" sizeInBits="16" />
            <FixedValueEntry name="service-type"     binaryValue="14"   sizeInBits="8" />
            <FixedValueEntry name="service-subtype"  binaryValue="03"   sizeInBits="8" />
            <ArgumentRefEntry argumentRef="num_params" />
            <ArgumentRefEntry argumentRef="param_entries" />
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

**Simulator (on-board emulation)** (not yet in `pus20_simulator.py` — ~20 lines to add):

Emulates the satellite-side behavior: receives TC[20,3], validates each param_id against the on-board parameter store, and applies the new values. Unknown param_ids are skipped with a warning (matching the on-board NACK behavior defined in §6.20.4.1d).

```python
def parse_tc_20_3(data):
    """Parse TC[20,3]: returns list of (param_id, new_value) tuples."""
    if len(data) < 9:
        return None
    if data[6] != 20 or data[7] != 3:
        return None
    n = data[8]
    needed = 9 + 6 * n  # each entry: 2B param_id + 4B value
    if len(data) < needed:
        return None
    entries = []
    for i in range(n):
        pid, val = struct.unpack_from(">HI", data, 9 + i * 6)
        entries.append((pid, val))
    return entries

def handle_tc_20_3(entries, tm_sock):
    for pid, val in entries:
        if pid not in PARAM_STORE:
            log.warning("TC[20,3] unknown param_id 0x%04X, skipping", pid)
            continue
        PARAM_STORE[pid] = val
        log.info("TC[20,3] set 0x%04X → %d", pid, val)
```

**XTCE status**: Compatible with mission convention (all param values fixed to uint32).

**Simulator status**: NOT YET IMPLEMENTED — minor addition to `handle_tc()` dispatch and add above functions.

---

## c) Gaps / Shortcomings

| # | Subtype | Gap | Layer | Impact | Workaround |
|---|---------|-----|-------|--------|------------|
| 1 | TM[20,2], TC[20,3] | **"Deduced" value type**: The spec says the value field type/width is derived from the `param_id`. XTCE cannot express type-dependent encoding. | MCS / YAMCS ground (XTCE) | If parameters have mixed types (int8, float, uint64 etc.), a single XTCE aggregate cannot handle them. | Mission convention: fix all on-board parameters to uint32. Document in ICD. |
| 2 | TC[20,1], TC[20,3] | **Partial execution on-board**: Spec §6.20.4.1f/g says the satellite should process all valid param instructions even when some are invalid. This is purely on-board logic — XTCE encodes the wire format only. | On-board (satellite) | Valid params in a mixed TC will still be processed on-board; invalid ones are skipped. YAMCS has no visibility into per-param on-board validation — it only sees the PUS-1 ACK/NACK. | Simulator handles per-param validation in Python, emulating on-board behavior. XTCE is only the wire format. |
| 3 | TC[20,1], TC[20,3] | **param_id enumeration**: Spec says param_id is "enumerated" (from a mission-defined list). XTCE declares it as raw uint16 — no MDB-level enforcement that an ID is valid. | MCS / YAMCS ground (XTCE) | Ground can send any uint16 without YAMCS rejecting it at encoding time. The satellite rejects invalid IDs with a NACK. | Acceptable for test/sim use. For flight, add YAMCS argument ranges or enumeration lists. |

---

### Overall feasibility verdict

**YES — PUS ST[20] (TC[20,1], TM[20,2], TC[20,3]) is XTCE-only for the MCS scope.** No Java
changes to `yamcs-core` are needed. The three in-scope subtypes require only:

- **MCS (XTCE)**: `pus20.xml` needs `TC_20_3` MetaCommand with `AggregateArgumentType` (standard XTCE pattern, ~30 lines). TC[20,1] and TM[20,2] are already implemented.
- **Simulator (on-board emulation)**: `pus20_simulator.py` needs `parse_tc_20_3` + `handle_tc_20_3` + dispatch (~20 lines) to emulate the satellite-side parameter store update logic.

No structural XTCE workarounds are needed (unlike ST[19] where an embedded raw TC packet
required a non-standard length prefix). The deduced-value issue is resolved by a documented
mission convention, not a workaround.

#### Two-layer artifact table

| Layer | Artifact | Status |
|---|---|---|
| **MCS / YAMCS ground** | `pus20.xml` — TC[20,1] MetaCommand (XTCE encoding) | Already implemented |
| **MCS / YAMCS ground** | `pus20.xml` — TM[20,2] SequenceContainer (XTCE decoding) | Already implemented |
| **MCS / YAMCS ground** | `pus20.xml` — TC[20,3] MetaCommand (XTCE encoding) | Minor addition required (~30 lines) |
| **MCS / YAMCS ground** | `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml` — MDB reference | Verify `pus20.xml` is listed |
| **Simulator (on-board emulation)** | `pus20_simulator.py` — `parse_tc_20_1` + `handle_tc` (TC[20,1] + TM[20,2] response) | Already implemented |
| **Simulator (on-board emulation)** | `pus20_simulator.py` — `parse_tc_20_3` + `handle_tc_20_3` (TC[20,3] handling) | Not yet implemented |

> **Key finding:** YAMCS/MCS sends TC[20,1] and TC[20,3] (ground → satellite) and receives TM[20,2] (satellite → ground). All on-board logic — maintaining the parameter store, validating param_ids, applying set-value commands, and generating TM[20,2] responses — executes on the satellite. The simulator emulates this on-board behavior for ground testing. No `yamcs-core` Java changes are required.
