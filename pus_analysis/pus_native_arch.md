# PUS Native YAMCS-Core Architecture

Architecture learnings for implementing PUS services natively in yamcs-core
(i.e. YAMCS acts as the on-board service — no relay to a spacecraft required).

---

## 1. Full TC Pipeline

```
Ground (REST / WebSocket)
    → CommandingManager.buildCommand()           # MetaCommandProcessor encodes args → binary
    → CommandingManager.sendCommand()
        → cmdHistoryManager.addCommand(pc)       # publishes initial tuple to cmdhist_realtime
        → CommandQueueManager.addCommand()       # checks queue state + transmission constraints
    → CommandingManager.releaseCommand()
        → CommandVerificationHandler.start()     # starts XTCE verifiers if configured
        → commandReleaser.releaseCommand(pc)     # → StreamTcCommandReleaser (or native service)
    → StreamTcCommandReleaser.releaseCommand()
        → StreamWriter.releaseCommand(pc)        # matches tcStream + command name patterns
        → stream.emitTuple(pc.toTuple())         # writes to tc_realtime stream
    → LinkManager.TcStreamSubscriber.onTuple()
        → PreparedCommand.fromTuple(tuple, mdb)  # deserializes from stream
        → TcDataLink.sendCommand(pc)             # → queued in AbstractThreadedTcDataLink
    → AbstractTcDataLink.postprocess(pc)
        → CommandPostProcessor.process(pc)       # fills seq count, appends CRC
        → PusCommandPostprocessor.process()      # may wrap in TC[11,4] if pus11ScheduleAt set
    → TcpTcDataLink.uplinkCommand(pc)            # raw bytes → socket → spacecraft
```

**Key interception point**: `CommandReleaser.releaseCommand()`.
If a service implements `CommandReleaser`, it becomes the sole command releaser
for the processor. Everything else is transparent.

### How `CommandReleaser` is discovered at startup

In `Processor.init()`, the service list is scanned:
```java
for (ProcessorServiceWithConfig swc : serviceList) {
    if (swc.service instanceof CommandReleaser) {
        this.commandReleaser = (CommandReleaser) swc.service;
    }
}
// After discovery, commandHistoryPublisher is auto-created if missing:
commandReleaser.setCommandHistory(commandHistoryPublisher);
```

Only one `CommandReleaser` per processor is supported; the last one wins.

### TC stream tuple format

`StreamTcCommandReleaser` emits `pc.toTuple()` to the TC stream.
The base definition is `StandardTupleDefinitions.TC`:

| Column | Type | Notes |
|--------|------|-------|
| `gentime` | TIMESTAMP | generation time |
| `origin` | STRING | sender origin |
| `seqNum` | INT | sequence number |
| `cmdName` | ENUM | fully-qualified command name |
| + dynamic columns | various | binary, assignments, attributes |

`LinkManager.TcStreamSubscriber.onTuple()` reads the tuple back via
`PreparedCommand.fromTuple(tuple, mdb)` and passes it to each `TcDataLink`.

### Multiple TC links / stream routing

A TC stream can serve multiple `TcDataLink` instances (failover chain).
Each link's `sendCommand()` is tried in order; first `true` return wins.
Stream routing can be configured with `tcPatterns` (regex list on command name).

---

## 2. How to Implement a Native PUS Service

### Pattern: Extend `StreamTcCommandReleaser`

The cleanest approach is to subclass `StreamTcCommandReleaser` and override
`releaseCommand()`:

```java
public class PusXxService extends StreamTcCommandReleaser {

    @Override
    public synchronized void releaseCommand(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        if (binary != null && (binary[7] & 0xFF) == MY_SERVICE_TYPE) {
            handleLocally(pc);
        } else {
            super.releaseCommand(pc);  // forward to TC stream as normal
        }
    }
}
```

Non-intercepted commands flow to the TC stream normally via `super.releaseCommand()`.
Intercepted commands are handled in-process.

### How to identify a PUS TC from raw binary

```
binary[0..1] = CCSDS primary header word 0 (version + type + sh_flag + APID)
binary[2..3] = sequence flags + sequence count
binary[4..5] = packet data length
binary[6]    = PUS secondary header byte 0 (version + ack flags)
binary[7]    = service type
binary[8]    = service subtype
binary[9..10] = source ID
binary[11+]  = application data
```

