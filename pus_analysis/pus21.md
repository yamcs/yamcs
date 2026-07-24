# PUS ST[21] Request Sequencing — Analysis & Implementation Plan

**Spec reference**: ECSS-E-ST-70-41C §6.21 (requirements) and §8.21 (packet definitions)
**Required subtypes**: TC[21,1..6], TM[21,7], TC[21,8..9], TM[21,10], TC[21,11], TM[21,12], TC[21,13], TM[21,14]

---

## Ground vs. On-board Responsibility

| Concern | YAMCS / MCS (ground) | Satellite / On-board software |
|---------|----------------------|-------------------------------|
| Uplink TC[21,1..6,8,9,11,13] to spacecraft | **YES** — YAMCS encodes and transmits all TCs | No (receives them) |
| Sequence storage (SEQUENCE_STORE) | No — ground has no visibility into on-board store | **YES** — OBS maintains the authoritative store |
| Sequence activation / scheduling / execution | No — ground only sends the activate command | **YES** — OBS releases each embedded TC per the delay schedule |
| TC relay (releasing embedded TCs during execution) | No | **YES** — OBS sends each embedded TC to target subsystems |
| Receive and decode TM[21,7,10,12,14] telemetry | **YES** — YAMCS decodes incoming packets via XTCE | No (transmits them) |
| Display / archive telemetry | **YES** — YAMCS standard pipeline | No |
| Checksum computation for TC[21,9] | No — ground requests it | **YES** — OBS computes CRC over stored sequence and replies with TM[21,10] |

> **YAMCS/MCS implementation = XTCE only. No Java changes to `yamcs-core` are needed for ST[21].**
> All sequence execution, TC relay, and on-board state management run entirely within the satellite's on-board software. The simulator (Python/Java) emulates this on-board behaviour for ground testing.

**Simulator note**: The simulator emulates the satellite's on-board ST[21] software — it maintains SEQUENCE_STORE, manages sequence threads, relays embedded TCs, and generates TM replies. It is **not** part of the YAMCS/MCS ground implementation.

---

## a) General Context

PUS ST[21] is the **Request Sequencing** service. It manages on-board sequences of TC requests,
releasing them one-by-one with configurable inter-request delays. Multiple sequences can run in
parallel. The service also provides capabilities to load, activate, abort, checksum, and report
on those sequences.

### Key characteristics

| Property | Value |
|----------|-------|
| PUS service type | 21 |
| Sub-service | Request sequencing subservice (single subservice per §6.21.2.1) |
| State maintained | **[ON-BOARD]** SEQUENCE_STORE: dict[seq_id → RequestSequence] — on-board only; not visible to YAMCS ground |
| Background tasks | **[ON-BOARD]** One execution thread per active ("under execution") sequence — on-board only |
| New pus_dt.xml types needed | `string16` (fixed-string, 128 bits) if not already present — MCS/ground XTCE |
| Major XTCE limitations | (1) Embedded TC packets inside repeating array — same gap as ST[19]. (2) Variable-length + optional file path strings in TC[21,2]/TC[21,8]. |

### Spec-defined message types (§8.21.2)

| Subtype | Direction | Name | In scope |
|---------|-----------|------|----------|
| 1  | TC | Direct-load a request sequence | YES |
| 2  | TC | Load a request sequence by reference | YES |
| 3  | TC | Unload a request sequence | YES |
| 4  | TC | Activate a request sequence | YES |
| 5  | TC | Abort a request sequence | YES |
| 6  | TC | Report execution status of each request sequence | YES |
| 7  | TM | Request sequence execution status report | YES |
| 8  | TC | Load by reference and activate a request sequence | YES |
| 9  | TC | Checksum a request sequence | YES |
| 10 | TM | Request sequence checksum report | YES |
| 11 | TC | Report the content of a request sequence | YES |
| 12 | TM | Request sequence content report | YES |
| 13 | TC | Abort all request sequences and report | YES |
| 14 | TM | Aborted request sequence report | YES |

### Request sequence model (§6.21.4)

Each request sequence contains:
- A unique identifier (`request_sequence_ID`): fixed character-string — **mission fixes length to 16 bytes**.
- An ordered list of request entries, each entry = `{ TC packet (raw bytes), delay (relative time) }`.
- An execution status: `inactive` (0) or `under execution` (1) — Table 8-22.

```
SEQUENCE_STORE: dict[str, RequestSequence]

RequestSequence:
  id          : str           # 16-byte null-padded ASCII string
  status      : "inactive" | "under_execution"
  entries     : list[(raw_tc: bytes, delay_ms: int)]
  exec_thread : Thread | None # active only while under_execution
```

### Sequence lifecycle

All steps below occur **on-board**. Ground sends TCs to trigger each transition; YAMCS is responsible only for uplink (steps marked **[GROUND → SAT]**). Everything else is on-board logic.

