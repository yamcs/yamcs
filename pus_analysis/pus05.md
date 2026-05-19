# PUS ST[05] — Event Reporting Service
> ECSS-E-ST-70-41C (15 April 2016), §6.5 (pp. 121–126) and §8.5 (pp. 477–480)

---

## General Context (§6.5)

### Purpose

ST[05] provides the capability to report information of operational significance that is not explicitly provided within provider-initiated reports of another service. It covers:

- On-board failures and anomalies, including FDIR-detected anomalies
- Initiation, progress, and completion of activities initiated from ground or autonomously
- Hardware built-in test results
- Normal payload events

### Core Concepts

**Event Definition**: A declared reportable event, identified by an *event definition identifier* unique within its application process. Each definition has a fixed severity level and an optional auxiliary data structure.

**Event Definition System Identifier**: APID + event definition identifier (globally unique across the mission).

**Event Report Generation Status**: Per-event-definition flag — `enabled` or `disabled`. Initial state is declared at specification time (§6.5.5.1b).

**Auxiliary Data — "Deduced" Presence**: TM[5,1-4] carries `event_id` + `auxiliary data`. The structure of the aux data field is NOT self-describing in the packet — the receiver infers it solely from the event ID. In XTCE this is modelled as container inheritance with restriction criteria (one sub-container per event type).

### Service Layout

Each application process hosts at most one ST[05] provider (§6.5.2.2a).

**Severity levels** (mapped to message subtype):

| Subtype | Name | YAMCS severity |
|---------|------|----------------|
| 1 | Informative event report | INFO |
| 2 | Low severity anomaly report | WATCH |
| 3 | Medium severity anomaly report | DISTRESS |
| 4 | High severity anomaly report | CRITICAL |

### Controlling Event Report Generation (§6.5.5)

- TC[5,5] enables report generation for a list of event IDs
- TC[5,6] disables report generation for a list of event IDs
- An instruction to enable/disable an unknown event ID is rejected; valid instructions in the same TC still execute (§6.5.5.2f / §6.5.5.3f)
- TC[5,7] queries the current disabled list; response is TM[5,8]

---

## Message Types

| Subtype | Dir | Name |
|---------|-----|------|
| 1 | TM | Informative event report |
| 2 | TM | Low severity anomaly report |
| 3 | TM | Medium severity anomaly report |
| 4 | TM | High severity anomaly report |
| 5 | TC | Enable report generation of event definitions |
| 6 | TC | Disable report generation of event definitions |
| 7 | TC | Report the list of disabled event definitions |
| 8 | TM | Disabled event definitions list report |

---

## Packet Structures (§8.5)

### TM[5,1–4] — Event Reports
```
| event definition ID (enumerated) | auxiliary data (deduced) |
```
All four subtypes share the same source data field layout. Subtype encodes severity.

### TC[5,5] Enable / TC[5,6] Disable
```
| N (unsigned int) | event definition ID × N (enumerated) |
```

### TC[5,7] Report disabled list
No application data (omitted per §8.5.2.7b).

### TM[5,8] Disabled event definitions list report
```
| N (unsigned int) | event definition ID × N (enumerated) |
```

---

## Current Implementation Architecture

### Component Roles

| Component | File | Role |
|-----------|------|------|
| Simulator (on-board) | `simulator/.../pus/Pus5Service.java` | Generates TM[5,1-4] events; handles TC[5,5/6/7] |
| Ground decoder | `yamcs-core/.../pus/PusEventDecoder.java` | Decodes TM[5,1-4] → YAMCS native events via XtceTmExtractor + templates |
| MDB | `examples/pus/src/main/yamcs/mdb/pus5.xml` | XTCE definitions for all 8 message types |

### XTCE Container Hierarchy

```
pus5-tm  (type == 5, no payload)
  ├── pus5-event-report  (subtype < 5) — extracts event_id
  │     ├── event1  (restriction: event_id == EVENT_1) → event1_para1 (uint16), event1_para2 (float32)
  │     └── event2  (restriction: event_id == EVENT_2) → event2_msg (prepended_string)
  └── pus5-disabled-list  (subtype == 8) → disabled_count (uint8), disabled_event_ids (array)
```

### PusEventDecoder Flow

```
TM stream (tm_realtime)
  → PusEventDecoder.StreamEventDecoder.onTuple()
      → guards: type == 5, subtype in [1,4]
      → XtceTmExtractor.processPacket()  — walks XTCE tree, evaluates restrictions
      → extracts event_id + auxiliary parameters
      → EventFormatter.format(apid, eventId, params)  — looks up YAML template by eventId
      → emits Event proto on events_realtime stream
```

