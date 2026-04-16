# PUS ST[05] — Event Reporting Service
> ECSS-E-ST-70-41C (15 April 2016), §6.5 (pp. 121–126) and §8.5 (pp. 477–480)

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

## Key Concepts

- **Event Definition**: A declared reportable event, identified by an *event definition identifier* unique within its application process. Has a fixed severity level and optional auxiliary data structure.
- **Event Definition System Identifier**: apid + event definition identifier (globally unique).
- **Event Report Generation Status**: Per-event-definition flag — `enabled` or `disabled`. Initial value declared at spec time.

---

## Packet Structures

### TM[5,1–4] — Event Reports
```
| event definition ID | auxiliary data        |
| enumerated          | deduced (see below)   |
```
Subtype encodes severity: 1=informative, 2=low, 3=medium, 4=high.

### TC[5,5] Enable / TC[5,6] Disable
```
| N (uint)  | event definition ID (enum) | × N |
```
- Reject unknown event IDs → `nack_completion` per bad instruction
- Valid instructions processed regardless of faulty ones in the same TC (§6.5.5.2f)

### TC[5,7] Report disabled list
No application data.

### TM[5,8] Disabled event definitions list report
```
| N (uint)  | event definition ID (enum) | × N |
```

---

## Auxiliary Data — "Deduced" Explained

"Deduced presence" means no type/length tag is embedded in the packet — the receiver infers structure solely from the event ID. The contract is:

**Definition (`pus5.xml`)** — XTCE container inheritance:
```
pus5-tm (type==5, no entries)
  ├── pus5-event-report (subtype < 5) — extracts event_id
  │     ├── event1  (restriction: event_id == EVENT_1)
  │     │     ├── event1_para1  uint16
  │     │     └── event1_para2  float32
  │     └── event2  (restriction: event_id == EVENT_2)
  │           └── event2_msg    prepended_string
  └── pus5-disabled-list (subtype == 8) — extracts disabled_count, disabled_event_ids
```
Only the matching sub-container is parsed; no other aux data is extracted.

**Generation (`Pus5Service.java`)** — bytes must exactly match the MDB layout:
```java
// EVENT_1: event_id (1B) + uint16 (2B) + float32 (4B)
bb.put((byte) 1); bb.putShort((short) count); bb.putFloat(value);

// EVENT_2: event_id (1B) + length-prefixed string
bb.put((byte) 2); bb.putShort((short) msg.length); bb.put(msg);
```

**Parsing** — `PusEventDecoder` calls `XtceTmExtractor.processPacket()`, which walks the XTCE tree and evaluates restriction criteria. Returns a flat `ParameterValueList` of all matched parameters.

**Formatting** — `EventFormatter` looks up a template by the event ID label and substitutes parameter values by name:
```json
{ "eventId": "EVENT_1", "template": "Event 1: para1={event1_para1} and para2={event1_para2; %3.3f}" }
```

To add a new event type: add enum entry in `pus5.xml`, add sub-container with its parameters, write the bytes in `Pus5Service.sendEvent()`, add template in `events.json`.

---

## Bugs Fixed

### ✅ 1. TM[5,8] leaks into event pipeline
**`yamcs-core/src/main/java/org/yamcs/pus/PusEventDecoder.java`**

`PusEventDecoder` filtered only on `type == 5`, so TM[5,8] entered the event formatting path. Fixed by adding a subtype guard in `StreamEventDecoder.onTuple()` after the subtype is read:
```java
if (subtype < 1 || subtype > 4) return;  // only TM[5,1-4] are event reports
```

### ✅ 2. `pus5-tm` mismatch on TM[5,8]
**`examples/pus/src/main/yamcs/mdb/pus5.xml`**

Restructured the container hierarchy. `pus5-tm` is now a thin base (no entries). A new intermediate container `pus5-event-report` gates subtype 1–4 packets and extracts `event_id`. `event1` and `event2` inherit from it. `pus5-disabled-list` (subtype==8) inherits directly from `pus5-tm`. See container tree above.