`APP_DATA_OFFSET = 11`

Helper utilities in `PusPacket`:
```java
int apid    = PusPacket.getApid(binary);    // packet[0..1] & 0x7FF
int type    = PusPacket.getType(binary);    // packet[7] & 0xFF
int subtype = PusPacket.getSubtype(binary); // packet[8] & 0xFF
```

### TC relay during sequence execution

To release an embedded TC (raw bytes) back through the normal pipeline:

```java
CommandId cmdId = CommandId.newBuilder()
    .setCommandName("/pus21/relay")
    .setOrigin("pus21-sequencer")
    .setSequenceNumber(relaySeqCounter.getAndIncrement())
    .setGenerationTime(processor.getCurrentTime())
    .build();
PreparedCommand relayPc = new PreparedCommand(cmdId);
relayPc.setBinary(rawTc);
relayPc.setRaw(true);            // flags as raw (no arg assignment decoding)
super.releaseCommand(relayPc);   // goes to TC stream → TcStreamSubscriber → datalink
```

**Important**: `setRaw(true)` sets the `raw` attribute on the `PreparedCommand` but does **NOT**
skip postprocessing in the data link (`disablePostprocessing()` reads `CNAME_NO_POSTPROCESSING`,
a separate attribute). The `PusCommandPostprocessor` will still run on the relayed command.
If the embedded TC already has a CRC, configure the postprocessor to skip CRC or use
`disablePostprocessing(true)` instead.

**Limitation**: bypasses YAMCS `CommandingManager` (no command queue, no verifiers).
Appropriate for sequence-released commands that are "best effort fire-and-forget".

---

## 3. Full TM Pipeline

```
Socket / physical medium
    → TcpTmDataLink.run()                       # reads raw bytes from socket
    → PacketPreprocessor.process(TmPacket)
        → PusPacketPreprocessor.process()        # verifies CRC, decodes time,
                                                 # checks apid sequence
        → returns TmPacket with gentime set
    → LinkManager.processTmPacket()
        → emits Tuple on TM stream (e.g. tm_realtime):
          { gentime, seqNum, rectime, status, packet, ertime, obt, link, rootContainer }

TM stream → two parallel subscribers:

[A] StreamTmPacketProvider.onTuple()
    → tmProcessor.processPacket(tmPacket, rootContainer)
    → XtceTmProcessor → XtceTmExtractor.processContainer()
    → extracts ParameterValues per XTCE container definition
    → ParameterProcessorManager → distributed to real-time subscribers

[B] XtceTmRecorder.onTuple()
    → runs XtceTmExtractor to find the most-specific matching container
    → uses container qualified name as pname (archive partition key)
    → upserts tuple into tm table
```

### TM stream tuple format

`StandardTupleDefinitions.TM`:

| Column | Type | Notes |
|--------|------|-------|
| `gentime` | TIMESTAMP | packet generation time |
| `seqNum` | INT | CCSDS sequence count |
| `rectime` | TIMESTAMP | reception/recording time |
| `status` | INT | 0 = OK |
| `packet` | BINARY | full raw packet bytes |
| `ertime` | HRES_TIMESTAMP | earth reception time (optional) |
| `obt` | LONG | on-board time raw (optional) |
| `link` | ENUM | data link name |
| `rootContainer` | ENUM | XTCE root container override (optional) |

`XtceTmRecorder` strips `rootContainer` and adds `pname` before writing to the `tm` table.

---

## 4. Injecting TM Responses into YAMCS (Native Services)

To inject TM back from a native service (so YAMCS decodes it via MDB and parameters
appear in the archive), emit a Tuple directly on the realtime TM stream.

### Get the TM stream

```java
String yamcsInstance = proc.getInstance();
YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
for (StreamConfig.TmStreamConfigEntry entry : StreamConfig.getInstance(yamcsInstance).getTmEntries()) {
    if ("realtime".equals(entry.getProcessor())) {
        tmStream = ydb.getStream(entry.getName());
        break;
    }
}
```

### Emit a TM Tuple

