# PUS Native YAMCS-Core Architecture

Architecture learnings for implementing PUS services natively in yamcs-core
(YAMCS acts as the on-board service — no relay to a spacecraft required).

---

## Contents

| § | Section | Line |
|---|---------|------|
| 0 | Deployment Context | 50 |
| — | · System topology | 52 |
| — | · Configuration files | 67 |
| — | · Storage | 75 |
| 1 | TC Pipeline | 83 |
| — | · How CommandReleaser is discovered | 104 |
| — | · TC stream tuple (StandardTupleDefinitions.TC) | 112 |
| 2 | Implementing a Native PUS Service | 124 |
| — | · Pattern: PusCommandReleaser + PusTcHandler | 126 |
| — | · How to identify a PUS TC from raw binary | 189 |
| — | · TC relay (for ST[21] sequence execution) | 199 |
| 3 | TM Pipeline | 221 |
| — | · TM stream tuple (StandardTupleDefinitions.TM) | 235 |
| 4 | Injecting TM from a Native Service | 253 |
| — | · PUS TM packet structure | 272 |
| 5 | Command History Integration | 291 |
| — | · Standard ack key constants | 303 |
| 6 | Processor Configuration | 315 |
| 7 | State Persistence (MementoDb) | 352 |
| 8 | Archive / Database Layer | 376 |
| — | · Tables | 378 |
| — | · Stream types | 387 |
| 9 | XTCE / MDB Notes | 398 |
| 10 | Command Verification | 416 |
| — | · Pus1Verifier | 424 |
| 11 | PUS-Specific Classes | 440 |
| 12 | Limitations | 491 |
| 13 | Checklist: Adding a New PUS Service | 504 |
| 14 | Testing a Native PUS Service | 528 |
| — | · Setup | 532 |
| — | · What to verify for any new service | 544 |
| — | · Automated test pattern | 580 |
| — | · Debugging checklist | 627 |
| 15 | Time Handling | 640 |
| — | · Time Correlation Service (TCO) | 650 |

---

## 0. Deployment Context

### System topology

```
[Ground operators / Mission Control]
        |  REST / WebSocket
        ↓
[YAMCS Server]
        |  TCP socket (TcpTcDataLink / TcpTmDataLink)
        ↓
[Ground Station / Simulator]
```

### THIS APPLICATION YAMCS IS GOING TO BE GROUNDSTATION ONLY.
### SIMULATOR CODE/Refernces are for testing purposes

### Configuration files

| File | Scope | Purpose |
|------|-------|---------|
| `etc/yamcs.yaml` | Global | HTTP port, instances list, data directory |
| `etc/yamcs.<instance>.yaml` | Per-instance | Links, streams, MDB loaders, instance services |
| `etc/processor.yaml` | Per-instance | Processor type definitions and service lists |

### Storage

Archive backend is **RocksDB** via the **Yarch** layer. Two archive types:
- **Stream Archive**: time-sorted tuples — `tm`, `cmdhist`, `events`, `alarms_*`
- **Parameter Archive**: column-oriented segments (~70-min), optimal for long-range parameter retrieval

---

## 1. TC Pipeline

```
Ground (REST / WebSocket)
    → CommandingManager.buildCommand()           # MetaCommandProcessor encodes args → binary
    → CommandingManager.sendCommand()
        → cmdHistoryManager.addCommand(pc)
        → CommandQueueManager.addCommand()
    → CommandingManager.releaseCommand()
        → CommandVerificationHandler.start()
        → commandReleaser.releaseCommand(pc)     # → PusCommandReleaser (or StreamTcCommandReleaser)
    → PusCommandReleaser.releaseCommand()
        → dispatches by service type → PusTcHandler.handleTc(pc)
        → OR super.releaseCommand(pc)            # non-PUS → TC stream
    → StreamTcCommandReleaser.releaseCommand()
        → stream.emitTuple(pc.toTuple())         # writes to tc_realtime stream
    → LinkManager → TcDataLink → socket → spacecraft
```

**Key interception point**: `CommandReleaser.releaseCommand()`. Only one `CommandReleaser` per processor is supported.

### How CommandReleaser is discovered

In `Processor.init()`, the service list is scanned for `CommandReleaser` instances:
```java
commandReleaser.setCommandHistory(commandHistoryPublisher);
```
Override `setCommandHistory()` to capture this reference (the default is a no-op).

### TC stream tuple format (`StandardTupleDefinitions.TC`)