---

## Native YAMCS Implementation

### Feasibility: YES — IMPLEMENTED ✅

PUS ST[05] is implemented natively using XTCE MDB definitions + `Pus5Service`. The existing `pus5.xml` already defines all 8 required message types — **no new XTCE definitions are needed**.

### Actual Class: `org.yamcs.pus.Pus5Service`

Pattern: extend `PusTcHandler`, registered under `PusCommandReleaser` in `processor.yaml`.  
See `pus_native_arch.md §2` for the full dispatcher pattern.

**processor.yaml config:**
```yaml
- class: org.yamcs.pus.PusCommandReleaser
  args:
    apid: 1
    timeEncoding:
      implicitPfield: false
      pfield: 0x2f
    handlers:
      - serviceType: 5
        class: org.yamcs.pus.Pus5Service
        args:
          eventIdParameter: /PUS5/event_id   # FQN of the EnumeratedParameterType in the MDB
          defaultEnabled: true               # optional, default true
```

**Event registry seeded from MDB** (not from a hand-maintained list in config):
```java
var mdb = MdbFactory.getInstance(releaser.getYamcsInstance());
Parameter param = mdb.getParameter(paramFqn);              // e.g. /PUS5/event_id
EnumeratedParameterType ept = (EnumeratedParameterType) param.getParameterType();
for (var ve : ept.getValueEnumerationList()) {
    enabled.put((int) ve.getValue(), defaultEnabled);
}
```
Adding a new event only requires a new `<Enumeration>` in `pus5.xml` — no `processor.yaml` change.

---

## Per-Message Implementation Plan

### TM[5,1] Informative Event Report
**Direction**: TM (native service → TM stream → PusEventDecoder → YAMCS events)

**XTCE**: `pus5-event-report` + per-event sub-containers (already complete in `pus5.xml`).

**Java**: `raiseEvent(eventId, 1, auxData)` → build packet → emit on `tm_realtime`.

Packet layout:
```
[0..5]   CCSDS primary header (TM, APID)
[6]      PUS secondary header: 0x21 (version=2, scRefStatus=1)
[7]      service type = 5
[8]      subtype = 1
[9..10]  message type counter (uint16, big-endian)
[11..12] destination ID = 0 (ground)
[13..T]  CUC time (CucTimeEncoder)
[T+1]    event_id (uint8, matches IntegerDataEncoding in MDB)
[T+2..]  aux data bytes (must exactly match sub-container field layout)
[-2..-1] CRC-16-CCIIT
```

**Emit TM tuple** — `StandardTupleDefinitions.TM` has 9 columns; all must be provided (last 4 nullable):
```java
long now = processor.getCurrentTime();
int seqNum = ((pkt[2] & 0x3F) << 8) | (pkt[3] & 0xFF);
// columns: gentime, seqNum, rectime, status, packet, ertime, obt, link, rootContainer
Tuple t = new Tuple(StandardTupleDefinitions.TM,
    new Object[]{ now, seqNum, now, 0, pkt, null, null, null, null });
tmStream.emitTuple(t);
```
⚠️ `Tuple` validates that column count == definition size; passing fewer values throws `IllegalArgumentException`.

---

### TM[5,2] Low Severity Anomaly Report
Same as TM[5,1] with `subtype = 2`. No additional work beyond calling `raiseEvent(eventId, 2, auxData)`.

---

### TM[5,3] Medium Severity Anomaly Report
Same as TM[5,1] with `subtype = 3`.

---

### TM[5,4] High Severity Anomaly Report
Same as TM[5,1] with `subtype = 4`.

---

### TC[5,5] Enable Report Generation
**Direction**: TC (ground → `Pus5Service`)

**XTCE**: `ENABLE_REPORT_GENERATION` MetaCommand (already in `pus5.xml`).
Arguments: `N` (uint8), `events` (array of event_id_type).

**Java** (`enableDisableEvents(pc, true)`):
```java
void enableDisableEvents(PreparedCommand pc, boolean enable) {
    byte[] bin = pc.getBinary();
    int offset = APP_DATA_OFFSET;
    int n = bin[offset++] & 0xFF;
    boolean anyFailure = false;
    for (int i = 0; i < n; i++) {
        int eventId = bin[offset++] & 0xFF;    // matches uint8 IntegerDataEncoding in MDB
        if (!enabled.containsKey(eventId)) {
            nackCompletion(pc, COMPL_ERR_INVALID_EVENT_ID);
            anyFailure = true;
            // continue — §6.5.5.2f: process valid instructions regardless of faulty ones
        } else {
            enabled.put(eventId, enable);
        }
    }
    if (!anyFailure) ackCompletion(pc);
}
```

