# PUS ST[09] Time Management

---

## 1. General Context (§6.9, ECSS-E-ST-70-41C)

PUS Service Type 9 (ST[09]) provides **time management**: the spacecraft periodically
downlinks CUC (or CDS) time reports so the ground can correlate on-board time to UTC.
The ground can also adjust the reporting rate dynamically.

### Two subservices

| Subservice | Purpose |
|------------|---------|
| **Time reporting** | Periodic TM[9,2] / TM[9,3] packets carrying spacecraft OBT |
| **Time reporting control** | TC[9,1] sets the report generation rate |

### Key spec constraints (§6.9.2 – 6.9.5)

- Exactly **one** time management service per spacecraft, hosted on **APID 0**.
- Time packets have **no secondary header** (secondary header flag = 0). Service type
  and subtype are therefore NOT present in the packet — message type (9,2) is implicit
  from APID=0.
- Rate is expressed as an exponent: `period = 2^rateExponent` seconds.
  - Valid exponent range: **0–8** (1 s to 256 s per §6.9.3c NOTE 1).
- TC[9,1] contains exactly one byte: the `rateExponent`.
  - If the exponent is out of range, the service shall reject with a failed-start
    execution notification (§6.9.5.1.1d).
- Spec §6.9.4.4 ties report generation to VC0 transfer frame count, but this is
  satellite-side scheduling detail; a YAMCS native implementation uses wall-clock
  scheduling instead.

### Scope for this implementation

| Subtype | Direction | Required |
|---------|-----------|----------|
| TC[9,1] | TC | Yes |
| TM[9,2] | TM | Yes |

---

## 2. Native YAMCS-Core Implementation

> **Verdict: Fully feasible.** ST[09] native implementation requires **XTCE definitions
> (already exist) + one new Java service class**. No new XTCE changes are needed.

### 2.1 XTCE / MDB — what exists, what's needed

#### TC[9,1] — `SET_TIME_REPORT_RATE`

Already defined in `examples/pus/src/main/yamcs/mdb/pus9.xml`:

```xml
<MetaCommand name="SET_TIME_REPORT_RATE">
  <!-- inherits APID=1, type=9 from pus9-tc abstract base -->
  <CommandContainer name="SET_TIME_REPORT_RATE">
    <EntryList>
      <ArgumentEntry argumentRef="rateExponent" />
    </EntryList>
    <BaseContainer containerRef="pus9-tc">
      <RestrictionCriteria>
        <Comparison parameterRef="subtype" value="1" />
      </RestrictionCriteria>
    </BaseContainer>
  </CommandContainer>
</MetaCommand>
```

- Argument `rateExponent`: uint8, acceptable range 0–8 (enforced in Java).
- **No XTCE changes needed** for TC[9,1].

#### TM[9,2] — CUC time report

Already decoded by the `pus-time` SequenceContainer in
`examples/pus/src/main/yamcs/mdb/pus.xml`:

```xml
<SequenceContainer name="pus-time">
  <EntryList>
    <!-- bit 48 from containerStart = byte 6 (after 6-byte CCSDS header) -->
    <ParameterRefEntry parameterRef="time-rate">
      <LocationInContainerInBits referenceLocation="containerStart">
        <FixedValue>48</FixedValue>
      </LocationInContainerInBits>
    </ParameterRefEntry>
    <ParameterRefEntry parameterRef="time-type" />   <!-- P-field, 1 byte -->
    <ParameterRefEntry parameterRef="obt-coarse" />  <!-- 4 bytes -->
    <ParameterRefEntry parameterRef="obt-fine" />    <!-- 3 bytes -->
  </EntryList>
  <BaseContainer containerRef="ccsds">
    <RestrictionCriteria>
      <Comparison parameterRef="apid" comparisonOperator="==" value="0" />
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>
```

The container matches on `apid == 0` only. APID=0 is sufficient per spec — no
service type/subtype fields are present in TM[9,2]. The decoded parameters
(`time-rate`, `obt-coarse`, `obt-fine`, `pus-time`) are available in the YAMCS
parameter archive after injection.

**No XTCE changes needed for TM[9,2].**

---

### 2.2 Java — `Pus9TimeManagementService`

Path: `yamcs-core/src/main/java/org/yamcs/pus/Pus9TimeManagementService.java`

Follows the same pattern as `Pus21RequestSequencingService`.

#### Class structure

