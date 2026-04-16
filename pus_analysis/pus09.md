# PUS ST[09] Time Management — Implementation Notes

## Overview

PUS Service Type 9 (ST[09]) provides time management: the spacecraft periodically downlinks a **CUC time report** (TM[9,2]) so the ground can correlate on-board time to UTC. The ground can also adjust the reporting rate via TC[9,1].

This implementation covers:
- **TM[9,2] CUC time report** — generated periodically at a configurable rate
- **TC[9,1] set the time report generation rate** — adjusts the period dynamically

---

## Spec Key Points (§6.9)

- One time management service per spacecraft, hosted on **APID 0**.
- Time packets have **no secondary header** (secondary header flag = 0). This means they carry no service type/subtype in the packet itself — the message type (9,2) is implicit.
- Rate is expressed as an exponent: `period = 2^rateExponent` seconds. Valid exponent range: 0–8 (1 s to 256 s).
- TC[9,1] contains a single byte: the `rateExponent`. Exponent > 8 is rejected with a failed-start notification.

---

## Files Changed

### New Files

#### `simulator/src/main/java/org/yamcs/simulator/pus/Pus9Service.java`
Implements the ST[09] service as an `AbstractPusService` subclass.

**How it works:**
- On `start()`, schedules a periodic task via `PusSimulator.executor` to call `sendTimePacket()`.
- `sendTimePacket()` constructs a `PusTmTimePacket(rateExponent)` and sends it via `tmLink.sendImmediate()`.
- On TC[9,1] (`executeTc`):
  1. Validates subtype == 1, else NACK start with `START_ERR_INVALID_PUS_SUBTYPE`.
  2. Reads the exponent byte from user data.
  3. If exponent > 8, sends NACK start with `START_ERR_INVALID_RATE_EXPONENT`.
  4. Otherwise: ACK start → update `rateExponent` → cancel old task and reschedule with new period → ACK completion.
- `reschedule(exp)` cancels the existing `ScheduledFuture` (if any) and schedules a new fixed-rate task with `initialDelay=0`, so the new rate takes effect immediately.

**Why `sendImmediate` instead of `transmitRealtimeTM`:**  
`PusTmTimePacket` fills its checksum in the constructor. `transmitRealtimeTM` calls `fillChecksum()` again on the packet, which would corrupt it. `tmLink.sendImmediate()` bypasses the extra checksum call and sends the raw bytes directly.

#### `examples/pus/src/main/yamcs/mdb/pus9.xml`
XTCE MDB for the TC side of ST[09].

**Structure:**
- Abstract base command `pus9-tc` inherits from `/PUS/pus-tc` with fixed `apid=1`, `type=9`.
- `SET_TIME_REPORT_RATE` inherits from `pus9-tc` with fixed `subtype=1`, and one argument: `rateExponent` (uint8).

**Why TM[9,2] is not defined here:**  
The CUC time report packet uses APID=0 with no secondary header. The existing `pus-time` container in `pus.xml` already matches all packets with `apid == 0` and decodes:
- `time-rate` (byte 6): the rateExponent
- `time-type`, `obt-coarse`, `obt-fine`: the raw CUC time fields
- `pus-time`: the decoded absolute time (via `TimeBinaryDecoder` with `tco0`)

No additional container is needed.

---

### Modified Files

#### `simulator/src/main/java/org/yamcs/simulator/pus/PusSimulator.java`

**Changes:**
1. Added `Pus9Service pus9Service` field.
2. Instantiated in constructor: `pus9Service = new Pus9Service(this)`.
3. In `doStart()`:
   - **Removed** the old inline `executor.scheduleAtFixedRate(() -> sendTimePacket(), 0, 4, TimeUnit.SECONDS)`.
   - Added `pus9Service.start()`.
4. **Removed** the old `sendTimePacket()` private method (which called `tmLink.sendImmediate(new PusTmTimePacket())`).
5. Added `case 9 -> pus9Service.executeTc(commandPacket)` in `executePendingCommands()`.

**Why:** The old implementation hardcoded a 4-second period with no TC control. Moving it to `Pus9Service` makes it consistent with other services and adds TC[9,1] support.

#### `simulator/src/main/java/org/yamcs/simulator/pus/PusTmTimePacket.java`

**Change:** Constructor signature changed from `PusTmTimePacket()` (hardcoded rate byte `2`) to `PusTmTimePacket(int rateExponent)` (dynamic).

**Why:** The rate exponent must reflect whatever rate is currently active in `Pus9Service`. The caller passes the current `rateExponent` value so it's embedded in each outgoing time packet, allowing the ground to know the generation rate from the packet itself.

#### `simulator/src/main/java/org/yamcs/simulator/pus/AbstractPusService.java`