```java
int seqCount = ((pkt[2] & 0x3F) << 8) | (pkt[3] & 0xFF);
TupleDefinition td = StandardTupleDefinitions.TM.copy();
Tuple t = new Tuple(td, new Object[]{
    now,      // gentime
    seqCount, // seqNum
    now,      // rectime
    0,        // status
    pkt       // raw packet bytes
});
tmStream.emitTuple(t);
```

**Archiving**: both `StreamTmPacketProvider` and `XtceTmRecorder` subscribe to the TM stream.
Injected packets ARE decoded by the processor (real-time parameter distribution) AND
archived to the `tm` table — as long as the raw bytes match an XTCE `SequenceContainer`.

**Note**: injecting via Tuple bypasses `PacketPreprocessor`. The gentime must be
set by the service. The raw packet bytes still go through MDB extraction for
parameter decoding.

### Building a PUS TM Packet

PUS TM packet structure:

```
[0..5]   CCSDS primary header (6 bytes)
[6]      PUS secondary header: 0x21 (version=2, scRefStatus=1)
[7]      service type
[8]      service subtype
[9..10]  message type counter (uint16, big-endian)
[11..12] destination ID (uint16, big-endian, 0 = ground)
[13+]    absolute time (CUC format, see timeEncoding config)
[13+T+]  application data
[-2..-1] CRC-16-CCIIT
```

Use `CucTimeEncoder` (same class as `PusCommandPostprocessor`) to encode time.
Apply `CrcCciitCalculator.compute(pkt, 0, totalLen - 2)` for the CRC.

CCSDS primary header word 0 for TM: `(0 << 13) | (0 << 12) | (1 << 11) | (apid & 0x7FF)`
- bit 13 = packet type: 0 = TM
- bit 12 = secondary header flag: 1 = present
- bits 0-10 = APID

---

## 5. Command History Integration

### How command history flows to the archive

```
CommandingManager.sendCommand()
    → cmdHistoryManager.addCommand(pc)
    → StreamCommandHistoryPublisher.addCommand(pc)
    → stream.emitTuple(pc.toTuple())        # → cmdhist_realtime stream

CommandHistoryPublisher.publishAck()
    → StreamCommandHistoryPublisher.publishAck()
    → stream.emitTuple(ack tuple)           # → cmdhist_realtime stream

CommandHistoryRecorder (archive service)
    → subscribes: "upsert_append into cmdhist select * from cmdhist_realtime"
    → cmdhist table: primary key (gentime, origin, seqNum)
```

The `cmdhist` table uses **upsert_append** (not upsert) so each arriving tuple
adds new columns without overwriting existing ones. Each ack publish produces a
separate tuple with only the new columns (`key_Status`, `key_Time`, etc.).

### Publishing from a native service

```java
// Ack that the TC was "sent" (received and accepted by the service)
commandHistory.publishAck(pc.getCommandId(), AcknowledgeSent_KEY,
    processor.getCurrentTime(), AckStatus.OK);

// Report completion (success or failure)
commandHistory.publishAck(pc.getCommandId(), CommandComplete_KEY,
    processor.getCurrentTime(), AckStatus.OK /* or NOK */, errorMsg /* nullable */);
```

Override `setCommandHistory(CommandHistoryPublisher chp)` to receive this reference.
`StreamTcCommandReleaser.setCommandHistory()` is a no-op by default — override it.

### Standard ack key constants (`CommandHistoryPublisher`)

| Constant | Key string | Meaning |
|----------|-----------|---------|
| `AcknowledgeQueued_KEY` | `Acknowledge_Queued` | command entered queue |
| `AcknowledgeReleased_KEY` | `Acknowledge_Released` | released from queue |
| `AcknowledgeSent_KEY` | `Acknowledge_Sent` | sent via link / accepted by native service |
| `CommandComplete_KEY` | `CommandComplete` | final outcome |
| `TransmissionConstraints_KEY` | `TransmissionConstraints` | constraint eval result |

Each `publishAck(cmdId, key, time, status)` writes three sub-columns:
`key_Status`, `key_Time`, and optionally `key_Message` and `key_Return`.

The `ccsds-seqcount` key is published by `PusCommandPostprocessor` and is read
by `Pus1Verifier` as `sentSeqCount`.

---

## 6. Processor Configuration