```
[GROUND → SAT]  load (TC[21,1] or TC[21,2])  → [ON-BOARD] status = "inactive"
[GROUND → SAT]  activate (TC[21,4])           → [ON-BOARD] status = "under_execution"
                                                 [ON-BOARD] start release thread
                [ON-BOARD] thread releases each TC, waits delay, continues
                [ON-BOARD] after last TC + delay → status = "inactive"
[GROUND → SAT]  abort (TC[21,5])              → [ON-BOARD] cancel thread, status = "inactive"
[GROUND → SAT]  unload (TC[21,3])             → [ON-BOARD] only if inactive; remove from store
[ON-BOARD]      (periodic or on-request)      → [SAT → GROUND] TM[21,7/10/12/14] reports
```

TC[21,8] is a combined load-by-reference + activate in one TC.

---

## b) Per-Subtype Context and Implementation Plan

---

### TC[21,1] — Direct-load a request sequence

**Spec §6.21.5.2 + §8.21.2.1**

**Purpose**: **[GROUND → SAT]** Ground encodes and uplinks the full sequence content (TC packets + delays); the on-board software receives it and loads it into SEQUENCE_STORE.

**Packet structure (Figure 8-228)**:

```
CCSDS primary header (6 bytes)
  service_type    = 21
  service_subtype = 1
  request_sequence_ID   (fixed character-string)
  N                     (unsigned integer — number of entries)
  N × { request (TC packet), delay (relative time) }
```

**XTCE limitation**: The repeating body `N × {TC_packet, delay}` contains variable-length TC
packets — the same structural gap as ST[19]. XTCE cannot express variable-length elements
inside an array.

**Mission workaround — length prefix per entry**:

```
[8..23]  request_sequence_ID  (16 bytes, null-padded fixed string)
[24]     N                    (uint8)
[25+]    N × { tc_len:uint16, TC_packet (tc_len bytes), delay_ms:uint32 }
```

**XTCE implementation** (`pus21.xml`):

```xml
<!-- Declare seq_id as fixed-length string + N only.
     Python parses the entry block beyond these fields. -->
<StringArgumentType name="SeqIdArgType">
    <StringDataEncoding>
        <SizeInBits>
            <Fixed><FixedValue>128</FixedValue></Fixed>
        </SizeInBits>
    </StringDataEncoding>
</StringArgumentType>

<MetaCommand name="TC_21_1" shortDescription="TC[21,1] Direct-load a request sequence">
    <ArgumentList>
        <Argument argumentTypeRef="SeqIdArgType" name="seq_id" />
        <Argument argumentTypeRef="/dt/uint8"    name="num_entries" />
        <!-- entry body (tc_len, tc_bytes, delay) is raw binary, parsed in Python -->
    </ArgumentList>
    <CommandContainer name="TC_21_1">
        <EntryList>
            <FixedValueEntry name="ccsds-version"   binaryValue="00"   sizeInBits="3" />
            <FixedValueEntry name="ccsds-type"       binaryValue="01"   sizeInBits="1" />
            <FixedValueEntry name="ccsds-sec-hdr"    binaryValue="01"   sizeInBits="1" />
            <FixedValueEntry name="ccsds-apid"       binaryValue="00C8" sizeInBits="11" />
            <FixedValueEntry name="ccsds-seq-flags"  binaryValue="03"   sizeInBits="2" />
            <FixedValueEntry name="ccsds-seq-count"  binaryValue="0000" sizeInBits="14" />
            <FixedValueEntry name="ccsds-length"     binaryValue="0000" sizeInBits="16" />
            <FixedValueEntry name="service-type"     binaryValue="15"   sizeInBits="8" />
            <FixedValueEntry name="service-subtype"  binaryValue="01"   sizeInBits="8" />
            <ArgumentRefEntry argumentRef="seq_id" />
            <ArgumentRefEntry argumentRef="num_entries" />
            <!-- Entry block appended by ground tool; Python parses it -->
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

**Simulator (on-board emulation)**:

```python
def parse_tc_21_1(data):
    seq_id = data[8:24].decode('ascii').rstrip('\x00')
    n = data[24]
    offset = 25
    entries = []
    for _ in range(n):
        tc_len = struct.unpack_from(">H", data, offset)[0]; offset += 2
        raw_tc = data[offset:offset + tc_len]; offset += tc_len
        delay_ms = struct.unpack_from(">I", data, offset)[0]; offset += 4
        entries.append((raw_tc, delay_ms))
    return seq_id, entries

def handle_tc_21_1(data):
    # [ON-BOARD] Receive TC from ground, parse, store in on-board SEQUENCE_STORE
    seq_id, entries = parse_tc_21_1(data)
    if seq_id in SEQUENCE_STORE:
        log.warning("TC[21,1] seq '%s' already loaded, rejecting", seq_id)
        return
    SEQUENCE_STORE[seq_id] = RequestSequence(seq_id, "inactive", entries)
    log.info("TC[21,1] loaded seq '%s' with %d entries", seq_id, len(entries))