**Change:** Added error code constant:
```java
static final int START_ERR_INVALID_RATE_EXPONENT = 3;
```

**Why:** `Pus9Service` needs to NACK start with a specific code when the rateExponent > 8. Code 3 is consistent with the `INVALID_RATE_EXPONENT` enum value defined in `pus.xml`.

#### `examples/pus/src/main/yamcs/mdb/pus.xml`

**Change:** Added `INVALID_RATE_EXPONENT` (value=3) to the `start-failure-code` enumeration:
```xml
<Enumeration label="INVALID_RATE_EXPONENT" value="3" />
```

**Note:** `INVALID_EVENT_ID` (ST[05]) and `INVALID_RATE_EXPONENT` (ST[09]) both map to value 3. This is intentional — they are contextually separate services and share the same numeric error slot.

#### `examples/pus/src/main/yamcs/etc/yamcs.pus.yaml`

**Change:** Added `pus9.xml` to the MDB loader list, right after `pus.xml`:
```yaml
- type: "xtce"
  spec: "mdb/pus9.xml"
```

**Why:** Without this, YAMCS would not load the TC[9,1] command definition, so `SET_TIME_REPORT_RATE` would be unavailable in the command stack.

#### `examples/pus/src/main/yamcs/etc/yamcs.yaml`

**Change:** `dataDir` changed from `/storage/yamcs-data` to `/tmp/yamcs-data`.

**Why:** Local development convenience — `/storage/yamcs-data` requires creating a system directory with special permissions. `/tmp/yamcs-data` works out of the box on macOS/Linux dev machines.

---

## Packet Structure

### TM[9,2] CUC Time Report (APID=0, no secondary header)

```
Byte  0-1:  CCSDS primary header word 1 (version=0, type=TM, 2ndHdr=0, APID=0)
Byte  2-3:  Sequence flags + sequence count
Byte  4-5:  Packet data length
Byte  6:    rateExponent (uint8)  ← time-rate parameter
Byte  7:    P-field / time-type (CUC preamble, uint8)
Byte  8-11: OBT coarse (uint32)
Byte 12-14: OBT fine (uint24)
Byte 15-16: CRC-16-CCITT
```

Total: 17 bytes.

### TC[9,1] Set Time Report Generation Rate

```
Standard PUS TC header (APID=1, type=9, subtype=1) + CRC
App data (1 byte): rateExponent (uint8, 0..8)
```

---

## Testing

### Starting the simulator
```bash
mvn -pl examples/pus yamcs:run
```

### Observe TM[9,2] time packets
In YAMCS UI → Telemetry → Parameters:
- `/PUS/time-rate` — current rateExponent embedded in last time packet
- `/PUS/pus-time` — decoded on-board time (correlated via `tco0`)
- `/PUS/obt-coarse`, `/PUS/obt-fine` — raw CUC time components

### Send TC[9,1] — change rate to 1 second (exponent=0)
Command: `SET_TIME_REPORT_RATE` with `rateExponent=0`

Expected behavior:
- TM[1,3] (ACK start) received
- TM[1,7] (ACK completion) received
- Time packets now arrive every ~1 second (observable in packet viewer)
- `/PUS/time-rate` shows `0`

### Send TC[9,1] — invalid exponent
Command: `SET_TIME_REPORT_RATE` with `rateExponent=9`

Expected behavior:
- TM[1,4] (NACK start) received with failure code `INVALID_RATE_EXPONENT` (3)
- Rate unchanged; time packets continue at previous period

### Verify time correlation
1. Send `SET_TIME_REPORT_RATE` with `rateExponent=2` (4 s, default).
2. Wait for a few time packets.
3. In YAMCS UI → Time Correlation (`tco0`): check that the on-board time offset and drift are being updated as packets arrive.

---

## Design Decisions

| Decision | Rationale |
|---|---|
| Rate as exponent (not direct seconds) | Matches PUS spec §6.9.3c: valid rates are powers of 2 (1, 2, 4 … 256 s) |
| `sendImmediate` for time packets | Time packets fill CRC in constructor; `transmitRealtimeTM` would double-compute and corrupt CRC |
| APID=0 for time packets | Mandated by PUS spec §6.9.2.3a — reserved for time reporting subservice |
| TC MDB in separate `pus9.xml` | Keeps each service's definitions isolated, consistent with pus5.xml / pus11.xml / pus17.xml pattern |
| TM[9,2] decoded in `pus.xml` `pus-time` container | Container already existed and correctly matches APID=0 packets; no duplication needed |
| Default `rateExponent=2` (4 s) | Reasonable polling interval; matches the original hardcoded 4-second schedule that was replaced |