```java
public class Pus9TimeManagementService extends StreamTcCommandReleaser {

    static final int SERVICE_TYPE = 9;
    static final int APP_DATA_OFFSET = 11; // 6 CCSDS + 5 TC secondary header

    // config
    int apid;                          // APID for generated TM[9,2] — fixed 0 per spec
    int defaultRateExponent = 2;       // 2^2 = 4 s default

    // runtime state
    volatile int rateExponent;
    ScheduledFuture<?> scheduledFuture;
    ScheduledExecutorService executor;
    Stream tmStream;
    CommandHistoryPublisher commandHistory;
    CucTimeEncoder timeEncoder;
    CrcCciitCalculator crcCalculator;
    AtomicInteger msgCounter = new AtomicInteger(0);
}
```

#### `init(YConfiguration config)`

```java
apid = config.getInt("apid", 0);   // must be 0 per PUS spec
rateExponent = config.getInt("defaultRateExponent", 2);
// init CucTimeEncoder from timeEncoding sub-config
// (same as Pus21RequestSequencingService: pfield=0x2F, implicitPfield=false)

// resolve realtime TM stream
String instance = processor.getInstance();
YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
for (StreamConfig.TmStreamConfigEntry e :
        StreamConfig.getInstance(instance).getTmEntries()) {
    if ("realtime".equals(e.getProcessor())) {
        tmStream = ydb.getStream(e.getName());
        break;
    }
}
```

#### `doStart()`

```java
executor = Executors.newSingleThreadScheduledExecutor();
reschedule(rateExponent);
notifyStarted();
```

#### `releaseCommand(PreparedCommand pc)`

```java
byte[] binary = pc.getBinary();
if (binary != null && (binary[7] & 0xFF) == SERVICE_TYPE) {
    int subtype = binary[8] & 0xFF;
    if (subtype == 1) {
        handleSetRate(pc, binary);
    } else {
        // unknown subtype — NACK start
        publishNackStart(pc, "Unknown ST[09] subtype: " + subtype);
    }
} else {
    super.releaseCommand(pc);   // non-ST[09] → TC stream as normal
}
```

#### TC[9,1] handler — `handleSetRate()`

```java
private void handleSetRate(PreparedCommand pc, byte[] binary) {
    int exp = binary[APP_DATA_OFFSET] & 0xFF;
    if (exp > 8) {
        publishNackStart(pc, "rateExponent " + exp + " exceeds max 8");
        return;
    }
    publishAckSent(pc);
    rateExponent = exp;
    reschedule(exp);
    publishCompletion(pc, true, null);
}
```

#### `reschedule(int exp)`

```java
private synchronized void reschedule(int exp) {
    if (scheduledFuture != null) {
        scheduledFuture.cancel(false);
    }
    long periodSec = 1L << exp;    // 2^exp seconds
    scheduledFuture = executor.scheduleAtFixedRate(
        this::sendTimePacket, 0, periodSec, TimeUnit.SECONDS);
}
```

#### TM[9,2] packet builder — `sendTimePacket()`

Packet layout (17 bytes total):

```
Byte  0-1:  CCSDS word 0 — APID=0, sec_hdr_flag=0, type=TM (0)
            = (0 << 13) | (0 << 12) | (0 << 11) | 0  →  0x0000
Byte  2-3:  sequence flags=3 (unsegmented), seqCount (rolling)
Byte  4-5:  packet data length = 17 - 6 - 1 = 10  →  0x000A
Byte  6:    rateExponent (uint8)
Byte  7:    P-field = 0x2F  (CUC 1+4+3 format)
Byte  8-11: OBT coarse (uint32, big-endian, seconds since mission epoch)
Byte 12-14: OBT fine   (uint24, big-endian, 2^-24 s units)
Byte 15-16: CRC-16-CCIIT
```