Replace `StreamTcCommandReleaser` with the native service in the processor config:

```yaml
# In processors.yaml (or in yamcs.<instance>.yaml under processorConfig)
- class: org.yamcs.pus.Pus21RequestSequencingService
  args:
    apid: 1           # APID for generated TM packets
    timeEncoding:     # must match the PusPacketPreprocessor time config
      implicitPfield: false
      pfield: 0x2f
```

The service registers itself as the processor's `CommandReleaser` because it
implements `CommandReleaser` (via `StreamTcCommandReleaser`). No other
`CommandReleaser` should be present for the same processor.

If `StreamTcCommandReleaser` was previously in the service list, remove it and
replace with this class. Non-targeted commands are forwarded to the TC stream
via `super.releaseCommand()` exactly as before.

---

## 7. Archive / Database Layer

### Tables created by YAMCS services

| Table | Service | Primary Key | Notes |
|-------|---------|-------------|-------|
| `tm` | `XtceTmRecorder` | `(gentime, seqNum)` | partitioned by `time_and_value(gentime, pname)` |
| `cmdhist` | `CommandHistoryRecorder` | `(gentime, origin, seqNum)` | upsert_append, partitioned by time |
| `pp_realtime` (parameter archive) | `ParameterArchive` | varies | parameter values over time |
| `events` | `EventRecorder` | `(gentime, source, seqNum)` | YAMCS events |
| `alarms_realtime` | `AlarmRecorder` | varies | parameter alarms |

### Stream types and their roles

| Stream type | Standard name | Role |
|-------------|--------------|------|
| `TM` | `tm_realtime` | TM packets: from link → processor → archive |
| `TC` | `tc_realtime` | TC uplink: from releaser → TC data link |
| `CMD_HIST` | `cmdhist_realtime` | Command history: from publisher → archive |
| `PARAM` | `pp_realtime`, `sys_param` | Parameter values to archive |
| `EVENT` | `events_realtime` | Events to archive |
| `INVALID_TM` | `invalid_tm_stream` | CRC-failed packets (optional) |

**Important distinction**: `TC` stream (`tc_realtime`) is for uplink binary; 
`CMD_HIST` stream (`cmdhist_realtime`) is for command history updates.
Both use `StandardTupleDefinitions.TC` as the base tuple format, but serve
different pipeline stages.

### Stream initialization

`StreamInitializer.createStreams()` runs at instance startup and creates all
streams declared in `streamConfig` using their standard tuple definitions.
Streams are created before services start, so services can safely look them up
in `init()`.

---

## 8. XTCE / MDB Notes for Native Services

MDB is still required for:
- Ground tool encoding of TC commands (argument types, fixed values, etc.)
- YAMCS TM decoding of injected TM packets (container hierarchy, parameters)
- Command verification (verifier definitions in `CommandVerifierSet`)
- Transmission constraints (`TransmissionConstraint` elements)

The native service handles the _on-board_ logic; the MDB handles the
_ground-side_ encoding/decoding contract.

For TM packets injected by the service, the raw bytes must match the XTCE
`SequenceContainer` definitions (correct service type / subtype in the right
byte positions, correct data types and sizes for all fields).

### TC encoding by MetaCommandProcessor

`CommandingManager.buildCommand()` calls `MetaCommandProcessor.buildCommand(mc, argAssignmentList, time)`.
This encodes the command binary according to XTCE `MetaCommand` container definitions.
The result is stored in `pc.binary`. `PusCommandPostprocessor` then fills in
the sequence count and CRC when the command reaches the TC data link.

---

## 9. PUS-Specific Classes (Reference)

### `PusPacket`

Utilities for parsing PUS packet headers:

```java
int apid    = PusPacket.getApid(packet);    // (packet[0..1]) & 0x7FF
int type    = PusPacket.getType(packet);    // packet[7] & 0xFF
int subtype = PusPacket.getSubtype(packet); // packet[8] & 0xFF
int TM_MIN_SIZE = 13;                        // minimum PUS TM packet size
```

### `PusPacketPreprocessor`