```

**XTCE status**: Partial. XTCE declares seq_id + N; Python handles the entry block.

---

### TC[21,2] — Load a request sequence by reference

**Spec §6.21.5.3 + §8.21.2.2**

**Purpose**: **[GROUND → SAT]** Ground uplinks a reference (file path) pointing to an on-board file; **[ON-BOARD]** the on-board software reads and loads the sequence from its local filesystem.

**Packet structure (Figure 8-229)**:

```
request_sequence_ID   (fixed character-string)
[optional] file_path:
    repository_path   (variable character-string)
    file_name         (variable character-string)
```

**XTCE limitation**: Variable-length strings and an optional block cannot be expressed in XTCE.

**Mission workaround — two MetaCommand variants**:

- `TC_21_2_no_path`: seq_id only; loading policy determines which file to use.
- `TC_21_2_with_path`: seq_id + `repo_path_len:uint8` + `repo_path_bytes` + `file_name_len:uint8` + `file_name_bytes`.

```xml
<MetaCommand name="TC_21_2_no_path"
             shortDescription="TC[21,2] Load sequence by reference (no explicit path)">
    <ArgumentList>
        <Argument argumentTypeRef="SeqIdArgType" name="seq_id" />
    </ArgumentList>
    <CommandContainer name="TC_21_2_no_path">
        <EntryList>
            <!-- CCSDS header -->
            <FixedValueEntry name="service-type"    binaryValue="15" sizeInBits="8" />
            <FixedValueEntry name="service-subtype" binaryValue="02" sizeInBits="8" />
            <ArgumentRefEntry argumentRef="seq_id" />
        </EntryList>
    </CommandContainer>
</MetaCommand>

<!-- TC_21_2_with_path: seq_id + raw path bytes (parsed in Python) -->
```

**Simulator (on-board emulation)**:

```python
def parse_tc_21_2(data):
    seq_id = data[8:24].decode('ascii').rstrip('\x00')
    offset = 24
    repo_path, file_name = "", ""
    if len(data) > offset:
        repo_len = data[offset]; offset += 1
        repo_path = data[offset:offset+repo_len].decode(); offset += repo_len
        file_len = data[offset]; offset += 1
        file_name = data[offset:offset+file_len].decode()
    return seq_id, repo_path, file_name

def handle_tc_21_2(data):
    # [ON-BOARD] Receive TC from ground; look up file on the on-board filesystem and load
    seq_id, repo_path, file_name = parse_tc_21_2(data)
    if seq_id in SEQUENCE_STORE:
        log.warning("TC[21,2] seq '%s' already loaded", seq_id)
        return
    path = os.path.join(repo_path or SEQUENCE_REPO_PATH, file_name or (seq_id + ".bin"))
    entries = load_sequence_file(path)
    SEQUENCE_STORE[seq_id] = RequestSequence(seq_id, "inactive", entries)
    log.info("TC[21,2] loaded seq '%s' from %s", seq_id, path)
```

**XTCE status**: Partial. Two MetaCommand variants cover both cases; path field parsed in Python.

---

### TC[21,3] — Unload a request sequence

**Spec §6.21.5.4 + §8.21.2.3**

**Purpose**: **[GROUND → SAT]** Ground uplinks an unload command; **[ON-BOARD]** the on-board software removes the named sequence from its store (only if it is not currently executing).

**Packet structure (Figure 8-230)**:

```
request_sequence_ID   (fixed character-string)
```

**Reject if**: seq not loaded, or seq is "under execution".

**XTCE implementation**:

```xml
<MetaCommand name="TC_21_3" shortDescription="TC[21,3] Unload a request sequence">
    <ArgumentList>
        <Argument argumentTypeRef="SeqIdArgType" name="seq_id" />
    </ArgumentList>
    <CommandContainer name="TC_21_3">
        <EntryList>
            <FixedValueEntry name="service-type"    binaryValue="15" sizeInBits="8" />
            <FixedValueEntry name="service-subtype" binaryValue="03" sizeInBits="8" />
            <ArgumentRefEntry argumentRef="seq_id" />
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

**Simulator (on-board emulation)**:

```python
def handle_tc_21_3(data):
    # [ON-BOARD] Receive unload TC from ground; remove sequence from on-board store if inactive
    seq_id = data[8:24].decode('ascii').rstrip('\x00')
    seq = SEQUENCE_STORE.get(seq_id)
    if seq is None or seq.status == "under_execution":
        log.warning("TC[21,3] cannot unload '%s' (not loaded or under execution)", seq_id)
        return
    del SEQUENCE_STORE[seq_id]
    log.info("TC[21,3] unloaded seq '%s'", seq_id)
```

**XTCE status**: Fully compatible.

---

### TC[21,4] — Activate a request sequence

**Spec §6.21.5.5 + §8.21.2.4**

**Purpose**: **[GROUND → SAT]** Ground uplinks the activate command; **[ON-BOARD]** the on-board software begins releasing embedded TC requests from the named sequence according to the configured inter-request delays.