**Ack flow**: `ack_sent` (published before switch), then `ack_completion` or `nack_completion` per instruction.

---

### TC[5,6] Disable Report Generation
**Direction**: TC (ground → `Pus5Service`)

**XTCE**: `DISABLE_REPORT_GENERATION` MetaCommand (already in `pus5.xml`). Same arguments as TC[5,5].

**Java**: `enableDisableEvents(pc, false)` — identical logic with `enable = false`.

---

### TC[5,7] Report List of Disabled Event Definitions
**Direction**: TC (ground → `Pus5Service`) → triggers TM[5,8]

**XTCE**: `REPORT_DISABLED_LIST` MetaCommand (already in `pus5.xml`). No arguments.

**Java** (`sendDisabledList(pc)`):
```java
void sendDisabledList(PreparedCommand pc) {
    List<Integer> disabled = enabled.entrySet().stream()
        .filter(e -> !e.getValue())
        .map(Map.Entry::getKey)
        .sorted()
        .collect(Collectors.toList());

    int auxLen = 1 + disabled.size();     // N byte + N × event_id bytes
    byte[] aux = new byte[auxLen];
    aux[0] = (byte) disabled.size();
    for (int i = 0; i < disabled.size(); i++) aux[1 + i] = (byte) disabled.get(i).intValue();

    byte[] pkt = buildTm(8, aux);          // subtype = 8
    emitTm(pkt);
    ackCompletion(pc);
}
```

---

### TM[5,8] Disabled Event Definitions List Report
**Direction**: TM (native service → TM stream, in response to TC[5,7])

**XTCE**: `pus5-disabled-list` container (already in `pus5.xml`).
Layout: `disabled_count (uint8)` + `disabled_event_ids (array, IncludeCondition: count > 0)`.

**Java**: Packet application data = `[N, id_0, id_1, ..., id_{N-1}]`.
- N = 0: just the count byte, no event IDs. The MDB's IncludeCondition prevents the empty-array crash.
- N > 0: count byte + N event_id bytes (each uint8).

**Processor config**: Replace `StreamTcCommandReleaser` with `PusCommandReleaser` (with `Pus5Service` handler) in `processor.yaml`.
`PusEventDecoder` remains as an instance-level service in `yamcs.<instance>.yaml` (not processor-level).

---

## Gaps and Shortcomings

### Per-Message

| Message | Gap | Severity |
|---------|-----|----------|
| TM[5,1-4] | Event registry is **static** (declared at MDB load time). Adding a new event type at runtime requires a new `<Enumeration>` in `pus5.xml` + YAMCS restart. | Medium |
| TM[5,1-4] | ~~Enabled/disabled state is in-memory only~~ — **FIXED**: state is persisted in `MementoDb` (key `pus5.enabled`); survives restarts. | ~~Medium~~ ✅ |
| TM[5,1-4] | Auxiliary data is "deduced" — each event type needs its own XTCE sub-container AND matching Java byte layout. There is no generic mechanism; adding an event requires both MDB and Java changes in sync. | Medium |
| TM[5,1-4] | Subtype 3 maps to YAMCS `DISTRESS` (not `WARNING`). This is per-spec but may surprise operators expecting a WARNING level. | Low |
| TC[5,5/6] | Event IDs are encoded as raw bytes (uint8) matching the MDB `IntegerDataEncoding` default. If the mission changes event IDs to uint16, **both** the MDB and the Java parsing code must be updated together. | Low |
| TC[5,7] / TM[5,8] | The **Complete verifier** for TC[5,7] should be a `ContainerVerifier` on `pus5-disabled-list`, not a `Pus1Verifier`, because TM[5,8] is not a PUS1 acknowledgement packet. This verifier is **not yet defined** in the MDB — it is an open item. | Medium |
| All | §6.5.6 observables (accumulated event occurrences, count of disabled definitions, accumulated generated reports, last event definition ID, last event generation time — per severity) are **not implemented**. | Low |

### Architectural

| Limitation | Detail |
|------------|--------|
| No broadcast | If multiple YAMCS instances run, the enabled/disabled registry state is not shared. |
| TC verification gap | For TC[5,5/6], PUS1 verifiers handle the ack. For TC[5,7], a `ContainerVerifier` is required — this is different from all other ST[05] TCs. |
| PusEventDecoder dependency | `PusEventDecoder` must remain active as an instance service; it is the only path that converts TM[5,1-4] packets into YAMCS native events. The native service only generates the PUS TM packets. |
| Template file required | `PusEventDecoder` requires an `eventTemplateFile` (YAML) with a template entry per event ID. Missing templates result in warnings and no YAMCS event, though the raw TM packet is still archived. |