| Column | Type | Notes |
|--------|------|-------|
| `gentime` | TIMESTAMP | generation time |
| `origin` | STRING | sender origin |
| `seqNum` | INT | sequence number |
| `cmdName` | ENUM | fully-qualified command name |
| + dynamic columns | various | binary, assignments, attributes |

---

## 2. Implementing a Native PUS Service

### Pattern: `PusCommandReleaser` + `PusTcHandler`

All PUS services are registered with a single `PusCommandReleaser` dispatcher.
Individual services extend `PusTcHandler` — they handle TC logic only; all TM building
and command history infrastructure is centralised in the dispatcher.

**Class hierarchy:**
```
StreamTcCommandReleaser
  └── PusCommandReleaser          ← single CommandReleaser in processor.yaml
        owns: apid, tmStream, timeEncoder, crcCalculator, seqCounter, commandHistory
        dispatches by PUS service type → Map<Integer, PusTcHandler>

PusTcHandler (abstract)
  ├── Pus5Service                 ← handles ST[05]
  ├── Pus21RequestSequencingService  ← handles ST[21]
  └── (any future PUS handler)
```

**To add a new PUS service**, extend `PusTcHandler`:

```java
public class PusXxService extends PusTcHandler {

    static final int SERVICE_TYPE = XX;

    @Override
    protected void doInit(YConfiguration config) {
        // read handler-specific config
    }

    @Override
    public void handleTc(PreparedCommand pc) {
        byte[] bin = pc.getBinary();
        if (bin.length < APP_DATA_OFFSET) return;
        publishAckSent(pc);
        switch (PusPacket.getSubtype(bin)) {
            case 1 -> handleSubtype1(pc);
            default -> publishCompletion(pc, false, "unknown subtype");
        }
    }

    // Override for lifecycle needs:
    @Override protected void doStart() { /* start threads */ }
    @Override protected void doStop()  { /* stop threads  */ }
}
```

**Available inherited helpers** (delegate to `PusCommandReleaser`):

| Method | Purpose |
|--------|---------|
| `emitTm(serviceType, subtype, appData)` | Build + inject PUS TM packet on tm_realtime |
| `publishAckSent(pc)` | Publish `Acknowledge_Sent OK` to command history |
| `publishCompletion(pc, success, msg)` | Publish `CommandComplete OK/NOK` to command history |
| `getCurrentTime()` | Current YAMCS mission time (ms) |
| `APP_DATA_OFFSET = 11` | Byte offset of PUS TC app data |
| `log` | Logger (initialised with handler class name) |

**`doStart` / `doStop` lifecycle gotcha**: `PusCommandReleaser.doStart()` calls `notifyStarted()` itself after calling each handler's `doStart()`. Do **not** call `super.doStart()` from `PusCommandReleaser` — `StreamTcCommandReleaser.doStart()` would call `notifyStarted()` a second time. Handler `doStart()`/`doStop()` are plain callbacks (not Guava service hooks), so no `notifyStarted()` is needed in them.

**`commandHistory` access**: Use `processor.getCommandHistoryPublisher()` directly in `PusCommandReleaser` helper methods. Do not rely on `setCommandHistory()` — `StreamTcCommandReleaser` overrides it as a no-op and restoring it requires care.

### How to identify a PUS TC from raw binary

```
binary[6]    = PUS secondary header byte 0 (version + ack flags)
binary[7]    = service type        ← PusPacket.getType(binary)
binary[8]    = service subtype     ← PusPacket.getSubtype(binary)
binary[9..10] = source ID
binary[11+]  = application data    ← APP_DATA_OFFSET = 11
```

### TC relay (for ST[21] sequence execution)

To release an embedded TC back to the stream, bypassing the handler dispatch:

```java
CommandId cmdId = CommandId.newBuilder()
    .setCommandName("/pus21/relay")
    .setOrigin("pus21-sequencer")
    .setSequenceNumber(relaySeqCounter.getAndIncrement())
    .setGenerationTime(getCurrentTime())
    .build();
PreparedCommand relayPc = new PreparedCommand(cmdId);
relayPc.setBinary(rawTc);
relayPc.setRaw(true);
releaser.relayTc(relayPc);   // bypasses all handlers → TC stream → data link
```

**Note**: `setRaw(true)` does NOT skip `PusCommandPostprocessor`. If embedded TCs
already have CRC/seqcount, use `disablePostprocessing(true)` instead.

---

## 3. TM Pipeline