**Packet structure (Figure 8-231)**:

```
request_sequence_ID   (fixed character-string)
```

**Reject if**: seq not loaded, already under execution, or insufficient resources.

**XTCE implementation**:

```xml
<MetaCommand name="TC_21_4" shortDescription="TC[21,4] Activate a request sequence">
    <ArgumentList>
        <Argument argumentTypeRef="SeqIdArgType" name="seq_id" />
    </ArgumentList>
    <CommandContainer name="TC_21_4">
        <EntryList>
            <FixedValueEntry name="service-type"    binaryValue="15" sizeInBits="8" />
            <FixedValueEntry name="service-subtype" binaryValue="04" sizeInBits="8" />
            <ArgumentRefEntry argumentRef="seq_id" />
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

**Simulator (on-board emulation)**:

```python
def handle_tc_21_4(data):
    # [ON-BOARD] Receive activate TC from ground; start on-board execution thread
    seq_id = data[8:24].decode('ascii').rstrip('\x00')
    seq = SEQUENCE_STORE.get(seq_id)
    if seq is None or seq.status == "under_execution":
        log.warning("TC[21,4] cannot activate '%s'", seq_id)
        return
    seq.status = "under_execution"
    seq.exec_thread = threading.Thread(target=_run_sequence, args=(seq,), daemon=True)
    seq.exec_thread.start()
    log.info("TC[21,4] activated seq '%s'", seq_id)

def _run_sequence(seq):
    # [ON-BOARD] Release each embedded TC per its inter-request delay (on-board logic only)
    for raw_tc, delay_ms in seq.entries:
        TC_RELAY_SOCK.sendto(raw_tc, TC_RELAY_ADDR)
        time.sleep(delay_ms / 1000.0)
    seq.status = "inactive"
    seq.exec_thread = None
    log.info("Sequence '%s' completed", seq.id)
```

**XTCE status**: Fully compatible.

---

### TC[21,5] — Abort a request sequence

**Spec §6.21.5.7 + §8.21.2.5**

**Purpose**: **[GROUND → SAT]** Ground uplinks an abort command; **[ON-BOARD]** the on-board software stops the running sequence and resets its status to inactive.

**Packet structure (Figure 8-232)**:

```
request_sequence_ID   (fixed character-string)
```

**Reject if**: seq not loaded, or seq is "inactive".

**XTCE implementation**: Same pattern as TC[21,3]/TC[21,4] with subtype=5.

**Simulator (on-board emulation)**:

```python
def handle_tc_21_5(data):
    # [ON-BOARD] Receive abort TC from ground; signal execution thread to stop
    seq_id = data[8:24].decode('ascii').rstrip('\x00')
    seq = SEQUENCE_STORE.get(seq_id)
    if seq is None or seq.status == "inactive":
        log.warning("TC[21,5] cannot abort '%s' (not loaded or already inactive)", seq_id)
        return
    seq.status = "inactive"   # thread checks status and exits on next iteration
    log.info("TC[21,5] aborted seq '%s'", seq_id)
```

**XTCE status**: Fully compatible.

---

### TC[21,6] — Report execution status of each request sequence

**Spec §6.21.6 + §8.21.2.6**

**Purpose**: **[GROUND → SAT]** Ground uplinks a status-report request; **[ON-BOARD]** the on-board software iterates SEQUENCE_STORE and replies with TM[21,7]. **[SAT → GROUND]** YAMCS receives and decodes TM[21,7].

**Packet structure**: Application data field omitted (zero payload).

**XTCE implementation**:

```xml
<MetaCommand name="TC_21_6"
             shortDescription="TC[21,6] Report execution status of each request sequence">
    <!-- No <ArgumentList> — zero application data -->
    <CommandContainer name="TC_21_6">
        <EntryList>
            <FixedValueEntry name="service-type"    binaryValue="15" sizeInBits="8" />
            <FixedValueEntry name="service-subtype" binaryValue="06" sizeInBits="8" />
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

**Simulator (on-board emulation)**: **[ON-BOARD]** Iterates SEQUENCE_STORE, builds and downlinks TM[21,7].

**XTCE status**: Fully compatible.

---

### TM[21,7] — Request sequence execution status report

**Spec §6.21.6 + §8.21.2.7**

**Purpose**: **[SAT → GROUND]** On-board software downlinks a status report listing all loaded sequences and their execution state; **[GROUND]** YAMCS receives and decodes it via the XTCE definition below.

**Packet structure (Figure 8-233)**:

```
[8]     N                   (uint8, number of entries)
[9+]    N × { request_sequence_ID (16 bytes), execution_status (uint8) }
```

Execution status: `inactive`=0, `under execution`=1 (Table 8-22).

**XTCE implementation**:

```xml
<AggregateParameterType name="SeqStatusEntryType">
    <MemberList>
        <Member typeRef="SeqIdParamType"  name="seq_id" />
        <Member typeRef="/dt/uint8"       name="exec_status" />
    </MemberList>
</AggregateParameterType>

<ArrayParameterType arrayTypeRef="SeqStatusEntryType" name="SeqStatusEntriesType">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ParameterInstanceRef parameterRef="num_entries" />
                    <LinearAdjustment intercept="-1" />
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayParameterType>

<SequenceContainer name="TM_21_7"
                   shortDescription="TM[21,7] Request sequence execution status report">
    <EntryList>
        <ParameterRefEntry parameterRef="num_entries" />
        <ArrayParameterRefEntry parameterRef="seq_status_entries" />
    </EntryList>
    <BaseContainer containerRef="PUS21Packet">
        <RestrictionCriteria>
            <ComparisonList>
                <Comparison parameterRef="service_type"    value="21" />
                <Comparison parameterRef="service_subtype" value="7" />
            </ComparisonList>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Simulator (on-board emulation)**:

```python
def build_tm_21_7() -> bytes:
    # [ON-BOARD] Build TM report from on-board SEQUENCE_STORE and downlink to ground
    entries = list(SEQUENCE_STORE.values())
    n = len(entries)
    user_data = struct.pack(">B", n)
    for seq in entries:
        seq_id_bytes = seq.id.encode('ascii').ljust(16, b'\x00')[:16]
        status_byte = 1 if seq.status == "under_execution" else 0
        user_data += seq_id_bytes + struct.pack(">B", status_byte)
    return _build_tm_packet(21, 7, user_data)
```

**XTCE status**: Fully compatible with mission-fixed 16-byte string length.

---

### TC[21,8] — Load by reference and activate a request sequence

**Spec §6.21.5.6 + §8.21.2.8**

**Purpose**: **[GROUND → SAT]** Combined TC[21,2] + TC[21,4] uplinkable in one command; **[ON-BOARD]** the on-board software loads the sequence from its filesystem and immediately starts execution.

**Packet structure (Figure 8-234)**: Identical to TC[21,2] (seq_id + optional file path).

**XTCE implementation**: Same dual-MetaCommand approach as TC[21,2], with subtype=8.

**Simulator (on-board emulation)**: **[ON-BOARD]** Parse same as TC[21,2]; after loading, immediately activate (set status to `under_execution` and start `_run_sequence` thread).

**XTCE status**: Partial (same workaround as TC[21,2]).

---

### TC[21,9] — Checksum a request sequence

**Spec §6.21.7 + §8.21.2.9**

**Purpose**: **[GROUND → SAT]** Ground uplinks a checksum request; **[ON-BOARD]** the on-board software computes a CRC over the stored sequence and replies with TM[21,10]. **[SAT → GROUND]** YAMCS receives and decodes TM[21,10].

**Packet structure (Figure 8-235)**:

```
request_sequence_ID   (fixed character-string)
```

**XTCE implementation**: Same as TC[21,3] with subtype=9.

**Simulator (on-board emulation)**:

```python
def handle_tc_21_9(data, tm_sock):
    # [ON-BOARD] Receive checksum request from ground; compute CRC over on-board sequence; downlink TM[21,10]
    seq_id = data[8:24].decode('ascii').rstrip('\x00')
    seq = SEQUENCE_STORE.get(seq_id)
    if seq is None:
        log.warning("TC[21,9] seq '%s' not loaded", seq_id)
        return
    raw = b"".join(raw_tc + struct.pack(">I", dms) for raw_tc, dms in seq.entries)
    checksum = crc16(raw)
    pkt = build_tm_21_10(seq_id, checksum)
    tm_sock.sendto(pkt, TM_ADDR)
```

**XTCE status**: Fully compatible.

---

### TM[21,10] — Request sequence checksum report

**Spec §6.21.7 + §8.21.2.10**

**Purpose**: **[SAT → GROUND]** On-board software downlinks the computed checksum of the requested sequence; **[GROUND]** YAMCS receives and decodes it via the XTCE definition below.

**Packet structure (Figure 8-236)**:

```
[8..23]  request_sequence_ID  (16 bytes)
[24..25] calculated_checksum_value  (uint16, CRC-16)
```

**XTCE implementation**:

```xml
<SequenceContainer name="TM_21_10"
                   shortDescription="TM[21,10] Request sequence checksum report">
    <EntryList>
        <ParameterRefEntry parameterRef="seq_id" />
        <ParameterRefEntry parameterRef="checksum_value" />
    </EntryList>
    <BaseContainer containerRef="PUS21Packet">
        <RestrictionCriteria>
            <ComparisonList>
                <Comparison parameterRef="service_type"    value="21" />
                <Comparison parameterRef="service_subtype" value="10" />
            </ComparisonList>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Simulator (on-board emulation)**:

```python
def build_tm_21_10(seq_id: str, checksum: int) -> bytes:
    # [ON-BOARD] Build checksum report and downlink to ground
    seq_id_bytes = seq_id.encode('ascii').ljust(16, b'\x00')[:16]
    user_data = seq_id_bytes + struct.pack(">H", checksum)
    return _build_tm_packet(21, 10, user_data)
```

