# PUS Native YAMCS-Core Architecture

Architecture learnings for implementing PUS services natively in yamcs-core
(i.e. YAMCS acts as the on-board service — no relay to a spacecraft required).

---

## 1. YAMCS Commanding Pipeline

```
Ground (REST / WebSocket)
    → CommandingManager.sendCommand()
    → CommandQueueManager.addCommand()
    → CommandQueue (HOLD / BYPASS)
    → CommandingManager.releaseCommand()
    → CommandReleaser.releaseCommand(PreparedCommand)
    → StreamTcCommandReleaser → TC stream → TcDataLink → spacecraft
```

**Key interception point**: `CommandReleaser.releaseCommand()`.
If a service implements `CommandReleaser`, it becomes the sole command releaser
for the processor. Everything else is transparent.

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
relayPc.setRaw(true);            // skip post-processing
super.releaseCommand(relayPc);   // goes to TC stream → datalink
```

**Limitation**: bypasses YAMCS `CommandingManager` (no command queue, no verifiers).
Appropriate for sequence-released commands that are "best effort fire-and-forget".

---

## 3. Injecting TM Responses into YAMCS

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

The TM stream expects tuples with these columns (`StandardTupleDefinitions.TM`):
- `gentime` (TIMESTAMP / long)
- `seqNum` (INT / int) — CCSDS sequence count
- `rectime` (TIMESTAMP / long)
- `status` (INT / int) — 0 = OK
- `packet` (BINARY / byte[]) — full raw packet bytes

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

---

## 4. Command History Integration

The native service should publish to command history to keep audit trails:

```java
// Ack that the TC was "sent" (received and accepted by the service)
commandHistory.publishAck(pc.getCommandId(), AcknowledgeSent_KEY,
    processor.getCurrentTime(), AckStatus.OK);

// Report completion (success or failure)
commandHistory.publishAck(pc.getCommandId(), CommandComplete_KEY,
    processor.getCurrentTime(), AckStatus.OK /* or NOK */, errorMsg /* nullable */);
```

Override `setCommandHistory(CommandHistoryPublisher chp)` to receive this reference.
`StreamTcCommandReleaser.setCommandHistory()` is a no-op by default.

---

## 5. Processor Configuration

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
replace with this class. Non-ST[21] commands are forwarded to the TC stream
via `super.releaseCommand()` exactly as before.

---

## 6. XTCE / MDB Notes for Native Services

MDB is still required for:
- Ground tool encoding of TC commands (argument types, fixed values, etc.)
- YAMCS TM decoding of injected TM packets (container hierarchy, parameters)

The native service handles the _on-board_ logic; the MDB handles the
_ground-side_ encoding/decoding contract.

For TM packets injected by the service, the raw bytes must match the XTCE
`SequenceContainer` definitions (correct service type / subtype in the right
byte positions, correct data types and sizes for all fields).

---

## 7. Limitations of the Native Approach

| Limitation | Explanation |
|------------|-------------|
| Load by reference (TC[21,2]/TC[21,8]) | Requires a filesystem / on-board storage service; not implemented in the YAMCS native service. Ground must use TC[21,1] (direct load) instead. |
| Embedded TC command history | Relayed TCs use synthetic CommandIds (`/pus21/relay`). They appear in command history but without full argument decoding. |
| TM time accuracy | `processor.getCurrentTime()` is YAMCS mission time, which may differ slightly from on-board time used by the spacecraft. |
| No persistence | Sequence store is in-memory; sequences are lost on YAMCS restart. For persistence, use a `Table`-backed store. |
| No broadcast | If multiple YAMCS instances are running, sequence state is not shared. |

---

## 8. Pattern Reuse for Other PUS Services

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