```java
private void sendTimePacket() {
    long nowMs = processor.getCurrentTime();
    byte[] pkt = new byte[17];

    // CCSDS primary header — APID=0, no secondary header, TM
    pkt[0] = 0x00;
    pkt[1] = 0x00;
    // sequence: unsegmented (0xC000) | rolling counter
    int seq = msgCounter.getAndIncrement() & 0x3FFF;
    pkt[2] = (byte) (0xC0 | (seq >> 8));
    pkt[3] = (byte) (seq & 0xFF);
    // data length = total - 6 - 1 = 10
    pkt[4] = 0x00;
    pkt[5] = 0x0A;

    // application data
    pkt[6] = (byte) rateExponent;

    // CUC time: encode via CucTimeEncoder
    // pfield=0x2F → 1 pfield + 4 coarse + 3 fine
    byte[] timeBytes = timeEncoder.encode(nowMs);  // returns 8 bytes
    System.arraycopy(timeBytes, 0, pkt, 7, 8);

    // CRC
    int crc = CrcCciitCalculator.compute(pkt, 0, 15);
    pkt[15] = (byte) (crc >> 8);
    pkt[16] = (byte) (crc & 0xFF);

    // Inject on TM stream
    int seqCount = ((pkt[2] & 0x3F) << 8) | (pkt[3] & 0xFF);
    TupleDefinition td = StandardTupleDefinitions.TM.copy();
    Tuple t = new Tuple(td, new Object[]{nowMs, seqCount, nowMs, 0, pkt});
    tmStream.emitTuple(t);
}
```

#### Command history helpers

```java
@Override
public void setCommandHistory(CommandHistoryPublisher chp) {
    this.commandHistory = chp;
}

private void publishAckSent(PreparedCommand pc) {
    commandHistory.publishAck(pc.getCommandId(),
        CommandHistoryPublisher.AcknowledgeSent_KEY,
        processor.getCurrentTime(), AckStatus.OK);
}

private void publishNackStart(PreparedCommand pc, String msg) {
    commandHistory.publishAck(pc.getCommandId(),
        CommandHistoryPublisher.AcknowledgeSent_KEY,
        processor.getCurrentTime(), AckStatus.NOK, msg);
}

private void publishCompletion(PreparedCommand pc, boolean ok, String msg) {
    commandHistory.publishAck(pc.getCommandId(),
        CommandHistoryPublisher.CommandComplete_KEY,
        processor.getCurrentTime(),
        ok ? AckStatus.OK : AckStatus.NOK, msg);
}
```

---

### 2.3 Processor / YAML configuration

In `processors.yaml` (or `yamcs.<instance>.yaml` under `processorConfig`):

```yaml
services:
  - class: org.yamcs.pus.Pus9TimeManagementService
    args:
      apid: 0
      defaultRateExponent: 2
      timeEncoding:
        implicitPfield: false
        pfield: 0x2F
```

Remove standalone `StreamTcCommandReleaser` if present; non-ST[09] TCs are
forwarded via `super.releaseCommand()`.

---

## 3. Gaps and Shortcomings

### TC[9,1]

| Gap | Severity | Detail |
|-----|----------|--------|
| No TM[1,x] PUS-1 ack packets generated | **Medium** | The native service publishes to YAMCS command history (`AcknowledgeSent`, `CommandComplete`) but does NOT build or inject TM[1,3]/TM[1,4] raw PUS packets. XTCE verifiers configured via `Pus1Verifier` will never fire because the TC is never forwarded to a spacecraft. If ST[01] ack packets are required in the TM stream, the native service must explicitly build and inject them (add `buildPusTmPacket(1, 3/4, ...)` calls before/after rate change). |
| In-memory rate state, lost on restart | **Low** | `rateExponent` resets to `defaultRateExponent` on YAMCS restart. Persist to a YAMCS `Table` (via Yarch) if durability is required. |
| No XTCE-level range check | **Info** | The XTCE argument definition for `rateExponent` does not enforce 0–8 at encoding time. Validation is Java-only (service handler). Could add a `ValidRange` element to the XTCE arg definition to block out-of-range values in the ground UI. |

### TM[9,2]

| Gap | Severity | Detail |
|-----|----------|--------|
| TCO (Time Correlation) not updated | **Low** | `PusPacketPreprocessor` normally calls `tcoService.addSample(obt, ert)` when it receives APID=0 packets. Native TM injection bypasses the preprocessor entirely, so the TCO service receives no samples from these packets. Since YAMCS IS the time source in native mode, OBT ≡ wall clock and TCO is not needed for ground correlation — this is a moot gap unless a real spacecraft is also injecting APID=0 packets. |
| Transfer-frame-triggered sampling not implemented | **Low** | PUS spec §6.9.4.4: reports are triggered per VC0 transfer frame count. YAMCS uses a wall-clock `ScheduledExecutorService`. Semantic divergence is acceptable for ground-side functional testing. |
| APID=0 collision with real spacecraft | **Low** | If a live spacecraft simultaneously sends APID=0 time packets on the same TM stream, they will mix with native-generated packets. Only affects mixed (native + live) deployments; not relevant for native-only test setups. |
| Spacecraft time reference status field omitted | **Info** | TM[9,2] optionally carries a `spacecraft time reference status` field (§6.9.4.2e). The existing `pus-time` XTCE container and 17-byte packet layout do not include it. Not required per scope, but would need XTCE + Java changes to add. |
| Duplicate `(gentime, seqNum)` archive key | **Info** | YAMCS `tm` table primary key is `(gentime, seqNum)`. If two TM[9,2] packets share the same millisecond gentime and rolling seqCount (unlikely at normal rates), the second is silently discarded. Use a monotonic seqCounter scoped to APID=0 and ensure `processor.getCurrentTime()` advances between packets. |