**XTCE status**: Fully compatible (fixed-length fields only).

---

### TC[21,11] — Report the content of a request sequence

**Spec §6.21.8 + §8.21.2.11**

**Purpose**: **[GROUND → SAT]** Ground uplinks a content-report request; **[ON-BOARD]** the on-board software reads its SEQUENCE_STORE and replies with TM[21,12]. **[SAT → GROUND]** YAMCS receives and decodes TM[21,12].

**Packet structure (Figure 8-237)**:

```
request_sequence_ID   (fixed character-string)
```

**XTCE implementation**: Same as TC[21,3] with subtype=11.

**Simulator (on-board emulation)**: **[ON-BOARD]** Validates sequence is loaded in on-board store; builds and downlinks TM[21,12].

**XTCE status**: Fully compatible.

---

### TM[21,12] — Request sequence content report

**Spec §6.21.8 + §8.21.2.12**

**Purpose**: **[SAT → GROUND]** On-board software downlinks the full ordered list of entries (TC packets + delays) for the requested sequence; **[GROUND]** YAMCS receives and partially decodes it (seq_id + N via XTCE; entry body via Python workaround).

**Packet structure (Figure 8-238)**:

```
[8..23]  request_sequence_ID  (16 bytes)
[24]     N                    (uint8)
[25+]    N × { tc_len:uint16, TC_packet (tc_len bytes), delay_ms:uint32 }
```

Same embedded-TC limitation as TC[21,1] — XTCE declares seq_id + N only; Python builds the rest.

**Simulator (on-board emulation)**:

```python
def build_tm_21_12(seq) -> bytes:
    # [ON-BOARD] Read sequence entries from on-board store; build and downlink TM content report
    seq_id_bytes = seq.id.encode('ascii').ljust(16, b'\x00')[:16]
    n = len(seq.entries)
    user_data = seq_id_bytes + struct.pack(">B", n)
    for raw_tc, delay_ms in seq.entries:
        user_data += struct.pack(">H", len(raw_tc)) + raw_tc
        user_data += struct.pack(">I", delay_ms)
    return _build_tm_packet(21, 12, user_data)
```

**XTCE status**: Partial — same embedded-TC workaround as TC[21,1].

---

### TC[21,13] — Abort all request sequences and report

**Spec §6.21.5.8 + §8.21.2.13**

**Purpose**: **[GROUND → SAT]** Ground uplinks a global abort command; **[ON-BOARD]** the on-board software aborts every sequence currently under execution and downlinks a single TM[21,14] listing them. **[SAT → GROUND]** YAMCS receives and decodes TM[21,14].

**Packet structure**: Application data field omitted (zero payload).

**XTCE implementation**:

```xml
<MetaCommand name="TC_21_13"
             shortDescription="TC[21,13] Abort all request sequences and report">
    <!-- No <ArgumentList> — zero application data -->
    <CommandContainer name="TC_21_13">
        <EntryList>
            <FixedValueEntry name="service-type"    binaryValue="15" sizeInBits="8" />
            <FixedValueEntry name="service-subtype" binaryValue="0D" sizeInBits="8" />
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

**Simulator (on-board emulation)**:

```python
def handle_tc_21_13(tm_sock):
    # [ON-BOARD] Receive global abort from ground; abort all running sequences; downlink TM[21,14]
    aborted = []
    for seq in SEQUENCE_STORE.values():
        if seq.status == "under_execution":
            seq.status = "inactive"   # thread exits on next check
            aborted.append(seq.id)
    pkt = build_tm_21_14(aborted)
    tm_sock.sendto(pkt, TM_ADDR)
    log.info("TC[21,13] aborted %d sequences", len(aborted))
```

**XTCE status**: Fully compatible.

---

### TM[21,14] — Aborted request sequence report

**Spec §6.21.5.8 + §8.21.2.14**

**Purpose**: **[SAT → GROUND]** On-board software downlinks the identifiers of all sequences aborted by TC[21,13]; **[GROUND]** YAMCS receives and decodes it via the XTCE definition below.

**Packet structure (Figure 8-239)**:

```
[8]     N                   (uint8)
[9+]    N × { request_sequence_ID (16 bytes) }
```

**XTCE implementation**:

```xml
<ArrayParameterType arrayTypeRef="SeqIdParamType" name="AbortedSeqIdsType">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ParameterInstanceRef parameterRef="num_aborted" />
                    <LinearAdjustment intercept="-1" />
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayParameterType>

<SequenceContainer name="TM_21_14"
                   shortDescription="TM[21,14] Aborted request sequence report">
    <EntryList>
        <ParameterRefEntry parameterRef="num_aborted" />
        <ArrayParameterRefEntry parameterRef="aborted_seq_ids" />
    </EntryList>
    <BaseContainer containerRef="PUS21Packet">
        <RestrictionCriteria>
            <ComparisonList>
                <Comparison parameterRef="service_type"    value="21" />
                <Comparison parameterRef="service_subtype" value="14" />
            </ComparisonList>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Simulator (on-board emulation)**:

```python
def build_tm_21_14(aborted_ids: list) -> bytes:
    # [ON-BOARD] Build aborted-sequences report and downlink to ground
    n = len(aborted_ids)
    user_data = struct.pack(">B", n)
    for sid in aborted_ids:
        user_data += sid.encode('ascii').ljust(16, b'\x00')[:16]
    return _build_tm_packet(21, 14, user_data)
```

**XTCE status**: Fully compatible (array of fixed-length strings).

---

## c) Gaps / Shortcomings

| # | Subtype(s) | Gap | Impact | Workaround |
|---|-----------|-----|--------|------------|
| 1 | TC[21,1], TM[21,12] | **Embedded TC packets in repeating array** — XTCE cannot express variable-length elements inside an array. Same structural limitation as ST[19]. | Ground tools cannot use YAMCS command encoding for the entry body; must append raw binary manually. | Mission-convention `tc_len:uint16` prefix before each TC packet. XTCE declares `seq_id` + `N` only; Python parses the entry block. |
| 2 | TC[21,2], TC[21,8] | **Variable-length + optional file path strings** — XTCE cannot express variable-length strings or optional blocks. | Two different packet layouts (with/without path) cannot share a single MetaCommand. | Two MetaCommand variants per subtype (`_no_path` / `_with_path`). Path strings encoded as length-prefixed byte sequences; Python decodes them. |
| 3 | All | **request_sequence_ID length** — spec says "fixed character-string" but does not define the length. | Wire format ambiguous without a mission decision. | Mission fixes to 16 bytes (128 bits); document in ICD. |
| 4 | TC[21,1], TM[21,12] | **`delay: relative time`** — PUS relative time encoding is not defined at this spec level. | Cannot express duration without a mission encoding decision. | Mission convention: encode delay as **uint32 milliseconds** (big-endian). |
| 5 | TC[21,1], TM[21,12] | **TC relay logic** — when a sequence executes, the embedded TC packets must be "released" (sent to the target subsystem). This is **on-board logic only**; YAMCS/MCS is not involved in sequence execution. | Not a YAMCS gap — YAMCS only uplinks the TC[21,x] commands and decodes returned TM. | **[ON-BOARD]** The on-board software (emulated by simulator) extracts raw TC bytes from the sequence and relays them to target subsystems; YAMCS only sees the ST[21] wrapper packet. |

### Overall feasibility verdict

**YES — PUS ST[21] (all 14 in-scope subtypes) can be implemented for the MCS ground scope with XTCE only. No Java changes to `yamcs-core` are needed.**

All sequence storage, scheduling, execution, TC relay, and checksum computation are **on-board responsibilities**. YAMCS/MCS is responsible only for:
- Encoding and uplinking TC[21,1..6,8,9,11,13] (XTCE MetaCommand definitions).
- Receiving and decoding TM[21,7,10,12,14] (XTCE SequenceContainer definitions).

Two categories of XTCE workarounds are needed for the ground (MCS) XTCE definitions:

1. **Embedded TC arrays** (TC[21,1], TM[21,12]): use the established ST[19] length-prefix
   workaround. XTCE covers only the fixed header; Python/ground-tool handles the entry body.

2. **Optional variable-length file paths** (TC[21,2], TC[21,8]): use dual MetaCommand variants
   (`_no_path` / `_with_path`).

The remaining 10 subtypes are fully XTCE-compatible using standard patterns (fixed-string
arguments, zero-payload MetaCommands, AggregateParameterType arrays).

#### Two-layer artifact summary

| Layer | Artifact | Purpose |
|-------|----------|---------|
| **MCS / YAMCS ground** | `pus21.xml` (XTCE) | TC encoding (MetaCommands) + TM decoding (SequenceContainers) |
| **MCS / YAMCS ground** | Ground tool / script (Python, optional) | Assembles variable-length TC[21,1] entry body; parses TM[21,12] entry body |
| **Simulator (on-board emulation)** | `Pus21Service.java` / `pus21_sim.py` | Emulates on-board SEQUENCE_STORE, sequence execution, TC relay, TM generation |

> **Key finding**: YAMCS/MCS is purely a TC uplink and TM decode layer for ST[21]. All sequencing intelligence — loading, activating, scheduling, TC relay, abort, checksum — runs on-board. The simulator emulates this on-board intelligence for ground testing; it is not part of the operational YAMCS/MCS software.

---

## d) Simulator On-board Emulation (Java)

> **Note**: This section describes the **simulator** implementation that emulates on-board ST[21] behaviour for ground testing. These are **not** changes to `yamcs-core` or the operational MCS ground software. The MCS ground implementation is XTCE-only (`pus21.xml`).

The following Java changes are required **in the simulator only** to emulate the on-board Request Sequencing service.