### ✅ 3. Empty array crash on TM[5,8] with no disabled events
**`examples/pus/src/main/yamcs/mdb/pus5.xml`**

When `disabled_count == 0`, the `DynamicValue` dimension evaluates to -1, creating an empty `ArrayValue`. YAMCS `calibrateArray` then crashes with `ArrayIndexOutOfBoundsException`. Fixed with an `IncludeCondition` on the `disabled_event_ids` entry:
```xml
<ParameterRefEntry parameterRef="disabled_event_ids">
    <IncludeCondition>
        <Comparison parameterRef="disabled_count" comparisonOperator="&gt;" value="0"/>
    </IncludeCondition>
</ParameterRefEntry>
```

### ✅ 4. `Pus1Verifier` false failure on TC[5,7]
**`yamcs-core/src/main/java/org/yamcs/pus/Pus1Verifier.java`** and **`mdb/pus.xml`**

After TM[1,3] (ack start) sets `tc-ack-apid` and `tc-ack-seq`, TM[5,8] arrives with `subtype=8`. The `CompleteVerifier` algorithm fired with those stale (but matching) values, treating it as a TM[1,8] failure.

`Yamcs:AlgorithmMandatoryInput` only prevents the algorithm from running when a value has *never* been received — it does not require the value to be freshly updated in the current packet. So stale values still trigger the algorithm.

Fixed by:
- Adding `type` as input[6] to the Started and Complete verifiers in `pus.xml`
- Adding a type guard in `Pus1Verifier.java`: if `input[6]` is present and `!= 1`, return `NO_RESULT`

This means the verifiers now ignore any TM packet that isn't a ST[01] packet.

### ✅ 5. Hardcoded event registry
**`simulator/src/main/java/org/yamcs/simulator/pus/Pus5Service.java`**

Replaced `boolean[] enabled` with `Map<Integer, Boolean> enabled`. Event IDs are now looked up by key; unknown IDs are rejected via `!enabled.containsKey(eventId)`. Adding new mission events requires only a new entry in the map declaration.

---

## Mission-Specific Items (not fixable generically)

### ⚠️ `prepended_string` encoding
**`examples/pus/src/main/yamcs/mdb/pus5.xml`**

`/dt/prepended_string` is a YAMCS-specific 2-byte-length-prefixed string. Real FSW typically uses fixed-length fields, null-terminated strings, or a 1-byte prefix. Auxiliary data layouts are mission-specific — replace with the appropriate parameter types for the actual onboard encoding.

### ⚠️ Event ID bit width
**`pus5.xml`** and **`Pus5Service.java`**

The MDB and all TC/TM read/write calls assume the default XTCE integer encoding size (inferred from the enumeration values). PUS spec does not mandate a size; many missions use 8-bit or 16-bit event IDs.

If explicit sizing is needed:
- MDB: add `<IntegerDataEncoding sizeInBits="N"/>` to both `EnumeratedParameterType` and `EnumeratedArgumentType`
- Java: use `bb.get() & 0xFF` / `bb.put((byte) id)` for 8-bit, or `bb.getShort() & 0xFFFF` / `bb.putShort((short) id)` for 16-bit (in `enableDisableEvents` and `sendDisabledList`)

### ⚠️ PUSVerifier Update
**`PusVerifier.java`**
- Needs update to correctly acknowledge TC[5,7]'s response of TM[5,8]

---

## Observables Required by Spec (§6.5.6, not implemented)

Per severity level: accumulated event occurrences, count of disabled definitions, accumulated generated reports, last event definition ID, last event generation time.

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
3. EVENT_1 should still be disabled (per-instruction processing per §6.5.5.2f)

---

## Useful UI Locations

| What | Where |
|------|-------|
| Command acks / verifier status | Commanding → Command History |
| Live events | Events tab |
| Parameter values (`disabled_count` etc.) | Parameters → search `/PUS5/` |
| Raw TM packet stream | Links → tm_realtime → Packets |