---

## Bugs Fixed (Prior Work)

### ✅ 1. TM[5,8] leaks into event pipeline
**`PusEventDecoder.StreamEventDecoder.onTuple()`** — Added subtype guard: `if (subtype < 1 || subtype > 4) return;`

### ✅ 2. Container hierarchy mismatch on TM[5,8]
**`pus5.xml`** — Restructured to `pus5-tm` (thin base) → `pus5-event-report` (subtypes 1–4) and `pus5-disabled-list` (subtype 8).

### ✅ 3. Empty array crash on TM[5,8] with N=0
**`pus5.xml`** — Added `IncludeCondition` on `disabled_event_ids`: only extracted when `disabled_count > 0`.

### ✅ 4. `Pus1Verifier` false failure on TC[5,7]
**`Pus1Verifier.java`** + **`pus.xml`** — Added `type` as input[6]; verifier now returns `NO_RESULT` for any packet where service type ≠ 1.

### ✅ 5. Hardcoded event registry
**`Pus5Service.java`** — Replaced `boolean[] enabled` with `Map<Integer, Boolean> enabled`. Unknown event IDs rejected via `!enabled.containsKey(eventId)`.

---

## Mission-Specific Items

### ⚠️ `prepended_string` encoding
`/dt/prepended_string` is a YAMCS-specific 2-byte-length-prefixed string. Real FSW typically uses fixed-length fields, null-terminated strings, or a 1-byte prefix. Replace with the actual onboard encoding.

### ⚠️ Event ID bit width
MDB and Java both assume uint8 event IDs. If the mission uses 16-bit IDs:
- MDB: add `<IntegerDataEncoding sizeInBits="16"/>` to both `EnumeratedParameterType` and `EnumeratedArgumentType`
- Java: use `bb.getShort() & 0xFFFF` / `bb.putShort((short) id)` everywhere

### ⚠️ TC[5,7] Complete Verifier (open item)
TC[5,7]'s response TM[5,8] is not a PUS1 ack packet. A `ContainerVerifier` on `pus5-disabled-list` needs to be added to the MetaCommand's `CommandVerifierSet` in `pus5.xml`.

---

## Observables Required by Spec (§6.5.6, not implemented)

Per severity level:
- Accumulated event occurrences
- Count of disabled definitions
- Accumulated generated reports
- Last event definition ID
- Last event generation time

---

## Build & Run

```
mvn -pl yamcs-core,simulator,examples/pus clean install -DskipTests
mvn -pl examples/pus yamcs:run
```

YAMCS starts on http://localhost:8090. Simulator binds TCP TM on port 10015, TC on 10025.

---

## Testing

### Passive — watch events roll in
- Events tab: EVENT_1 and EVENT_2 appear every second.
- EVENT_1 fires every 5th tick (subtype 1 = informative). EVENT_2 fires on ticks 1–4 (subtypes 1–4).

### TC[5,6] — Disable an event
1. MDB → Commands → `/PUS5/DISABLE_REPORT_GENERATION`
2. Set N=1, events=[EVENT_1], send
3. Verify `Verifier_Complete OK` in command history
4. Verify EVENT_1 stops appearing in the Events tab

### TC[5,7] — Query disabled list
1. Send `/PUS5/REPORT_DISABLED_LIST` (no arguments)
2. Verify `Verifier_Started OK` and `Verifier_Complete OK` in command history
3. Parameters → `/PUS5/disabled_count` = 1, `/PUS5/disabled_event_ids` = [EVENT_1]
4. TM[5,8] raw packet visible at Links → tm_realtime → Packets

### TC[5,5] — Re-enable
1. Send `/PUS5/ENABLE_REPORT_GENERATION` with N=1, events=[EVENT_1]
2. EVENT_1 resumes in Events tab
3. Send TC[5,7] again → `disabled_count` = 0

### Invalid event ID
1. Send TC[5,6] with N=2, events=[EVENT_1, 99]
2. Expect `Verifier_Complete NOK` (nack-completion for event ID 99)
3. EVENT_1 should still be disabled (per-instruction processing per §6.5.5.3f)

---

## Useful UI Locations

| What | Where |
|------|-------|
| Command acks / verifier status | Commanding → Command History |
| Live events | Events tab |
| Parameter values (`disabled_count` etc.) | Parameters → search `/PUS5/` |
| Raw TM packet stream | Links → tm_realtime → Packets |