```
Socket → TcpTmDataLink
    → PusPacketPreprocessor.process()    # CRC verify, time decode, seqcount check
    → LinkManager.processTmPacket()
    → emits Tuple on tm_realtime:
      { gentime, seqNum, rectime, status, packet, ... }

tm_realtime → two parallel subscribers:
[A] StreamTmPacketProvider → XtceTmExtractor → ParameterProcessorManager (realtime params)
[B] XtceTmRecorder → archives to tm table
```

### TM stream tuple (`StandardTupleDefinitions.TM`)

| Column | Type | Notes |
|--------|------|-------|
| `gentime` | TIMESTAMP | packet generation time |
| `seqNum` | INT | CCSDS sequence count |
| `rectime` | TIMESTAMP | reception time |
| `status` | INT | 0 = OK |
| `packet` | BINARY | full raw packet bytes |
| `ertime` | HRES_TIMESTAMP | earth reception time (nullable) |
| `obt` | LONG | on-board time raw (nullable) |
| `link` | ENUM | data link name (nullable) |
| `rootContainer` | ENUM | XTCE root container FQN (nullable) |

⚠️ `Tuple(TupleDefinition, Object[])` validates that column count == definition size and throws `IllegalArgumentException` if they differ. Always supply all 9 values (last 4 can be `null`).

---

## 4. Injecting TM from a Native Service

Emit a tuple directly on the TM stream — the packet goes through both the
processor (real-time parameter distribution) and the archive.

```java
// PusCommandReleaser.emitTm() handles this; handlers call emitTm(serviceType, subtype, appData)
// Internally:
byte[] pkt = buildPusTmPacket(serviceType, subtype, appData);
long now = processor.getCurrentTime();
int seqNum = ((pkt[2] & 0x3F) << 8) | (pkt[3] & 0xFF);
// Must supply all 9 TM columns; last 4 are nullable
Tuple t = new Tuple(StandardTupleDefinitions.TM,
    new Object[]{ now, seqNum, now, 0, pkt, null, null, null, null });
tmStream.emitTuple(t);
```

**Bypasses** `PacketPreprocessor` — gentime is set by the service, not decoded from bytes.

### PUS TM packet structure

```
[0..5]   CCSDS primary header
           word0 = (1 << 11) | (apid & 0x7FF)   // TM, secondary header present
           word1 = (3 << 14) | seqCount           // unsegmented, monotonic counter
           word2 = totalLen - 7                   // packet data length
[6]      0x21  (PUS version=2, scRefStatus=1)
[7]      service type
[8]      service subtype
[9..10]  message type counter (uint16, 0)
[11..12] destination ID (uint16, 0)
[13..T]  CUC time  (CucTimeEncoder)
[T+1..]  application data
[-2..-1] CRC-16-CCIIT
```

---

## 5. Command History Integration

```java
// Ack that the TC was received/accepted by the native service
commandHistory.publishAck(pc.getCommandId(), AcknowledgeSent_KEY,
    getCurrentTime(), AckStatus.OK);

// Report completion
commandHistory.publishAck(pc.getCommandId(), CommandComplete_KEY,
    getCurrentTime(), AckStatus.OK /* or NOK */, errorMsg /* nullable */);
```

### Standard ack key constants

| Constant | Key string | Meaning |
|----------|-----------|---------|
| `AcknowledgeSent_KEY` | `Acknowledge_Sent` | accepted by native service / sent via link |
| `CommandComplete_KEY` | `CommandComplete` | final outcome |

Each `publishAck` writes `key_Status`, `key_Time`, and optionally `key_Message`.
The `cmdhist` table uses `upsert_append` — each publish adds new columns without overwriting.

---

## 6. Processor Configuration

All PUS handlers are registered under a single `PusCommandReleaser` entry in `processor.yaml`.
`apid` and `timeEncoding` are shared across all handlers.

```yaml
realtime:
  services:
    - class: org.yamcs.StreamTmPacketProvider
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
              events:
                - {id: 1, initiallyEnabled: true}
                - {id: 2, initiallyEnabled: true}
          - serviceType: 21
            class: org.yamcs.pus.Pus21RequestSequencingService
    - class: org.yamcs.tctm.StreamParameterProvider
    - class: org.yamcs.algorithms.AlgorithmManager
    - class: org.yamcs.parameter.LocalParameterManager
```

Non-PUS TCs (or PUS service types with no registered handler) fall through to
`StreamTcCommandReleaser.releaseCommand()` → TC stream → data link.