Extends `CcsdsPacketPreprocessor`. Runs on the TM link side:
- Verifies CRC-16-CCIIT if configured
- Reads gentime from `pktTimeOffset` (default 13, i.e. CUC time at byte 13)
- Handles time correlation packets (APID 0, no secondary header)
- Optional TCO (Time Correlation) integration via `tcoService`

Config keys: `pktTimeOffset`, `timePktTimeOffset`, `performTimeCorrelation`, `timeEncoding`, `errorDetection`.

### `PusCommandPostprocessor`

Runs on the TC data link side after `StreamTcCommandReleaser` writes to the TC stream:
- Fills CCSDS packet length field (`binary[4..5]`)
- Fills sequence count via `CcsdsSeqCountFiller.fill(binary)`
- Publishes `ccsds-seqcount` to command history (consumed by `Pus1Verifier`)
- Appends CRC-16 if `errorDetection` is configured
- If `pus11ScheduleAt` command option is set, wraps the TC inside TC[11,4]:
  - Publishes `pus11-apid`, `pus11-ccsds-seqcount`, `pus11-binary` to command history
  - Config: `pus11Apid` (default = same as inner command), `pus11Crc` (default true), `pus11SourceId`

The `OPTION_SCHEDULE_TIME` command option is registered **globally** at class
load time via `YamcsServer.getServer().addCommandOption(...)`. Ground users see
it on every command in the UI.

### `Pus1Verifier`

Custom `AbstractAlgorithmExecutor` used as a command verifier in XTCE.
Configured with `stage: N` (e.g., 3 = Start, 5 = Progress, 7 = Completion).

Required algorithm **inputs** in exact order:
1. `sentApid` — uint32, APID of the sent command
2. `sentSeqCount` — sint32, read from command history `ccsds-seqcount` (no raw value)
3. `rcvdApid` — uint32, APID from PUS1 packet
4. `rcvdSeqCount` — uint32, seq count from PUS1 packet
5. `reportSubType` — uint32, PUS1 sub-type
6. (optional) `reportServiceType` — uint32, PUS1 service type; if provided, filters out non-ST[01]

Logic:
- If `sentApid == rcvdApid && sentSeqCount == rcvdSeqCount`:
  - `subType == stage` → SUCCESS
  - `subType == stage+1` → FAILURE (constructs message from `template` string)
- Otherwise → NO_RESULT (not a match for this command)

### `PusEventDecoder`

Instance-level service (not processor-level) that subscribes to TM streams and
decodes PUS ST[05] event packets into YAMCS events. Reads event templates from
a JSON file (`eventTemplateFile`). Uses `XtceTmExtractor` for parameter extraction
from the event packet. Emits YAMCS `Event` protos on the `events_realtime` stream.

Config keys: `eventIdParameter`, `eventTemplateFile`.

### `SuperComTimestampProcessor`

Custom `AbstractAlgorithmExecutor` for super-commutation timestamp interpolation.
When a single packet carries N samples of the same sensor with inter-sample
interval Δt, this algorithm produces scalar derived parameters with timestamps
shifted by `i × interSampleDeltaMs` relative to the packet generation time.

Config: `interSampleDeltaMs` (ms between samples), `numSamples` (samples per sensor).
Inputs: `[TempSamples (ArrayValue), VoltageSamples (ArrayValue)]`
Outputs: `numSamples` temp outputs + `numSamples` voltage outputs, in that order.

### `Constants`

PUS time offset constants:
```java
DEFAULT_PKT_TIME_OFFSET = 13;        // byte offset of CUC time in normal TM
DEFAULT_TIME_PACKET_TIME_OFFSET = 7; // byte offset of CUC time in time packets
```

---

## 10. Command Verification (Post-Release)

After `commandReleaser.releaseCommand(pc)`, `CommandingManager.releaseCommand()` also
starts `CommandVerificationHandler` if the MetaCommand has verifiers.

```
CommandVerificationHandler.start()
    → subscribes to command history for this command's CommandId
    → creates a Verifier per CommandVerifier element in MetaCommand
    → each Verifier waits for success/failure condition or timeout

On match:
    → verifier.verificationResult = SUCCESS / FAILURE / TIMEOUT
    → CommandHistoryPublisher.publishAck(cmdId, "Verifier_" + stage, ...)
    → commandingManager.verificatonFinished(activeCommand)
```

