# PUS Native YAMCS-Core Architecture

Architecture learnings for implementing PUS services natively in yamcs-core
(YAMCS acts as the on-board service — no relay to a spacecraft required).

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

## 14. Time Handling

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
