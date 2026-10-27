# PUS ST[21] Request Sequencing — Analysis & Implementation Plan

**Spec reference**: ECSS-E-ST-70-41C §6.21 (requirements) and §8.21 (packet definitions)
**Required subtypes**: TC[21,1..6], TM[21,7], TC[21,8..9], TM[21,10], TC[21,11], TM[21,12], TC[21,13], TM[21,14]

---

## Contents

- [Ground vs. On-board Responsibility](#ground-vs-on-board-responsibility)
- [a) General Context](#a-general-context)
  - [Key characteristics](#key-characteristics)
  - Spec-defined message types (§8.21.2)
  - Request sequence model (§6.21.4)
  - [Sequence lifecycle](#sequence-lifecycle)
- [b) Per-Subtype Context and Implementation Plan](#b-per-subtype-context-and-implementation-plan)
  - [TC\[21,1\] — Direct-load a request sequence](#tc211--direct-load-a-request-sequence)
  - [TC\[21,2\] — Load a request sequence by reference](#tc212--load-a-request-sequence-by-reference)
  - [TC\[21,3\] — Unload a request sequence](#tc213--unload-a-request-sequence)
  - [TC\[21,4\] — Activate a request sequence](#tc214--activate-a-request-sequence)
  - [TC\[21,5\] — Abort a request sequence](#tc215--abort-a-request-sequence)
  - [TC\[21,6\] — Report execution status of each request sequence](#tc216--report-execution-status-of-each-request-sequence)
  - [TM\[21,7\] — Request sequence execution status report](#tm217--request-sequence-execution-status-report)
  - [TC\[21,8\] — Load by reference and activate a request sequence](#tc218--load-by-reference-and-activate-a-request-sequence)
  - [TC\[21,9\] — Checksum a request sequence](#tc219--checksum-a-request-sequence)
  - [TM\[21,10\] — Request sequence checksum report](#tm2110--request-sequence-checksum-report)
  - [TC\[21,11\] — Report the content of a request sequence](#tc2111--report-the-content-of-a-request-sequence)
  - [TM\[21,12\] — Request sequence content report](#tm2112--request-sequence-content-report)
  - [TC\[21,13\] — Abort all request sequences and report](#tc2113--abort-all-request-sequences-and-report)
  - [TM\[21,14\] — Aborted request sequence report](#tm2114--aborted-request-sequence-report)
- [c) Gaps / Shortcomings](#c-gaps--shortcomings)
  - §6.21.9 subservice observables — out of scope
  - [Overall feasibility verdict](#overall-feasibility-verdict)
- [d) Simulator On-board Emulation (Java)](#d-simulator-on-board-emulation-java)
  - [New file: `Pus21Service.java`](#new-file-pus21servicejava)
  - [Modified file: `PusSimulator.java`](#modified-file-pussimulatorjava)
  - [Optional: `yamcs.pus.yaml` MDB entry](#optional-yamcspusyaml-mdb-entry)
  - [Gap summary for simulator implementation](#gap-summary-for-simulator-on-board-emulation-java-implementation)
- [e) MCS-side (`yamcs-core`) support: embedded-entry builder](#e-mcs-side-yamcs-core-support-one-embedded-entry-builder)
  - [Why this is still needed (recap)](#why-this-is-still-needed-recap)
  - [`Pus21EmbeddedEntryBuilder`](#pus21embeddedentrybuilder)
  - [Open questions for this section](#open-questions-for-this-section)
- [f) Testing Methodology — Actual Implementation](#f-testing-methodology--actual-implementation)
  - [Start the instance](#start-the-instance)
  - [Two ways to get a sequence loaded](#two-ways-to-get-a-sequence-loaded)
  - [Command reference — valid inputs](#command-reference--valid-inputs)
  - [TMs to check](#tms-to-check)
  - [Suggested manual test walkthrough](#suggested-manual-test-walkthrough)
  - [Caveats specific to this simulator](#caveats-specific-to-this-simulator)

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

> **YAMCS/MCS implementation = XTCE for all 14 subtypes, plus one small `yamcs-core` helper that pre-builds and CRC-finalizes each embedded sub-command's bytes for TC[21,1] (§e).**
> The embedded-TC array in TC[21,1]/TM[21,12] is expressible directly in XTCE as an `ArrayArgumentType`/`ArrayParameterType` of an aggregate `{tc_packet, delay}`, using an unbounded `BinaryArgumentType`/`ContainerRefEntry`+`RepeatEntry` — the same mechanism already shipped for ST[19]'s embedded request (`pus19.xml`). The only genuine gap is that XTCE's command encoder cannot itself choose *which* MetaCommand to build per array element from data, so producing each embedded entry's raw bytes (and giving it a real CRC, since it's never independently transmitted/postprocessed) needs a small piece of `yamcs-core` Java — see §e. All sequence execution, TC relay, and on-board state management still run entirely within the satellite's on-board software, unchanged.

**Simulator note**: The simulator emulates the satellite's on-board ST[21] software — it maintains SEQUENCE_STORE, manages sequence scheduling, relays embedded TCs, and generates TM replies. It is **not** part of the YAMCS/MCS ground implementation, and is unaffected by the §e addition below (which is purely ground-side).

**Wire-format constraint**: The packet layout must not deviate from the ECSS-standard structure anywhere requirements leave no room for mission choice. Concretely: **no extra length-prefix fields may be added that the spec does not define.** Every gap below has been re-checked against that rule — see the revision note in each subtype's section.

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
| Background tasks | **[ON-BOARD]** One scheduled release chain per active ("under execution") sequence — on-board only |
| New pus_dt.xml types needed | `string16` (fixed-string, 128 bits) if not already present — MCS/ground XTCE |
| Major XTCE limitations | (1) Embedded TC packets inside repeating array — XTCE can only declare `seq_id` + `N`; the entry body is parsed manually. **Resolved without any non-standard framing**: each embedded TC packet's boundary is read from its own CCSDS primary-header length field (bytes 4–5), exactly the technique `Pus11Service.insertActivities()` already uses for TC[11,4]'s embedded commands. No `tc_len` prefix is added to the wire format. (2) Variable-length + optional file path strings in TC[21,2]/TC[21,8] — ground-tooling limitation only, does not affect the wire bytes. |

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
- A unique identifier (`request_sequence_ID`): fixed character-string — **mission fixes length to 16 bytes** (spec leaves the length open; this is an intentional mission convention, not a structural addition).
- An ordered list of request entries, each entry = `{ TC packet (raw bytes, self-length-delimited via its own CCSDS header), delay (relative time) }`.
- An execution status: `inactive` (0) or `under execution` (1) — Table 8-22.

```
SEQUENCE_STORE: dict[str, RequestSequence]

RequestSequence:
  id       : str                     # 16-byte null-padded ASCII string
  status   : INACTIVE | UNDER_EXECUTION
  entries  : list[(raw_tc: bytes, delay_ms: int)]
  pending  : ScheduledFuture | None   # non-null only while under_execution; cancel() aborts immediately
```

### Sequence lifecycle

All steps below occur **on-board**. Ground sends TCs to trigger each transition; YAMCS is responsible only for uplink (steps marked **[GROUND → SAT]**). Everything else is on-board logic.

```
[GROUND → SAT]  load (TC[21,1] or TC[21,2])  → [ON-BOARD] status = INACTIVE
[GROUND → SAT]  activate (TC[21,4])           → [ON-BOARD] status = UNDER_EXECUTION
                                                 [ON-BOARD] release entry 0, schedule entry 1 after delay[0]
                [ON-BOARD] each scheduled callback releases the next entry, then reschedules itself
                [ON-BOARD] after last entry's delay elapses → status = INACTIVE
[GROUND → SAT]  abort (TC[21,5])              → [ON-BOARD] cancel the pending scheduled release, status = INACTIVE (immediate — no lag)
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

**Packet structure (Figure 8-228)** — exactly as specified, no added fields:

```
CCSDS primary header (6 bytes)
  service_type    = 21
  service_subtype = 1
  request_sequence_ID   (fixed character-string, 16 bytes mission convention)
  N                     (unsigned integer — number of entries, uint8 mission convention)
  N × { request (TC packet), delay (relative time) }
```

**Not actually an XTCE limitation**: `N × {TC_packet, delay}` is expressible as a plain
`ArrayArgumentType` of an `AggregateArgumentType {tc_packet, delay}` — confirmed against the
real command encoder, `MetaCommandContainerProcessor.encodeRawValue()`
(`yamcs-core/.../mdb/MetaCommandContainerProcessor.java:147-154`), which already iterates array
elements and recursively encodes each aggregate member. `pus19.xml` ships this exact pattern for
TC[19,1] (`ea_add_array_type` / `ea_add_entry_type`, `examples/pus/.../mdb/pus19.xml:192-212`).

**Resolution — no length prefix, self-describing boundary**: Every `request` is itself a
complete, valid CCSDS/PUS packet, and every CCSDS packet carries its own total length in its
primary header (bytes [4..5] = packet data length `C`; total packet size = `C + 7`). Unlike
ST[19] (which adds a sibling `request_len` argument, a valid but non-spec-minimal convention),
ST[21]'s spec figures show zero framing bytes, so `tc_packet` must be declared with **no**
`SizeInBits` at all: `BinaryDataEncoding` then defaults to `FIXED_SIZE` with `sizeInBits = -1`,
and the encoder (`DataEncodingEncoder.encodeRawBinary()`, `DataEncodingEncoder.java:298-306`)
writes out exactly the bytes supplied — no framing, no padding. This is a documented YAMCS
extension (see the `DIFFERS_FROM_XTCE` note on `BinaryDataEncoding` itself). The on-board
software (and the simulator emulating it) still finds each entry's boundary the same way it
always did — by reading the embedded packet's own length field — because the *bytes on the wire*
are unaffected by which XTCE construct produced them. This is the same self-describing-length
fact already exploited by `Pus11Service.insertActivities()` (`simulator/.../pus/Pus11Service.java:143`)
for TC[11,4]'s embedded commands:

```java
int length = (bb.getShort(bb.position() + 4) & 0xFFFF) + 7;
byte[] packet = new byte[length];
bb.get(packet);
```

**XTCE implementation** (`pus21.xml`) — revised: `entries` is now a real argument, array-of-
aggregate, `tc_packet` deliberately carrying no `SizeInBits` (unbounded — see above):

```xml
<StringArgumentType name="SeqIdArgType">
    <StringDataEncoding>
        <SizeInBits>
            <Fixed><FixedValue>128</FixedValue></Fixed>
        </SizeInBits>
    </StringDataEncoding>
</StringArgumentType>

<!-- No SizeInBits at all -> BinaryDataEncoding defaults to FIXED_SIZE, sizeInBits=-1 ->
     encoder writes exactly the bytes supplied by the caller, zero framing (DataEncodingEncoder
     .encodeRawBinary(), DataEncodingEncoder.java:298-306). This is what makes the entry
     self-length-delimited on the wire instead of relying on an invented sibling length field. -->
<BinaryArgumentType name="TcPacketArgType" />

<AggregateArgumentType name="Pus21EntryType">
    <MemberList>
        <Member typeRef="TcPacketArgType" name="tc_packet" />
        <Member typeRef="/dt/uint32"      name="delay" />
    </MemberList>
</AggregateArgumentType>

<ArrayArgumentType arrayTypeRef="Pus21EntryType" name="Pus21EntryArrayType">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="num_entries" />
                    <LinearAdjustment intercept="-1" />
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<MetaCommand name="TC_21_1" shortDescription="TC[21,1] Direct-load a request sequence">
    <ArgumentList>
        <Argument argumentTypeRef="SeqIdArgType"        name="seq_id" />
        <Argument argumentTypeRef="/dt/uint8"            name="num_entries" />
        <Argument argumentTypeRef="Pus21EntryArrayType"  name="entries" />
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
            <ArgumentRefEntry argumentRef="entries" />
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

Built and issued exactly like any other command — the standard `IssueCommandRequest`/
`CommandsApi.issueCommand()` REST call, with `entries` as a normal JSON array-of-objects
argument value. No bespoke REST endpoint. The one thing ground can't get from XTCE alone is each
entry's *finalized bytes* (real CRC) — see §e.

**On-board emulation (simulator, real Java — see §d for the full class)**:

```java
private List<Entry> parseEntries(ByteBuffer bb, int n) {
    List<Entry> entries = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
        int tcLen = (bb.getShort(bb.position() + 4) & 0xFFFF) + 7;  // self-describing, no prefix
        byte[] rawTc = new byte[tcLen];
        bb.get(rawTc);
        int delayMs = bb.getInt();
        entries.add(new Entry(rawTc, delayMs));
    }
    return entries;
}

private void loadDirectly(PusTcPacket tc) {
    ack_start(tc);
    ByteBuffer bb = tc.getUserDataBuffer();
    String seqId = readSeqId(bb);
    int n = bb.get() & 0xFF;
    if (sequenceStore.containsKey(seqId)) {
        log.warn("ST21: seq '{}' already loaded, rejecting TC[21,1]", seqId);
        nack_completion(tc, COMPL_ERR_SEQ_ALREADY_LOADED);
        return;
    }
    List<Entry> entries = parseEntries(bb, n);
    sequenceStore.put(seqId, new RequestSequence(seqId, entries));
    log.info("ST21: loaded seq '{}' with {} entries", seqId, entries.size());
    ack_completion(tc);
}
```

**XTCE status**: Full — the whole command, including the entry array, is one standard XTCE
`MetaCommand`. No custom encoder/decoder code, no bespoke REST endpoint.

**Remaining gap**: XTCE can't choose *which* MetaCommand to build for each entry, or finalize a
never-transmitted sub-command's CRC — that's the one thing that has to happen inside MCS itself
before `entries` is populated. See §e (`Pus21EmbeddedEntryBuilder`), now a small helper, not a
full assembler.

---

### TC[21,2] — Load a request sequence by reference

**Spec §6.21.5.3 + §8.21.2.2**

**Purpose**: **[GROUND → SAT]** Ground uplinks a reference (file path) pointing to an on-board file; **[ON-BOARD]** the on-board software reads and loads the sequence from its local filesystem.

**Packet structure (Figure 8-229)** — unchanged, exactly per spec:

```
request_sequence_ID   (fixed character-string)
[optional] file_path:
    repository_path   (variable character-string)
    file_name         (variable character-string)
```

**XTCE limitation**: Variable-length strings and an optional block cannot be expressed in XTCE.

**Mission workaround — two MetaCommand variants** (ground-tooling only; does not touch the wire
bytes beyond what the spec's own "variable character-string" and "optional" clauses already
allow):

- `TC_21_2_no_path`: seq_id only; loading policy determines which file to use.
- `TC_21_2_with_path`: seq_id + `repo_path_len:uint8` + `repo_path_bytes` + `file_name_len:uint8` + `file_name_bytes` (length-prefixed variable strings are the standard ECSS PUS encoding for a "variable character-string" field — not an invented addition).

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

<!-- TC_21_2_with_path: seq_id + raw path bytes (parsed on-board) -->
```

**On-board emulation — revised: in-memory file-repository stub (no more blanket NACK)**

This simulator has no ST[23] File Management service, so there is no TC that can *write* a file
into an on-board filesystem. The previous plan NACK'd TC[21,2]/TC[21,8] unconditionally, which
means they could never be exercised in a test. Instead, the simulator seeds a small in-memory
`fileRepo` (`Map<String, List<Entry>> path → sequence entries`) at startup, standing in for a
filesystem whose contents were provisioned out-of-band (e.g. during I&T, or via a mission
mechanism outside ST[21]'s scope). TC[21,2]/TC[21,8] can only *reference* what's already there —
this mirrors the real on-board constraint (ST[21] never writes files, only reads them) while
making the subtype testable:

```java
private static final String DEFAULT_REPO_PATH = "/seq";
private final Map<String, List<Entry>> fileRepo = new LinkedHashMap<>();

private void seedFileRepo() {
    // Demo file for TC[21,2]/[21,8] round-trip testing without needing TC[21,1] first.
    fileRepo.put(DEFAULT_REPO_PATH + "/demo-seq-1.bin", List.of(
            new Entry(buildPing(), 500),
            new Entry(buildPing(), 500)));
}

private RequestSequence loadFromRepo(PusTcPacket tc, ByteBuffer bb, String seqId) {
    // §6.21.5.3.d.1 / §6.21.5.6.d.1: reject if seq_id already loaded -- same condition as TC[21,1].
    if (sequenceStore.containsKey(seqId)) {
        log.warn("ST21: seq '{}' already loaded, rejecting", seqId);
        nack_completion(tc, COMPL_ERR_SEQ_ALREADY_LOADED);
        return null;
    }
    String repoPath = DEFAULT_REPO_PATH;
    String fileName = seqId + ".bin";
    if (bb.remaining() > 0) {
        int repoLen = bb.get() & 0xFF;
        byte[] repoBytes = new byte[repoLen];
        bb.get(repoBytes);
        repoPath = new String(repoBytes, StandardCharsets.US_ASCII);
        int fileLen = bb.get() & 0xFF;
        byte[] fileBytes = new byte[fileLen];
        bb.get(fileBytes);
        fileName = new String(fileBytes, StandardCharsets.US_ASCII);
    }
    String key = repoPath + "/" + fileName;
    // §6.21.5.3.d.3: "refers to a file that does not exist"
    if (!fileRepo.containsKey(key)) {
        log.warn("ST21: file '{}' not found in on-board repository", key);
        nack_completion(tc, COMPL_ERR_FILE_NOT_FOUND);
        return null;
    }
    List<Entry> entries = fileRepo.get(key);
    // §6.21.5.3.d.4 also requires rejecting a file that exists but is "not recognized as a
    // request sequence file" -- distinct from "not found" (COMPL_ERR_FILE_NOT_RECOGNIZED is
    // reserved for this). Not reachable today: fileRepo only ever holds well-formed
    // List<Entry> because there is no ST[23] File Management TC in this simulator that could
    // write malformed bytes into it. Revisit if fileRepo is ever backed by raw bytes parsed
    // on demand instead of pre-seeded Entry lists.
    RequestSequence seq = new RequestSequence(seqId, entries);
    sequenceStore.put(seqId, seq);
    log.info("ST21: loaded seq '{}' from repo file '{}'", seqId, key);
    return seq;
}
```

**XTCE status**: Partial. Two MetaCommand variants cover both cases; path fields parsed on-board.

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

**On-board emulation**:

```java
private void unloadSequence(PusTcPacket tc) {
    ack_start(tc);
    String seqId = readSeqId(tc.getUserDataBuffer());
    RequestSequence seq = sequenceStore.get(seqId);
    if (seq == null || seq.status == Status.UNDER_EXECUTION) {
        log.warn("ST21: cannot unload '{}' (not loaded or under execution)", seqId);
        nack_completion(tc, COMPL_ERR_SEQ_NOT_UNLOADABLE);
        return;
    }
    sequenceStore.remove(seqId);
    log.info("ST21: unloaded seq '{}'", seqId);
    ack_completion(tc);
}
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

**XTCE implementation**: same pattern as TC[21,3] with subtype=4.

**On-board emulation — revised: cancellable scheduled chain, not a sleeping thread**

The original plan spawned one daemon `Thread` per sequence that called `Thread.sleep(delay_ms)`
between releases and polled a status flag to detect abort. That means an abort issued mid-delay
doesn't take effect until the sleep finishes — a real responsiveness gap, and it also doesn't
match how this codebase already handles delayed release (`Pus11Service` uses the shared
`ScheduledThreadPoolExecutor` with a cancellable `ScheduledFuture` chain — see
`Pus11Service.scheduleNext()`/`runSchedule()`). ST[21] should use the same idiom: each entry
release reschedules itself via `pusSimulator.executor`, and abort simply cancels the pending
future — no lag, no polling, no extra thread per sequence.

```java
private void activateSequence(PusTcPacket tc) {
    ack_start(tc);
    String seqId = readSeqId(tc.getUserDataBuffer());
    RequestSequence seq = sequenceStore.get(seqId);
    if (seq == null || seq.status == Status.UNDER_EXECUTION) {
        log.warn("ST21: cannot activate '{}'", seqId);
        nack_completion(tc, COMPL_ERR_SEQ_NOT_ACTIVATABLE);
        return;
    }
    seq.status = Status.UNDER_EXECUTION;
    seq.nextIndex = 0;
    releaseNext(seq);
    log.info("ST21: activated seq '{}'", seqId);
    ack_completion(tc);
}

private void releaseNext(RequestSequence seq) {
    if (seq.status != Status.UNDER_EXECUTION) {
        return; // aborted since this callback was scheduled
    }
    if (seq.nextIndex >= seq.entries.size()) {
        seq.status = Status.INACTIVE;
        seq.pending = null;
        log.info("ST21: sequence '{}' completed", seq.id);
        return;
    }
    Entry e = seq.entries.get(seq.nextIndex++);
    pusSimulator.processTc(new PusTcPacket(e.rawTc));
    seq.pending = pusSimulator.executor.schedule(
            () -> releaseNext(seq), e.delayMs, TimeUnit.MILLISECONDS);
}
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

**On-board emulation — immediate cancellation, not a status-flag poll**:

```java
private void abortSequence(PusTcPacket tc) {
    ack_start(tc);
    String seqId = readSeqId(tc.getUserDataBuffer());
    RequestSequence seq = sequenceStore.get(seqId);
    if (seq == null || seq.status == Status.INACTIVE) {
        log.warn("ST21: cannot abort '{}' (not loaded or already inactive)", seqId);
        nack_completion(tc, COMPL_ERR_SEQ_NOT_ABORTABLE);
        return;
    }
    abort(seq);
    log.info("ST21: aborted seq '{}'", seqId);
    ack_completion(tc);
}

private void abort(RequestSequence seq) {
    seq.status = Status.INACTIVE;
    if (seq.pending != null) {
        seq.pending.cancel(false);
        seq.pending = null;
    }
}
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

**On-board emulation**: iterates SEQUENCE_STORE, builds and downlinks TM[21,7] (see §d).

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

**On-board emulation**:

```java
private void reportExecutionStatus(PusTcPacket tc) {
    ack_start(tc);
    sendTm21_7();
    ack_completion(tc);
}

private void sendTm21_7() {
    List<RequestSequence> all = new ArrayList<>(sequenceStore.values());
    int n = all.size();
    PusTmPacket pkt = newPacket(7, 1 + n * 17);
    ByteBuffer bb = pkt.getUserDataBuffer();
    bb.put((byte) n);
    for (RequestSequence seq : all) {
        writeSeqId(bb, seq.id);
        bb.put((byte) (seq.status == Status.UNDER_EXECUTION ? 1 : 0));
    }
    pusSimulator.transmitRealtimeTM(pkt);
}
```

**XTCE status**: Fully compatible with mission-fixed 16-byte string length.

---

### TC[21,8] — Load by reference and activate a request sequence

**Spec §6.21.5.6 + §8.21.2.8**

**Purpose**: **[GROUND → SAT]** Combined TC[21,2] + TC[21,4] uplinkable in one command; **[ON-BOARD]** the on-board software loads the sequence from the file repository and immediately starts execution.

**Packet structure (Figure 8-234)**: Identical to TC[21,2] (seq_id + optional file path).

**XTCE implementation**: Same dual-MetaCommand approach as TC[21,2], with subtype=8.

**On-board emulation**: Parse identically to TC[21,2] (`loadFromRepo`); on success, immediately
call `activateSequence`-style logic (`seq.status = UNDER_EXECUTION; releaseNext(seq)`) instead of
leaving it inactive.

**XTCE status**: Partial (same dual-variant workaround as TC[21,2]).

---

### TC[21,9] — Checksum a request sequence

**Spec §6.21.7 + §8.21.2.9**

**Purpose**: **[GROUND → SAT]** Ground uplinks a checksum request; **[ON-BOARD]** the on-board software computes a CRC over the stored sequence and replies with TM[21,10]. **[SAT → GROUND]** YAMCS receives and decodes TM[21,10].

**Packet structure (Figure 8-235)**:

```
request_sequence_ID   (fixed character-string)
```

**XTCE implementation**: Same as TC[21,3] with subtype=9.

**Checksum algorithm**: §6.21.7 Note 2 states *"For the checksum algorithm, refer to clause
5.4.4"* — a cross-reference to a shared, spec-mandated algorithm, not an open mission choice.
Clause 5.4.4 wasn't in the reviewed excerpt (only §6.21/§8.21), but `pus06.md` (ST[6] memory
management, also checksum-bearing) independently reached CRC-16/CCITT for its own checksum
fields ("M4" convention, `binary16`) via the same clause reference. **Decision: proceed with
CRC-16/CCITT**, reusing the existing `CrcCciitCalculator` (already used by
`PusTcPacket`/`PusTmPacket` for packet-integrity CRC) rather than a second implementation. If
clause 5.4.4 is later found to mandate something else, only this one function changes:

```java
private static final CrcCciitCalculator CRC = new CrcCciitCalculator();

private int computeChecksum(RequestSequence seq) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    for (Entry e : seq.entries) {
        bos.writeBytes(e.rawTc);
        bos.write((e.delayMs >>> 24) & 0xFF);
        bos.write((e.delayMs >>> 16) & 0xFF);
        bos.write((e.delayMs >>> 8) & 0xFF);
        bos.write(e.delayMs & 0xFF);
    }
    byte[] raw = bos.toByteArray();
    return CRC.compute(raw, 0, raw.length) & 0xFFFF;
}

private void checksumSequence(PusTcPacket tc) {
    ack_start(tc);
    String seqId = readSeqId(tc.getUserDataBuffer());
    RequestSequence seq = sequenceStore.get(seqId);
    if (seq == null) {
        log.warn("ST21: seq '{}' not loaded, cannot checksum", seqId);
        nack_completion(tc, COMPL_ERR_SEQ_NOT_FOUND);
        return;
    }
    sendTm21_10(seqId, computeChecksum(seq));
    ack_completion(tc);
}
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

**On-board emulation**:

```java
private void sendTm21_10(String seqId, int checksum) {
    PusTmPacket pkt = newPacket(10, 18);
    ByteBuffer bb = pkt.getUserDataBuffer();
    writeSeqId(bb, seqId);
    bb.putShort((short) checksum);
    pusSimulator.transmitRealtimeTM(pkt);
}
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

**On-board emulation**: validates sequence is loaded; builds and downlinks TM[21,12] (see §d).

**XTCE status**: Fully compatible.

---

### TM[21,12] — Request sequence content report

**Spec §6.21.8 + §8.21.2.12**

**Purpose**: **[SAT → GROUND]** On-board software downlinks the full ordered list of entries (TC packets + delays) for the requested sequence; **[GROUND]** YAMCS receives and fully decodes it via XTCE alone.

**Packet structure (Figure 8-238)** — exactly as specified, no added fields:

```
[8..23]  request_sequence_ID  (16 bytes)
[24]     N                    (uint8)
[25+]    N × { request (TC packet, self-length-delimited), delay (relative time, uint32 ms) }
```

**Decode — fully expressible in XTCE, no custom Java**: this is a `ContainerRefEntry` +
`RepeatEntry` (count = `N`, a `DynamicValue`) referencing a nested `SequenceContainer` whose own
size is left dynamic (`sizeInBits` unset) and whose trailing member is the raw TC bytes. The
decoder already recurses into referenced sub-containers and, when the sub-container's declared
size is negative, advances the outer buffer by however many bits the sub-container actually
consumed (`SequenceEntryProcessor.extractContainerEntry()`,
`yamcs-core/.../mdb/SequenceEntryProcessor.java:69-95`). `RepeatEntry` with a dynamic count is
generic — it works for any entry, including a `ContainerRefEntry`
(`SequenceContainerProcessor.extract()`, `SequenceContainerProcessor.java:72-80`). This is the
exact mechanism already shipped for ST[19]'s `TM_19_11` (`ea_def_entry` container +
`ContainerRefEntry`/`RepeatEntry`, `examples/pus/.../mdb/pus19.xml:134-142`) — ST[21] just needs
the embedded container's own length field (its CCSDS `packet_data_length`) wired as the
`DynamicValue` for the trailing binary member, instead of a separate sibling `..._len` argument,
since the spec adds no such sibling field:

```xml
<BinaryParameterType name="Pus21TcTrailerType">
    <BinaryDataEncoding>
        <SizeInBits>
            <DynamicValue>
                <!-- packet_data_length parsed a moment earlier, this same sub-container instance -->
                <ParameterInstanceRef parameterRef="ccsds_packet_data_length" />
                <LinearAdjustment slope="8" intercept="8" />  <!-- (C+1) bytes -> bits -->
            </DynamicValue>
        </SizeInBits>
    </BinaryDataEncoding>
</BinaryParameterType>

<SequenceContainer name="Pus21EmbeddedTcEntry">
    <EntryList>
        <!-- reuse the standard 6-byte CCSDS primary header parameters, incl. ccsds_packet_data_length -->
        <ParameterRefEntry parameterRef="ccsds_packet_data_length" />
        <ParameterRefEntry parameterRef="tc_trailer" />   <!-- Pus21TcTrailerType -->
        <ParameterRefEntry parameterRef="delay" />         <!-- uint32 -->
    </EntryList>
    <!-- no <SizeInBits> on the container itself -> dynamic, decoder uses actual bits consumed -->
</SequenceContainer>

<SequenceContainer name="TM_21_12">
    <EntryList>
        <ParameterRefEntry parameterRef="seq_id" />
        <ParameterRefEntry parameterRef="num_entries" />
        <ContainerRefEntry containerRef="Pus21EmbeddedTcEntry">
            <RepeatEntry>
                <Count><DynamicValue><ParameterInstanceRef parameterRef="num_entries" /></DynamicValue></Count>
            </RepeatEntry>
        </ContainerRefEntry>
    </EntryList>
    <BaseContainer containerRef="PUS21Packet">
        <RestrictionCriteria>
            <Comparison parameterRef="service_subtype" value="12" />
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

Decoded values land in the standard parameter archive automatically — no custom recorder table
needed either.

**On-board emulation**:

```java
private void writeEntries(ByteBuffer bb, List<Entry> entries) {
    for (Entry e : entries) {
        bb.put(e.rawTc);       // no length prefix — the packet is self-describing
        bb.putInt(e.delayMs);
    }
}

private void reportSequenceContent(PusTcPacket tc) {
    ack_start(tc);
    String seqId = readSeqId(tc.getUserDataBuffer());
    RequestSequence seq = sequenceStore.get(seqId);
    if (seq == null) {
        log.warn("ST21: seq '{}' not loaded, cannot report content", seqId);
        nack_completion(tc, COMPL_ERR_SEQ_NOT_FOUND);
        return;
    }
    int userDataLength = 16 + 1;
    for (Entry e : seq.entries) {
        userDataLength += e.rawTc.length + 4;
    }
    PusTmPacket pkt = newPacket(12, userDataLength);
    ByteBuffer bb = pkt.getUserDataBuffer();
    writeSeqId(bb, seq.id);
    bb.put((byte) seq.entries.size());
    writeEntries(bb, seq.entries);
    pusSimulator.transmitRealtimeTM(pkt);
    ack_completion(tc);
}
```

**XTCE status**: Full — decoded and archived entirely via MDB, standard parameter archive. No
`yamcs-core` code needed for TM[21,12] (§e now covers only the TC[21,1] build-side helper).

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

**On-board emulation** — uses the same immediate `abort()` cancellation as TC[21,5]:

```java
private void abortAll(PusTcPacket tc) {
    ack_start(tc);
    List<String> aborted = new ArrayList<>();
    for (RequestSequence seq : sequenceStore.values()) {
        if (seq.status == Status.UNDER_EXECUTION) {
            abort(seq);
            aborted.add(seq.id);
        }
    }
    sendTm21_14(aborted);
    log.info("ST21: TC[21,13] aborted {} sequences", aborted.size());
    ack_completion(tc);
}
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

**On-board emulation**:

```java
private void sendTm21_14(List<String> abortedIds) {
    int n = abortedIds.size();
    PusTmPacket pkt = newPacket(14, 1 + n * 16);
    ByteBuffer bb = pkt.getUserDataBuffer();
    bb.put((byte) n);
    for (String id : abortedIds) {
        writeSeqId(bb, id);
    }
    pusSimulator.transmitRealtimeTM(pkt);
}
```

**XTCE status**: Fully compatible (array of fixed-length strings).

---

## c) Gaps / Shortcomings

| # | Subtype(s) | Gap | Impact | Resolution |
|---|-----------|-----|--------|------------|
| 1 | TC[21,1], TM[21,12] | **Embedded TC packets in a repeating array.** Originally assumed XTCE-inexpressible. | None — fully resolved via XTCE (`ArrayArgumentType`/`ArrayParameterType` of an aggregate, unbounded `BinaryArgumentType`/`ContainerRefEntry`+`RepeatEntry`+`DynamicValue`). Only residual gap: XTCE can't pick *which* sub-command to build per entry or CRC-finalize a never-transmitted packet — one small `yamcs-core` helper (§e), not a full assembler/decoder pair. | **No wire-format addition.** Each embedded TC packet is self-length-delimited via its own CCSDS primary-header length field (bytes 4–5) — same fact `Pus11Service.insertActivities()` uses for TC[11,4], and the same `ContainerRefEntry`/`RepeatEntry` pattern already shipped for ST[19]'s `TM_19_11` (`pus19.xml:134-142`). |
| 2 | TC[21,2], TC[21,8] | **Variable-length + optional file path strings** — XTCE cannot express variable-length strings or optional blocks. | Two different packet layouts (with/without path) cannot share a single MetaCommand. | Two MetaCommand variants per subtype (`_no_path` / `_with_path`). Path strings encoded as length-prefixed byte sequences (standard ECSS "variable character-string" encoding, not an invention) parsed on-board. |
| 3 | All | **request_sequence_ID length** — spec says "fixed character-string" but does not define the length. | Wire format ambiguous without a mission decision. | Mission fixes to 16 bytes (128 bits); document in ICD. Spec explicitly leaves this open — not a structural change. |
| 4 | TC[21,1], TM[21,12] | **`delay: relative time`** — PUS relative time encoding is not defined at this spec level. | Cannot express duration without a mission encoding decision. | Mission convention: encode delay as **uint32 milliseconds** (big-endian). Spec explicitly leaves this open — not a structural change. |
| 5 | TC[21,1], TM[21,12] | **TC relay logic** — when a sequence executes, the embedded TC packets must be "released" (sent to the target subsystem). This is **on-board logic only**; YAMCS/MCS is not involved in sequence execution. | Not a YAMCS gap — YAMCS only uplinks the TC[21,x] commands and decodes returned TM. | **[ON-BOARD]** The on-board software (emulated by the simulator) extracts raw TC bytes from the sequence and relays them via `pusSimulator.processTc()` — same mechanism ST[11] uses for scheduled TC release; YAMCS only sees the ST[21] wrapper packet. |
| 6 | TC[21,2], TC[21,8] | **No ST[23] File Management service** — the simulator has no real on-board filesystem, so there's no way for ground to *write* a file that TC[21,2]/[21,8] could then load. | Previously resolved by NACK'ing both subtypes unconditionally — meant they were never actually exercised. | Simulator seeds a small in-memory `fileRepo` (`path → entries`) at startup, standing in for a filesystem provisioned out-of-band. TC[21,2]/[21,8] can reference pre-seeded files; unknown paths NACK with a "file not found" completion error, same as real on-board behaviour. |
| 7 | TC[21,5], TC[21,13] | **Abort responsiveness** — a naive "poll a status flag between `Thread.sleep()` calls" design means abort doesn't take effect until the in-flight delay finishes. | Ground sees a delayed/inconsistent abort response relative to when the TC was issued. | Use the same cancellable `ScheduledFuture` chain pattern already established by `Pus11Service` (`pusSimulator.executor.schedule(...)` + `future.cancel(false)`) instead of a sleeping thread — abort is immediate. |
| 8 | TC[21,2], TC[21,8] | **§6.21.5.3.d.1 / §6.21.5.6.d.1**: "already loaded" must be rejected for load-by-reference too, not just TC[21,1]. Missed in the first pass of this revision. | Ground could silently overwrite/duplicate a loaded sequence via TC[21,2]/[21,8]. | Fixed: `loadFromRepo()` now checks `sequenceStore.containsKey(seqId)` before consulting `fileRepo`, same as `loadDirectly()`. |
| 9 | TC[21,2], TC[21,8] | **§6.21.5.3.d.3/4**: spec distinguishes "file does not exist" from "file exists but is not recognized as a request sequence file" — two separate rejection reasons. | Only one NACK code covered both cases. | `COMPL_ERR_FILE_NOT_FOUND` vs `COMPL_ERR_FILE_NOT_RECOGNIZED` are now separate constants. The second is currently unreachable (this simulator's `fileRepo` only ever holds well-formed entries — no ST[23] File Management TC can write malformed bytes into it) but is reserved for spec fidelity. |
| 10 | TC[21,9] | **Checksum algorithm** — §6.21.7 Note 2 says "refer to clause 5.4.4" for the algorithm. That clause wasn't in the reviewed excerpt (only §6.21/§8.21). | `CrcCciitCalculator` reuse was previously justified as "a reasonable mission convention"; it may actually be spec-mandated. | **Decided**: proceed with CRC-16/CCITT via the existing `CrcCciitCalculator`, corroborated by `pus06.md`'s independent choice for ST[6] checksums under the same clause. Revisit only if clause 5.4.4 is later read and says otherwise. |
| 11 | — | **§6.21.9 Subservice observables**: "the list of request sequence identifiers and associated execution status of the loaded request sequences, in an array of size corresponding to the maximum number of request sequences that can be contemporaneously loaded" — a continuously-exposed observable, distinct from the on-demand TM[21,7] report. | Not addressed anywhere in this plan; no periodic/HK-style exposure of SEQUENCE_STORE state was designed. | **Decided**: out of scope for the simulator. On-board `sequenceStore` state isn't visible to YAMCS ground regardless; TC[21,6]→TM[21,7] already covers status reporting for ground-testing purposes. Treated as a ground-MDB/ICD-only declaration if ever needed on the real spacecraft. |

### §6.21.9 subservice observables — decided: out of scope for the simulator

The spec requires an observable — "the list of request sequence identifiers and associated
execution status of the loaded request sequences, in an array of size corresponding to the
maximum number of request sequences that can be contemporaneously loaded at any time." This is
conceptually the same data as TM[21,7], but exposed *continuously* (like a housekeeping
parameter) rather than only on-demand in response to TC[21,6].

**Decision**: not modeled in the simulator. On-board `sequenceStore` state is never visible to
YAMCS ground directly regardless of whether this observable exists on the real spacecraft, and
TC[21,6]→TM[21,7] already gives ground-testing full visibility into loaded-sequence status
on demand. If this observable is ever required for a real mission, it's a ground-MDB/ICD
declaration concern on the spacecraft side, not something the simulator needs to emulate.

### Overall feasibility verdict

**YES — PUS ST[21] (all 14 in-scope subtypes) can be implemented for the MCS ground scope with zero non-standard additions to the wire format. All 14 subtypes, including TC[21,1] and TM[21,12], are fully defined in XTCE. The only `yamcs-core` addition (§e) is a small helper for producing correctly-CRC'd bytes for embedded sub-commands — XTCE's encoder can't itself decide which MetaCommand an entry names.**

All sequence storage, scheduling, execution, TC relay, and checksum computation remain **on-board responsibilities** — unchanged. Ground-side:
- Encoding and uplinking TC[21,1..6,8,9,11,13] — all XTCE `MetaCommand` definitions, no code beyond MDB.
- Receiving and decoding TM[21,7,10,12,14] — all XTCE `SequenceContainer` definitions, no code beyond MDB.
- Assembling one embedded entry's finalized (CRC'd) bytes before it's placed into TC[21,1]'s `entries` argument — the one `yamcs-core` helper, §e.

Two categories of XTCE-tooling limitations remain (neither changes what's actually on the wire, and neither needs a decoder/assembler class — both are declarative MDB patterns already proven in `pus19.xml`):

1. **Embedded TC arrays** (TC[21,1], TM[21,12]): `ArrayArgumentType`/`ArrayParameterType` of an aggregate, with the raw-TC member left unbounded (encode) or sized via `ContainerRefEntry`+`RepeatEntry`+`DynamicValue` off the packet's own length field (decode).

2. **Optional variable-length file paths** (TC[21,2], TC[21,8]): dual MetaCommand variants (`_no_path` / `_with_path`), using the spec's own length-prefixed "variable character-string" encoding.

The remaining 10 subtypes are fully XTCE-compatible using standard patterns (fixed-string
arguments, zero-payload MetaCommands, AggregateParameterType arrays).

#### Artifact summary

| Layer | Artifact | Purpose |
|-------|----------|---------|
| **MCS / YAMCS ground** | `pus21.xml` (XTCE) | TC encoding (MetaCommands) + TM decoding (SequenceContainers) for all 14 subtypes, including the TC[21,1]/TM[21,12] entry arrays |
| **MCS / YAMCS ground (`yamcs-core`)** | `Pus21EmbeddedEntryBuilder` (§e) | Builds one embedded sub-command via `CommandingManager.buildCommand()` and patches in a real CRC, for ground to place into TC[21,1]'s `entries` argument |
| **Simulator (on-board emulation)** | `Pus21Service.java` | Emulates on-board SEQUENCE_STORE, sequence scheduling, TC relay, in-memory file repository, TM generation |

> **Key finding**: YAMCS/MCS remains a TC uplink and TM decode layer for ST[21] — it never gains any on-board sequencing intelligence (loading, activating, scheduling, TC relay, abort, checksum all still run on-board only, emulated by the simulator for ground testing). The embedded-TC "gap" that originally looked like it needed custom assembler/decoder classes turned out not to: XTCE's array-of-aggregate and `ContainerRefEntry`/`RepeatEntry`/`DynamicValue` machinery, already proven in this repo's `pus19.xml`, covers both directions. The only real gap — picking which MetaCommand an entry names, and giving a never-transmitted sub-command a real CRC — is inherent to what XTCE is (a static schema), not a bug in this MDB.

---

## d) Simulator On-board Emulation (Java)

> **Note**: This section describes the **simulator** implementation that emulates on-board ST[21] behaviour for ground testing. These are **not** changes to `yamcs-core` or the operational MCS ground software. The MCS ground implementation is XTCE (`pus21.xml`) plus the one small `yamcs-core` helper described in §e below.

### New file: `Pus21Service.java`

**Path**: `simulator/src/main/java/org/yamcs/simulator/pus/Pus21Service.java`

**Layer**: Simulator (on-board emulation) — NOT part of `yamcs-core` or YAMCS MCS ground software.

Follows the same pattern as `Pus5Service.java`/`Pus19Service.java`. Key design points (revised from the original plan):

| Aspect | Decision |
|--------|----------|
| State | `LinkedHashMap<String, RequestSequence> sequenceStore` — keyed on `seq_id` |
| seq_id encoding | 16-byte null-padded ASCII; `readSeqId` / `writeSeqId` helpers |
| Delay encoding | `uint32 milliseconds` — stored as `int delayMs` in `Entry` |
| **Embedded TC boundary** | **No length prefix.** `(bb.getShort(bb.position() + 4) & 0xFFFF) + 7` — same as `Pus11Service.insertActivities()` |
| TC relay | `pusSimulator.processTc(new PusTcPacket(entry.rawTc))` — same mechanism as ST[11] |
| **Scheduling model** | Cancellable chain on `pusSimulator.executor` (`ScheduledThreadPoolExecutor`), not a sleeping `Thread` — matches `Pus11Service.scheduleNext()`/`runSchedule()`. Abort = `future.cancel(false)`, immediate. |
| CRC (TC[21,9]) | Reuse the existing `CrcCciitCalculator` (already used by `PusTcPacket`/`PusTmPacket`) over the concatenation of `{tc_bytes ‖ delay_ms(uint32)}` for all entries — no second CRC implementation |
| **TC[21,2]/TC[21,8]** | **In-memory `fileRepo` stub**, seeded at construction, instead of unconditional NACK — makes both subtypes testable |

**Subtype dispatch table**:

```java
case 1  -> loadDirectly(tc)           // TC[21,1]: parse seq_id+N+entries, store inactive
case 2  -> loadByReference(tc, false) // TC[21,2]: look up in fileRepo, store inactive
case 3  -> unloadSequence(tc)         // TC[21,3]: remove if inactive
case 4  -> activateSequence(tc)       // TC[21,4]: set under_execution, start release chain
case 5  -> abortSequence(tc)          // TC[21,5]: cancel pending future, set inactive
case 6  -> reportExecutionStatus(tc)  // TC[21,6]: build TM[21,7]
case 8  -> loadByReference(tc, true)  // TC[21,8]: look up in fileRepo, then activate immediately
case 9  -> checksumSequence(tc)       // TC[21,9]: build TM[21,10]
case 11 -> reportSequenceContent(tc)  // TC[21,11]: build TM[21,12]
case 13 -> abortAll(tc)               // TC[21,13]: abort all active, build TM[21,14]
```

TM subtype 7, 10, 12, 14 are generated inside the TC handlers above (not dispatched separately).

**Local completion error codes** (each service defines its own, following the `Pus19Service`
pattern of `COMPL_ERR_EA_DEF_NOT_FOUND = 5` etc.):

```java
static final int COMPL_ERR_SEQ_ALREADY_LOADED   = 3;
static final int COMPL_ERR_SEQ_NOT_FOUND        = 4;
static final int COMPL_ERR_SEQ_NOT_UNLOADABLE   = 5;
static final int COMPL_ERR_SEQ_NOT_ACTIVATABLE  = 6;
static final int COMPL_ERR_SEQ_NOT_ABORTABLE    = 7;
static final int COMPL_ERR_FILE_NOT_FOUND       = 8;
static final int COMPL_ERR_FILE_NOT_RECOGNIZED  = 9;  // §6.21.5.3.d.4 / §6.21.5.6.d.4
```

**`start()` requirement**: like `Pus11Service`, `Pus21Service` needs a reference to
`pusSimulator.executor` for the release-chain scheduling, so `start()` must capture it:

```java
@Override
public void start() {
    // executor already exists on pusSimulator by the time doStart() calls this
}
```

(No separate field capture is strictly required since `pusSimulator.executor` can be referenced
directly at call time — unlike `Pus11Service` there's no local `executor` field to populate — but
`Pus21Service` must still be registered in `doStart()`'s startup sequence if it later gains any
own periodic behaviour.)

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

**3. `executePendingCommands()` switch** (adds case 21):
```java
case 21 -> pus21Service.executeTc(commandPacket);
```

No `doStart()` change is needed — `pusSimulator.executor` already exists by the time any TC
arrives (it's created first thing in `doStart()`), and `Pus21Service` reads it lazily via
`pusSimulator.executor.schedule(...)` rather than caching a local copy.

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
| TC[21,1] entry body (variable-length TC + delay) | **Simulator (on-board emulation)** | On-board must parse each entry without a length hint | `Pus21Service.parseEntries()` reads each embedded TC's own CCSDS length field — no non-standard framing |
| TC[21,2]/TC[21,8] file loading | **Simulator (on-board emulation)** | Simulator has no real on-board filesystem / no ST[23] | In-memory `fileRepo` seeded at construction; unknown path → NACK `COMPL_ERR_FILE_NOT_FOUND` |
| TC relay during sequence execution | **Simulator (on-board emulation)** | On-board must relay embedded TCs to target subsystems | `releaseNext()` calls `pusSimulator.processTc()` — same approach as ST[11] scheduled TC release |
| Abort responsiveness | **Simulator (on-board emulation)** | Naive polling delays abort until the current inter-request delay elapses | Cancellable `ScheduledFuture` chain (`pusSimulator.executor`), `cancel(false)` on abort — immediate |
| TM[21,12] variable-length content | **Simulator (on-board emulation)** | On-board builds content report with variable-length entry body | Built entirely in `reportSequenceContent()` by manual `ByteBuffer` assembly, no length prefix |

No `processors.yaml` change is needed on the ground side — ST[21] is emulated entirely inside the
simulator's existing `PusSimulator` dispatch loop (see the `PusSimulator.java` changes above),
the same as every other in-scope PUS service on this branch (ST[13..15], ST[19], ST[20]). There is
no native `yamcs-core` `CommandReleaser`/handler class for ST[21] — the `PusCommandReleaser` +
`PusTcHandler` dispatch pattern (documented in `pus_native_arch.md`) belongs to a different
(non-groundstation-only, "YAMCS acts as the on-board service") deployment mode and does not apply
here. What ST[21] *does* need on the ground side is the narrower, purpose-built pair of services
in §e below — they don't dispatch/handle incoming TCs the way `PusTcHandler` does; they only
assist encoding one outbound command type and decoding/archiving one inbound report type.

---

## e) MCS-side (`yamcs-core`) support: one embedded-entry builder

> **Layer**: MCS / YAMCS ground (`yamcs-core`). Everything else in §b — including the whole
> TC[21,1]/TM[21,12] entry array — is now plain XTCE (see those sections). This is the one
> residual piece: a helper to produce a single embedded sub-command's finalized bytes, since
> XTCE's static schema can't pick *which* MetaCommand an array entry names, and a sub-command
> that's never independently transmitted never gets a real CRC from the normal link pipeline.

### Why this is still needed (recap)

`entries[i].tc_packet` in TC[21,1] is a plain `BinaryArgumentType` argument (see §b) — ground can
already put arbitrary bytes there via the standard `issueCommand` REST call. What ground can't
get from XTCE is those bytes *in the first place*: XTCE has no construct for "invoke MetaCommand
X with args Y" as a function of runtime data, and a sub-command built via
`CommandingManager.buildCommand()` alone is not yet wire-ready — packet length, CCSDS sequence
count, and CRC are normally filled by `PusCommandPostprocessor.process()` at the TC data link,
right before actual transmission (`PusCommandPostprocessor.java:112`). That method is **not**
reusable for an embedded entry: it's bound to a specific `Link`'s `commandHistoryPublisher` and
`seqFiller`, and calling it would spuriously log command history and consume the live link's
sequence counter for a packet that's never actually transmitted over that link — it only travels
as opaque bytes inside TC[21,1] until the on-board software relays it later.

### `Pus21EmbeddedEntryBuilder`

**Path**: `yamcs-core/src/main/java/org/yamcs/pus/Pus21EmbeddedEntryBuilder.java`

```java
private static final CrcCciitCalculator CRC = new CrcCciitCalculator();
private final AtomicInteger localSeqCounter = new AtomicInteger();

public byte[] build(Processor processor, User user, String commandName,
        Map<String, Object> args, String origin) throws Exception {
    MetaCommand mc = processor.getMdb().getMetaCommand(commandName);
    PreparedCommand pc = processor.getCommandingManager()
            .buildCommand(mc, args, origin, 0, user);   // packet length already correct
    return finalizeEmbeddedEntry(pc.getBinary());
}

// Standalone finalization, no command-history/seqcounter side effects on any real link.
private byte[] finalizeEmbeddedEntry(byte[] binary) {
    byte[] withCrc = Arrays.copyOf(binary, binary.length + 2);
    ByteBuffer bb = ByteBuffer.wrap(withCrc);
    int seq = localSeqCounter.getAndIncrement() & 0x3FFF;         // local counter, not the link's
    bb.put(2, (byte) ((bb.get(2) & 0xC0) | ((seq >> 8) & 0x3F))); // seq-flags(2b) + seqcount hi
    bb.put(3, (byte) seq);                                        // seqcount lo
    int crc = CRC.compute(withCrc, 0, withCrc.length - 2);
    bb.putShort(withCrc.length - 2, (short) crc);
    return withCrc;
}
```

The local sequence counter only needs to be unique *within an assembled sequence*, not globally —
this packet is never transmitted independently. Ground calls this once per entry, then assembles
the standard `entries` array argument client-side (or via a thin convenience endpoint wrapping
this + the outer `issueCommand` in one call — naming/route TBD, low-risk either way) and issues
`TC_21_1` through the normal, existing `issueCommand` REST API. No REST endpoint is *required*
for the outer command — it's a standard MetaCommand.

### Open questions for this section

- Whether to expose `Pus21EmbeddedEntryBuilder` as its own small REST endpoint (so a thin client
  can fetch finalized entry bytes without duplicating CRC logic) or keep it Java-only and require
  callers to assemble the full `TC_21_1` request server-side in one shot — both satisfy "part of
  MCS"; this is just an ergonomics choice.
- Whether the local sequence counter needs to persist across restarts (leaning "no" — see above).
- Whether entry `args` should mirror the full `IssueCommandRequest` proto value types or a
  simplified subset.

---

## f) Testing Methodology — Actual Implementation

All 14 in-scope subtypes are implemented and wired up. This section supersedes any pseudocode
shown in §b/§d — it describes what to actually run.

| Artifact | Path |
|---|---|
| MDB | `examples/pus/src/main/yamcs/mdb/pus21.xml` |
| Simulator service | `simulator/src/main/java/org/yamcs/simulator/pus/Pus21Service.java` |
| Registration | `PusSimulator.java` (`pus21Service` field/constructor/`case 21 ->` dispatch), `yamcs.pus.yaml` (`mdb:` list) |
| Embedded-entry builder (ground) | `yamcs-core/src/main/java/org/yamcs/pus/Pus21EmbeddedEntryBuilder.java` — Java-only, no REST endpoint yet (see §e open questions) |
| Sequence store | `Pus21Service.sequenceStore` — `LinkedHashMap<String, RequestSequence>`, empty at startup |
| File repository stub | `Pus21Service.fileRepo` — pre-seeded with `/seq/demo-seq-1.bin` = two TC[17,1] ping entries, 500ms apart |

### Start the instance

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests   # first build only
mvn -pl examples/pus yamcs:run
```
Web UI: `http://localhost:8090`, instance `pus`. Commands live under `/PUS21/...`.

### Two ways to get a sequence loaded

TC[21,1] (`TC_21_1`) needs `entries[i].tc_packet` to already be complete, CRC'd, wire-ready bytes
of a sub-command — the command UI cannot construct that by hand, and there is no REST convenience
endpoint yet (§e). Two ways to actually exercise a loaded sequence without hand-crafting bytes:

1. **Preferred for most tests — TC[21,2]/TC[21,8] against the seeded file repo.** No embedded-byte
   construction needed: `TC_21_2_NO_PATH` with `seq_id="demo-seq-1"` loads the pre-seeded
   `/seq/demo-seq-1.bin` entries directly (`loadFromRepo()` defaults to `<seq_id>.bin` in `/seq`
   when no path is given).
2. **For exercising TC[21,1] itself** — call `Pus21EmbeddedEntryBuilder.build()` from a Java test
   (e.g. `buildCommand` a `TC_17_1` ping, get back finalized bytes), then paste those bytes
   (hex/base64, per the web UI's binary argument input) into `entries[i].tc_packet` alongside a
   `delay` value. Confirms the ground-side array-of-aggregate encoder path end-to-end.

### Command reference — valid inputs

| Command | Subtype | Valid example args |
|---|---|---|
| `TC_21_2_NO_PATH` | TC[21,2] | `{"seq_id": "demo-seq-1"}` — loads the seeded file |
| `TC_21_4` | TC[21,4] | `{"seq_id": "demo-seq-1"}` — activates a loaded, inactive sequence |
| `TC_21_5` | TC[21,5] | `{"seq_id": "demo-seq-1"}` — aborts a sequence currently under execution |
| `TC_21_3` | TC[21,3] | `{"seq_id": "demo-seq-1"}` — unloads an inactive sequence |
| `TC_21_6` | TC[21,6] | *(no args)* — triggers `TM_21_7` |
| `TC_21_9` | TC[21,9] | `{"seq_id": "demo-seq-1"}` — triggers `TM_21_10` |
| `TC_21_11` | TC[21,11] | `{"seq_id": "demo-seq-1"}` — triggers `TM_21_12` |
| `TC_21_13` | TC[21,13] | *(no args)* — aborts every under-execution sequence, triggers `TM_21_14` |
| `TC_21_8_NO_PATH` | TC[21,8] | `{"seq_id": "demo-seq-1"}` — load-and-activate in one TC (only works once per `seq_id`, since the file repo entry can be loaded only while not already in `sequenceStore`) |

### TMs to check

| Container | Subtype | Triggered by | Layout |
|---|---|---|---|
| `/PUS21/TM_21_7` | TM[21,7] | `TC_21_6` | `num_entries:u8`, then N × `{seq_id:16B, exec_status:u8}` |
| `/PUS21/TM_21_10` | TM[21,10] | `TC_21_9` | `seq_id:16B`, `checksum_value:u16` (CRC-16/CCITT) |
| `/PUS21/TM_21_12` | TM[21,12] | `TC_21_11` | `seq_id:16B`, `num_entries:u8`, then N × `{embedded TC packet (self-length-delimited), delay:u32}` |
| `/PUS21/TM_21_14` | TM[21,14] | `TC_21_13` | `num_entries:u8`, then N × `seq_id:16B` |

Also watch the standard PUS-1 verification containers (`/PUS/pus-tc-ack-*`) for ACK start/completion
on every command, and note that activation additionally triggers *relayed* TC[17,1] pings — each
appears as its own independent ACK-start/ACK-completion chain in command history, one per entry,
staggered by the configured delay.

### Suggested manual test walkthrough

1. **Load the seeded sequence**: `TC_21_2_NO_PATH` with `{"seq_id": "demo-seq-1"}`. Confirm ACK
   completion (not `COMPL_ERR_FILE_NOT_FOUND`).
2. **Reject a duplicate load**: repeat step 1. Confirm NACK completion,
   `COMPL_ERR_SEQ_ALREADY_LOADED` (3).
3. **Check status before activation**: `TC_21_6` (no args). `TM_21_7` should list `demo-seq-1` with
   `exec_status=0` (inactive).
4. **Check content**: `TC_21_11` with `{"seq_id": "demo-seq-1"}`. `TM_21_12` should show 2 entries,
   each a full TC[17,1] ping packet + `delay=500`.
5. **Checksum**: `TC_21_9` with `{"seq_id": "demo-seq-1"}`. `TM_21_10` returns a CRC-16/CCITT value
   — note it down, it should be stable across repeated calls on the same unmodified sequence.
6. **Activate and watch relay**: `TC_21_4` with `{"seq_id": "demo-seq-1"}`. Confirm ACK completion,
   then watch command history for two independent TC[17,1] ACK-start/completion chains roughly
   500ms apart. After the second entry's delay elapses, `TC_21_6` again should show `demo-seq-1`
   back to `exec_status=0` (execution completed and reset itself).
7. **Abort responsiveness**: reload (`TC_21_2_NO_PATH`), activate (`TC_21_4`), then immediately
   send `TC_21_5` with `{"seq_id": "demo-seq-1"}` (well inside the 500ms inter-entry delay).
   Confirm ACK completion and that **no second** relayed ping appears in command history — the
   cancellable `ScheduledFuture` chain means abort takes effect before the next entry's delay
   elapses, not after.
8. **Reject an abort on an inactive sequence**: repeat step 7's `TC_21_5` immediately after it
   already succeeded. Confirm NACK completion, `COMPL_ERR_SEQ_NOT_ABORTABLE` (7).
9. **Unload**: `TC_21_3` with `{"seq_id": "demo-seq-1"}`. Confirm ACK completion, then `TC_21_6`
   shows an empty `TM_21_7` (0 entries).
10. **Reject operations on an unknown seq_id**: `TC_21_9` with `{"seq_id": "does-not-exist"}`.
    Confirm NACK completion, `COMPL_ERR_SEQ_NOT_FOUND` (4).
11. **Reject a load from an unknown file**: `TC_21_2_NO_PATH` with `{"seq_id": "nope"}` (no file
    `/seq/nope.bin` in the repo). Confirm NACK completion, `COMPL_ERR_FILE_NOT_FOUND` (8).
12. **`TC_21_13` abort-all**: load and activate `demo-seq-1` again, then send `TC_21_13` (no args)
    mid-execution. Confirm `TM_21_14` lists `demo-seq-1` as aborted, and no further pings relay.

### Caveats specific to this simulator

- **Only one seeded file**: `/seq/demo-seq-1.bin`. Testing `TC_21_2_WITH_PATH`/`TC_21_8_WITH_PATH`
  against a *different* repo path requires editing `Pus21Service.seedFileRepo()` — there's no TC
  that can write into `fileRepo` at runtime (no ST[23] File Management service, see Gap 6 in §c).
- **Checksum algorithm not independently verified**: CRC-16/CCITT was chosen by analogy with
  ST[6]'s independent reading of the same spec clause (5.4.4), not by reading that clause directly
  (Gap 10 in §c). If a mission ICD later specifies otherwise, only `Pus21Service.computeChecksum()`
  and the matching ground-side expectation need to change.
- **`request_sequence_ID` is silently truncated/padded to 16 bytes**: `writeSeqId()` truncates
  longer ids and null-pads shorter ones with no rejection — a `seq_id` longer than 16 ASCII
  characters will not round-trip as typed.
- **No persistence across restarts**: `sequenceStore` and `fileRepo` are both plain in-memory maps,
  reset on every simulator process start (`fileRepo` reseeds itself; `sequenceStore` starts empty).
- **`Pus21EmbeddedEntryBuilder` has no REST endpoint**: it's a Java-only helper today (open question
  in §e). Building a real TC[21,1] test currently means writing a small Java caller, not something
  reachable purely from the web UI.