---

## 4. Simulator-Side Implementation (Reference)

The simulator implements ST[09] in `Pus9Service.java` as an `AbstractPusService`
subclass. This is separate from the native YAMCS-core service above.

### Files changed (simulator)

| File | Change |
|------|--------|
| `simulator/.../pus/Pus9Service.java` | New: ST[09] service, intercepts TC[9,1], schedules TM[9,2] |
| `simulator/.../pus/PusTmTimePacket.java` | Changed constructor to accept `rateExponent` arg |
| `simulator/.../pus/PusSimulator.java` | Replaced inline time-packet schedule with `pus9Service.start()` |
| `simulator/.../pus/AbstractPusService.java` | Added `START_ERR_INVALID_RATE_EXPONENT = 3` |
| `examples/.../mdb/pus.xml` | Added `INVALID_RATE_EXPONENT` (value=3) to `start-failure-code` enum |
| `examples/.../mdb/pus9.xml` | New: XTCE TC[9,1] definition |
| `examples/.../etc/yamcs.pus.yaml` | Added `pus9.xml` to MDB loader list |

### Packet structures

#### TM[9,2] CUC Time Report (APID=0, no secondary header, 17 bytes)

```
Byte  0-1:  CCSDS primary header word 1 (version=0, type=TM, 2ndHdr=0, APID=0)
Byte  2-3:  Sequence flags + sequence count
Byte  4-5:  Packet data length
Byte  6:    rateExponent (uint8)
Byte  7:    P-field = 0x2F (CUC 1+4+3)
Byte  8-11: OBT coarse (uint32)
Byte 12-14: OBT fine (uint24)
Byte 15-16: CRC-16-CCITT
```

#### TC[9,1] Set Time Report Generation Rate

```
Standard PUS TC header (APID=1, type=9, subtype=1) + CRC
App data byte 11: rateExponent (uint8, 0..8)
```

---

## 5. Testing

### Start the simulator
```bash
mvn -pl examples/pus yamcs:run
```

### Observe TM[9,2] time packets
YAMCS UI → Telemetry → Parameters:
- `/PUS/time-rate` — current rateExponent in last time packet
- `/PUS/pus-time` — decoded on-board time (correlated via `tco0`)
- `/PUS/obt-coarse`, `/PUS/obt-fine` — raw CUC time components

### Send TC[9,1] — change rate to 1 s (exponent=0)
Command: `SET_TIME_REPORT_RATE` with `rateExponent=0`

Expected:
- TM[1,3] (ACK start) received
- TM[1,7] (ACK completion) received
- Time packets now arrive every ~1 s
- `/PUS/time-rate` shows `0`

### Send TC[9,1] — invalid exponent
Command: `SET_TIME_REPORT_RATE` with `rateExponent=9`

Expected:
- TM[1,4] (NACK start) received with failure code `INVALID_RATE_EXPONENT` (3)
- Rate unchanged; time packets continue at previous period

---

## 6. Design Decisions

| Decision | Rationale |
|---|---|
| Rate as exponent (not direct seconds) | Matches PUS spec §6.9.3c: valid rates are powers of 2 (1, 2, 4 … 256 s) |
| `sendImmediate` for simulator time packets | Time packets fill CRC in constructor; `transmitRealtimeTM` would double-compute and corrupt CRC |
| APID=0 for time packets | Mandated by PUS spec §6.9.2.3a — reserved for time reporting subservice |
| TC MDB in separate `pus9.xml` | Consistent with pus5.xml / pus11.xml / pus17.xml pattern |
| TM[9,2] decoded in `pus.xml` `pus-time` container | Container already existed and correctly matches APID=0; no duplication needed |
| Default `rateExponent=2` (4 s) | Matches the original hardcoded 4-second schedule that was replaced |
| Native service: no TM[1,x] generation | Adds significant complexity; YAMCS command history acks are sufficient for ground-side functional testing |