Verifier types:
- `ContainerVerifier`: waits for a specific XTCE `SequenceContainer` to be received
- `AlgorithmVerifier`: runs an algorithm (e.g., `Pus1Verifier`) on each new TM packet/param

The `Pus1Verifier` approach: declare the PUS1 acknowledgement parameters as
algorithm inputs in the XTCE MDB; the algorithm runs each time those parameters
update and returns SUCCESS/FAILURE/NO_RESULT.

---

## 11. `Pus21RequestSequencingService` (Existing Implementation)

Intercepts TC[21,x] commands before the TC stream. Manages an in-memory sequence
store. Supported subtypes:

| Subtype | Name | Response TM |
|---------|------|-------------|
| 1 | Load directly | — (completion) |
| 2 | Load by reference | — (rejected: not implemented) |
| 3 | Unload | — |
| 4 | Activate | — |
| 5 | Abort | — |
| 6 | Report execution status | TM[21,7] |
| 8 | Load by reference + activate | — (rejected) |
| 9 | Checksum | TM[21,10] |
| 11 | Report sequence content | TM[21,12] |
| 13 | Abort all | TM[21,14] |

Sequence entry format in TC[21,1]:
```
seqId:  16 bytes (ASCII, null-padded)
N:       1 byte  (number of entries)
per entry:
  tc_len:  2 bytes (uint16)
  tc_bytes: tc_len bytes
  delay_ms: 4 bytes (int32)
```

Sequence execution: runs in a `ScheduledExecutorService` thread pool (2 threads).
Relays embedded TCs via `super.releaseCommand()` → TC stream → data link.

---

## 12. Limitations of the Native Approach

| Limitation | Explanation |
|------------|-------------|
| Load by reference (TC[21,2]/TC[21,8]) | Requires a filesystem / on-board storage service; not implemented. Ground must use TC[21,1] (direct load) instead. |
| Embedded TC command history | Relayed TCs use synthetic CommandIds (`/pus21/relay`). They appear in command history but without full argument decoding. |
| Relay TCs and postprocessor | `setRaw(true)` on relayed PCs does NOT skip `PusCommandPostprocessor`. If embedded TCs already have CRC/seqcount, use `disablePostprocessing(true)` or pre-strip them. |
| TM time accuracy | `processor.getCurrentTime()` is YAMCS mission time, which may differ slightly from on-board time used by the spacecraft. |
| No persistence | Sequence store is in-memory; sequences are lost on YAMCS restart. For persistence, use a `Table`-backed store. |
| No broadcast | If multiple YAMCS instances are running, sequence state is not shared. |
| TC stream vs cmdhist stream | Both use `StandardTupleDefinitions.TC` tuple format. TC uplink goes to `tc_realtime`; command history goes to `cmdhist_realtime`. Confusing naming but different purposes. |

---

## 13. Pattern Reuse for Other PUS Services

The same pattern applies to other PUS services that benefit from native YAMCS handling:

| Service | Native benefit |
|---------|---------------|
| ST[11] Time-based scheduling | YAMCS manages the schedule; avoids duplicating it on-board |
| ST[21] Request sequencing | YAMCS executes sequences; sequences visible in command history |
| ST[14] Packet forwarding control | YAMCS manages routing; no on-board routing table needed |
| ST[20] On-board parameter management | YAMCS parameter DB is the on-board store |

For any of these, the entry point is the same: extend `StreamTcCommandReleaser`,
intercept on `releaseCommand()`, manage state, relay via `super.releaseCommand()`,
inject TM via stream Tuple emission.

### Checklist for a new native PUS service

1. Extend `StreamTcCommandReleaser`, override `releaseCommand()` and `setCommandHistory()`
2. In `init()`: resolve the TM stream, configure encoders
3. For each handled TC subtype:
   - Call `publishAckSent(pc)` immediately
   - Process the command
   - Call `publishCompletion(pc, success, msg)` with result
4. For TM responses: build PUS TM packet, emit tuple on TM stream
5. In `processors.yaml`: replace `StreamTcCommandReleaser` with the new class
6. In the MDB: define TC commands and TM containers matching the packet layout
7. Optional: add command verifiers in MDB using `Pus1Verifier` for acknowledgement tracking