`PusEventDecoder` (ST[05] TM decoder) remains an **instance-level** service in
`yamcs.<instance>.yaml` — not processor-level.

---

## 7. State Persistence (MementoDb)

Use `MementoDb` for persisting service state across restarts. It is a key-value store
backed by a yarch table (RocksDB), using Gson for serialisation. Available in every
YAMCS instance with no setup.

```java
MementoDb db = MementoDb.getInstance(releaser.getYamcsInstance());

// Read
db.getJsonObject("myService.state").ifPresent(obj -> { /* restore */ });

// Write (synchronous yarch upsert — durable)
JsonObject obj = new JsonObject();
obj.addProperty("key", value);
db.putJsonObject("myService.state", obj);
```

**`Pus5Service` uses this** to persist the enabled/disabled event registry:
- On init: load config defaults, then overlay MementoDb (persisted TC changes win)
- On TC[5,5/6]: update in-memory map, immediately write to MementoDb

---

## 8. Archive / Database Layer

### Tables

| Table | Service | Primary Key |
|-------|---------|-------------|
| `tm` | `XtceTmRecorder` | `(gentime, seqNum)` |
| `cmdhist` | `CommandHistoryRecorder` | `(gentime, origin, seqNum)` |
| `events` | `EventRecorder` | `(gentime, source, seqNum)` |
| `memento` | `MementoDb` | `key` (STRING) |

### Stream types

| Stream type | Standard name | Role |
|-------------|--------------|------|
| `TM` | `tm_realtime` | TM packets: link → processor → archive |
| `TC` | `tc_realtime` | TC uplink: releaser → data link |
| `CMD_HIST` | `cmdhist_realtime` | Command history: publisher → archive |
| `EVENT` | `events_realtime` | Events to archive |

---

## 9. XTCE / MDB Notes

MDB is required for:
- Ground tool encoding of TC commands (argument types, fixed values)
- TM parameter extraction from injected packets (container hierarchy)
- Command verification (`CommandVerifierSet`)

The native service handles on-board logic; MDB handles the ground-side encoding/decoding contract.

For injected TM: raw bytes must match XTCE `SequenceContainer` definitions
(correct service type/subtype positions, correct field sizes).

`CommandingManager.buildCommand()` calls `MetaCommandProcessor.buildCommand()` to encode
TC binary from XTCE definitions. `PusCommandPostprocessor` fills sequence count and CRC
when the command reaches the data link.

---

## 10. Command Verification

After `releaseCommand()`, YAMCS starts `CommandVerificationHandler` if the MetaCommand has verifiers.

Verifier types:
- `ContainerVerifier`: waits for a specific XTCE `SequenceContainer` to be received
- `AlgorithmVerifier`: runs an algorithm (e.g., `Pus1Verifier`) on each TM update

### `Pus1Verifier`

Custom `AbstractAlgorithmExecutor`. Config: `stage: N` (3=Start, 5=Progress, 7=Completion).

Required algorithm inputs (in order):
1. `sentApid` — uint32
2. `sentSeqCount` — sint32 (from command history `ccsds-seqcount`)
3. `rcvdApid` — uint32
4. `rcvdSeqCount` — uint32
5. `reportSubType` — uint32
6. `reportServiceType` — uint32 (optional; filters non-ST[01] packets to prevent false failures)

Logic: if APIDs and seq counts match, `subType == stage` → SUCCESS, `subType == stage+1` → FAILURE.

---

## 11. PUS-Specific Classes

### `PusCommandReleaser`
Single `CommandReleaser` entry point. Owns TM packet building infrastructure (CUC time,
CRC-16, sequence counter, TM stream). Routes by PUS service type to registered `PusTcHandler` instances.

### `PusTcHandler`
Abstract base for individual PUS service handlers. Provides `emitTm`, `publishAckSent`,
`publishCompletion`, `getCurrentTime`, `APP_DATA_OFFSET`. Lifecycle: `doInit`, `doStart`, `doStop`.
CRC computation is owned by `PusCommandReleaser`, not exposed to handlers.

### `Pus5Service` (ST[05])
Handles TC[5,5/6/7], emits TM[5,1-4,8]. Event enabled/disabled state persisted in MementoDb.
`raiseEvent(eventId, subtype, auxData)` — called by other services to emit event TM reports.

### `Pus21RequestSequencingService` (ST[21])
Manages in-memory sequence store. Supported subtypes:

| Subtype | Name | Response |
|---------|------|----------|
| 1 | Load directly | — |
| 3 | Unload | — |
| 4 | Activate | — |
| 5 | Abort | — |
| 6 | Report status | TM[21,7] |
| 9 | Checksum | TM[21,10] |
| 11 | Report content | TM[21,12] |
| 13 | Abort all | TM[21,14] |

Runs in a 2-thread `ScheduledExecutorService`. Relays embedded TCs via `releaser.relayTc()`.

### `PusPacket`
```java
int apid    = PusPacket.getApid(binary);    // binary[0..1] & 0x7FF
int type    = PusPacket.getType(binary);    // binary[7] & 0xFF
int subtype = PusPacket.getSubtype(binary); // binary[8] & 0xFF
```

### `PusPacketPreprocessor`
Runs on TM link side: CRC-16-CCIIT verify, gentime decode, sequence count check, TCO integration.

### `PusCommandPostprocessor`
Runs on TC data link side: fills packet length, sequence count, appends CRC, optionally wraps in TC[11,4].

### `PusEventDecoder`
Instance-level service. Subscribes to TM streams, decodes ST[05] TM[5,1-4] packets into
YAMCS events using `XtceTmExtractor` + event template JSON file.
Config: `eventIdParameter`, `eventTemplateFile`.

---

## 12. Limitations

| Limitation | Detail |
|------------|--------|
| ST[21] load by reference | TC[21,2/8] not implemented — use TC[21,1] direct load |
| Relay TC and postprocessor | `setRaw(true)` does NOT skip `PusCommandPostprocessor`; use `disablePostprocessing(true)` if embedded TCs already have CRC |
| Relay TC command history | Synthetic `CommandId` (`/pus21/relay`) — no argument decoding |
| TM time | `getCurrentTime()` is YAMCS mission time; may differ slightly from on-board clock |
| ST[21] sequence persistence | Sequence store is in-memory; lost on restart |
| Multi-instance state | MementoDb is local to one YAMCS instance; enabled/disabled state not shared |

---

## 13. Checklist: Adding a New PUS Service

1. Create a class extending `PusTcHandler`
2. Override `doInit(YConfiguration config)` — read handler-specific config
   - If the handler maintains an ID registry (e.g. event IDs), **derive it from the MDB** rather than a hand-maintained config list:
     ```java
     var mdb = MdbFactory.getInstance(releaser.getYamcsInstance());
     var ept = (EnumeratedParameterType) mdb.getParameter(fqn).getParameterType();
     for (var ve : ept.getValueEnumerationList()) { registry.put((int) ve.getValue(), defaultState); }
     ```
   - Overlay persisted state from `MementoDb` after seeding defaults
3. Implement `handleTc(PreparedCommand pc)`:
   - Check `bin.length < APP_DATA_OFFSET` and return early if malformed
   - Call `publishAckSent(pc)`
   - Switch on `PusPacket.getSubtype(bin)`, delegate to private methods
   - Call `publishCompletion(pc, success, msg)` in each branch
4. For TM responses: call `emitTm(SERVICE_TYPE, subtype, appData)` with app-data bytes
5. If the handler needs threads: override `doStart()` / `doStop()` (no `notifyStarted()` needed here)
6. Register in `processor.yaml` under `handlers:` with its `serviceType`
7. In the MDB: define TC commands and TM containers matching the packet layout
8. Optional: add `Pus1Verifier` entries in the MDB MetaCommand `CommandVerifierSet`

---

## 14. Testing a Native PUS Service

The examples/pus project is the canonical integration harness. It bundles YAMCS + the PUS simulator into a single `mvn yamcs:run` command, providing a full TC→handler→TM→XTCE decode loop without any external infrastructure.

### Setup

```bash
# one-time build (skip if yamcs-core artifacts are already installed)
mvn install -DskipTests -pl simulator,yamcs-core,examples/pus

cd examples/pus
mvn yamcs:run          # starts YAMCS + embedded PUS simulator on localhost:8090
```

Wait for: `[SimulatorCommander] PUS simulator started`

### What to verify for any new service

#### 1. TC path — command history

Issue a TC via the web UI (*Telecommanding*) or Python client. Navigate to *Commanding → Command History* and confirm:

| Stage | Entry | Expected |
|-------|-------|----------|
| Accepted | `Acknowledge_Sent` | `OK` — handler received the TC |
| Completed | `CommandComplete` | `OK` or `NOK` with error message |