### New file: `Pus21Service.java`

**Path**: `simulator/src/main/java/org/yamcs/simulator/pus/Pus21Service.java`

**Layer**: Simulator (on-board emulation) — NOT part of `yamcs-core` or YAMCS MCS ground software.

Follows the same pattern as `Pus11Service.java`. Key design points:

| Aspect | Decision |
|--------|----------|
| State | `LinkedHashMap<String, RequestSequence> sequenceStore` — keyed on `seq_id` |
| seq_id encoding | 16-byte null-padded ASCII; `readSeqId` / `encodeSeqId` helpers |
| Delay encoding | `uint32 milliseconds` — stored as `int delayMs` in `Entry` |
| TC relay | `pusSimulator.processTc(new PusTcPacket(entry.rawTc))` — same mechanism as ST[11] |
| Thread model | One daemon thread per executing sequence (`"pus21-seq-<id>"`); `seq.active` is `volatile` for abort signalling |
| Synchronization | `executeTc` is `synchronized` on the service instance; `runSequence` re-enters with `synchronized(this)` at completion to reset `active` |
| CRC (TC[21,9]) | `CrcCciitCalculator.compute(raw, 0, len)` over concatenation of `{tc_bytes ‖ delay_ms(uint32)}` for all entries |
| TC[21,2]/TC[21,8] | NACK with `COMPL_ERR_FILE_NOT_SUPPORTED` — no filesystem in the simulator |

**Subtype dispatch table**:

```java
case 1  -> loadDirectly(tc)          // TC[21,1]: parse seq_id+N+entries, store inactive
case 2  -> loadByReference(tc, false) // TC[21,2]: NACK (no filesystem)
case 3  -> unloadSequence(tc)         // TC[21,3]: remove if inactive
case 4  -> activateSequence(tc)       // TC[21,4]: set active, spawn thread
case 5  -> abortSequence(tc)          // TC[21,5]: set active=false (thread exits on next check)
case 6  -> reportExecutionStatus(tc)  // TC[21,6]: build TM[21,7]
case 8  -> loadByReference(tc, true)  // TC[21,8]: NACK (no filesystem)
case 9  -> checksumSequence(tc)       // TC[21,9]: build TM[21,10]
case 11 -> reportSequenceContent(tc)  // TC[21,11]: build TM[21,12]
case 13 -> abortAll(tc)               // TC[21,13]: abort all active, build TM[21,14]
```

TM subtype 7, 10, 12, 14 are generated inside the TC handlers above (not dispatched separately).

---

### Modified file: `PusSimulator.java`

**Layer**: Simulator (on-board emulation) — NOT part of `yamcs-core` or YAMCS MCS ground software.

Three changes needed:

**1. Field declaration** (alongside other service fields):
```java
Pus21Service pus21Service;
```

**2. Constructor** (alongside other service instantiations):
```java
pus21Service = new Pus21Service(this);
```

**3. `doStart()`** (alongside other `start()` calls):
```java
pus21Service.start();
```

**4. `executePendingCommands()` switch** (adds case 21):
```java
case 21 -> pus21Service.executeTc(commandPacket);
```

---

### Optional: `yamcs.pus.yaml` MDB entry

**Layer**: MCS / YAMCS ground — this registers the XTCE definitions with the YAMCS ground server.

Once the `pus21.xml` MDB file is generated, add it to the `mdb` list:

```yaml
  - type: "xtce"
    spec: "mdb/pus21.xml"
```

---

### Gap summary for simulator (on-board emulation) Java implementation

> These gaps are all simulator/on-board concerns. They are not MCS/YAMCS ground gaps — YAMCS ground only uplinks TCs and decodes TM.

| Gap | Layer | Impact | How handled |
|-----|-------|--------|-------------|
| TC[21,1] entry body (variable-length TC + delay) | **Simulator (on-board emulation)** | On-board must parse the mission-convention `tc_len:uint16` prefix format | `Pus21Service.loadDirectly()` reads the entry block manually from `getUserDataBuffer()` |
| TC[21,2]/TC[21,8] file loading | **Simulator (on-board emulation)** | Simulator has no on-board filesystem | NACK with error code 14; ground must use TC[21,1] instead for simulator testing |
| TC relay during sequence execution | **Simulator (on-board emulation)** | On-board must relay embedded TCs to target subsystems | `runSequence()` calls `pusSimulator.processTc()` — same approach as ST[11] scheduled TC release |
| TM[21,12] variable-length content | **Simulator (on-board emulation)** | On-board builds content report with variable-length entry body | Built entirely in `reportSequenceContent()` by manual ByteBuffer assembly |

**Configuration note** (processors.yaml — simulator config, not MCS ground):

```yaml
# In processors.yaml, replace StreamTcCommandReleaser with:
- class: org.yamcs.pus.Pus21RequestSequencingService
  args:
    apid: 1
    timeEncoding:
      implicitPfield: false
      pfield: 0x2f
```