A missing `CommandComplete` means `publishCompletion()` was never called in one of the switch branches.

#### 2. TM path — parameters and containers

After the handler calls `emitTm(...)`, go to *Monitoring → Parameters* and filter by the service's XTCE namespace. Verify:
- Parameter values decoded correctly (check against raw packet bytes if suspicious)
- Array parameters (`disabled_event_ids` style) show correct element count
- No container mismatch errors in the YAMCS log

For event-flavored TM (ST[05] style): check *Monitoring → Events* for the formatted message. Missing entries mean `PusEventDecoder` didn't match the event ID or the template is absent from `events.json`.

#### 3. Simulator-side TC handling

The `simulator/.../pus/Pus<XX>Service.java` mirrors the ground handler. Confirm:
- Simulator logs no `invalid subtype` or `invalid event id` lines
- Simulator sends the expected TM response (check via TM stream subscription or raw container values)

#### 4. State persistence (MementoDb)

If the service persists state:
1. Apply a TC that changes state (e.g. disable an event).
2. Stop YAMCS (`Ctrl-C`), restart with `mvn yamcs:run`.
3. Query the state immediately (e.g. TC[5,7]) — the persisted change must survive the restart.
4. Confirm the init path loaded MementoDb *after* seeding defaults (defaults must not overwrite persisted state).

### Automated test pattern

Place tests in `examples/pus/tests/test-pus<XX>.py`. The standard structure:

```python
from yamcs.client import YamcsClient

INSTANCE  = "pus"
PROCESSOR = "realtime"
HOST      = "localhost:8090"

def run_tests():
    client   = YamcsClient(HOST)
    proc     = client.get_processor(INSTANCE, PROCESSOR)
    cmd_conn = proc.create_command_connection()

    # Optional: subscribe to a container for TM response validation
    tm_queue = queue.Queue()
    sub = proc.create_container_subscription(
        "/PUS<XX>/pus<xx>-response",
        on_data=lambda c: tm_queue.put(c.binary),
    )

    def issue(cmd_path, args=None, timeout=5.0):
        cmd = cmd_conn.issue(cmd_path, args=args or {})
        cmd.await_acknowledgment("Acknowledge_Sent", timeout=timeout)
        return cmd.await_acknowledgment("CommandComplete", timeout=timeout)

    # --- tests ---
    # assert compl.status == "OK"
    # assert tm_queue.get(timeout=5).hex() == expected_hex

    sub.cancel()

if __name__ == "__main__":
    sys.exit(0 if run_tests() else 1)
```

Run with: `python3 tests/test-pus<XX>.py` (YAMCS must be running).

Tests should cover at minimum:
- **Happy path**: nominal TC → expected TM response, correct CommandComplete
- **Bulk/array args**: multiple IDs in one TC (N > 1)
- **Idempotency**: repeat the same TC — state must not double-count
- **Unknown ID / invalid subtype**: TC with bad payload → `NOK` CommandComplete, no crash
- **State survives query**: apply change → query state → confirm change is reflected in TM response

### Debugging checklist

| Symptom | Where to look |
|---------|---------------|
| `CommandComplete` never appears | Missing `publishCompletion()` call in a code branch |
| TM container parameters all zero | XTCE field offsets or sizes don't match `emitTm()` byte layout |
| Event not in Events stream | `PusEventDecoder` event ID mismatch or missing template in `events.json` |
| State lost after restart | MementoDb write omitted, or defaults seed overwrites loaded state |
| `Pus1Verifier` reports failure on unrelated TC | Service type guard missing — add `if (reportServiceType != 1) return NO_RESULT` |
| Array container crashes on empty list | Missing `IncludeCondition` on the array `ParameterRefEntry` when count == 0 |

---

## 15. Time Handling

YAMCS timestamps: **signed 64-bit ms since 1970-01-01T00:00:00 TAI** (including leap seconds).

| Timestamp | Source | Usage |
|-----------|--------|-------|
| **Generation time** | Decoded from packet by preprocessor | Primary archive key |
| **Reception time** | Set when packet enters YAMCS | When data arrived |
| **Earth Reception Time** | High-res from ground station | Time correlation only |

### Time Correlation Service (TCO)

Computes `ground_time = m × obt + c` from `(obt, ert)` sample pairs.
`PusPacketPreprocessor` integrates via `tcoService` config key.
Coefficients invalid until `numSamples` are collected; reset on deviation > `validity`.
