# PUS ST[17] Test Service — Analysis & Implementation Plan

**Spec reference**: ECSS-E-ST-70-41C §6.17 (requirements) and §8.17 (packet definitions)
**Required subtypes**: TC[17,1], TM[17,2]

---

## a) General Context

PUS ST[17] is the **Test Service** — the simplest standardized PUS service. Its sole purpose is to
verify end-to-end communication between ground and spacecraft. When the spacecraft receives
TC[17,1], it proves both the uplink and downlink are operational by immediately responding with
TM[17,2].

### Key characteristics

| Property | Value |
|----------|-------|
| PUS service type | 17 |
| Sub-service | Test subservice (are-you-alive) |
| TC[17,1] application data | **Omitted** — zero bytes |
| TM[17,2] source data | **Omitted** — zero bytes |
| Response ratio | 1 TC[17,1] → 1 TM[17,2] |
| State maintained | None |
| Background tasks | None |
| New pus_dt.xml types needed | None |

### Spec-defined message types (§6.17.3)

- **TC[17,1]** — "Perform an are-you-alive connection test" — ground-to-spacecraft
- **TM[17,2]** — "Are-you-alive connection test report" — spacecraft-to-ground

> The spec also defines TC[17,3] (on-board connection test between two on-board processes) and
> TM[17,4], but these are **not in scope** for this implementation.

### Ground vs. On-board Responsibility (MCS is ground segment only)

| Responsibility | Where |
|---|---|
| Construct and send TC[17,1] to the spacecraft | **Ground (YAMCS MCS)** — XTCE encodes the TC packet |
| Receive and display TM[17,2] | **Ground (YAMCS MCS)** — XTCE decodes the TM packet |
| Receive TC[17,1], validate it, and generate TM[17,2] | **On-board (satellite)** |
| Maintain end-to-end round-trip state | **On-board (satellite)** — ground only observes the result |

**YAMCS/MCS implementation = XTCE only (`pus17.xml`). No Java changes to `yamcs-core` are needed for ST[17].**

The `pus17_simulator.py` described in this document emulates the satellite's on-board behavior (receiving TC[17,1] and replying with TM[17,2]) for ground testing. It is not part of the MCS.

---

### End-to-end semantics

1. **[GROUND]** Ground operator sends TC[17,1] via YAMCS
2. **[GROUND → SAT]** TC[17,1] is transmitted over the uplink
3. **[ON-BOARD]** Spacecraft test subservice receives and validates the TC
4. **[ON-BOARD]** Spacecraft generates TM[17,2] (no payload, just the header)
5. **[SAT → GROUND]** TM[17,2] is transmitted over the downlink
6. **[GROUND]** YAMCS receives TM[17,2] — confirms both uplink and downlink are live

The reception of TM[17,2] on the ground confirms:
- Uplink path (TC) is operational
- The application process is alive and executing
- Downlink path (TM) is operational

---

## b) Per-subtype Implementation Plan

### TC[17,1] — Perform are-you-alive connection test

> **Layer**: **GROUND (YAMCS MCS)** — XTCE encodes this TC for uplink. The on-board software handles execution after receipt.

**PUS Spec §8.17.2.1:**
- Service type = 17, subtype = 1
- Application data field: **omitted** (no payload after the secondary header)
- Instruction contains no argument (§6.17.3b NOTE)

**Packet layout (simplified PUS, test_yamcs style):**

```
Byte  0-1 : CCSDS word 1 — version(3b)=0, type(1b)=1(TC), sec_hdr(1b)=1, apid(11b)
Byte  2-3 : CCSDS word 2 — seq_flags(2b)=3, seq_count(14b)
Byte  4-5 : CCSDS word 3 — packet_data_length = 1 (secondary header only, minus 1)
Byte  6   : service_type = 17 (0x11)
Byte  7   : service_subtype = 1 (0x01)
            [no further bytes]
```

Total packet size: **8 bytes**.

**XTCE encoding (pus17.xml):**

```xml
<MetaCommand name="TC_17_1" shortDescription="TC[17,1] Are-you-alive connection test">
    <!-- No ArgumentList — zero application data per spec §8.17.2.1b -->
    <CommandContainer name="TC_17_1">
        <EntryList>
            <FixedValueEntry name="ccsds-version"   binaryValue="00" sizeInBits="3" />
            <FixedValueEntry name="ccsds-type"      binaryValue="01" sizeInBits="1" />
            <FixedValueEntry name="ccsds-sec-hdr"   binaryValue="01" sizeInBits="1" />
            <FixedValueEntry name="ccsds-apid"      binaryValue="00AA" sizeInBits="11" />
            <FixedValueEntry name="ccsds-seq-flags" binaryValue="03" sizeInBits="2" />
            <FixedValueEntry name="ccsds-seq-count" binaryValue="0000" sizeInBits="14" />
            <FixedValueEntry name="ccsds-length"    binaryValue="0001" sizeInBits="16" />
            <FixedValueEntry name="service-type"    binaryValue="11" sizeInBits="8" />
            <FixedValueEntry name="service-subtype" binaryValue="01" sizeInBits="8" />
        </EntryList>
    </CommandContainer>
</MetaCommand>
```

Replace `00AA` with the chosen APID in 11-bit hex (e.g. APID=170 → `0xAA` → `"0AA"`).

**XTCE notes:**
- No `<ArgumentList>` element needed — the command has no parameters
- `ccsds-length` = total_bytes_after_length_field − 1 = (8 − 6) − 1 = 1 → `0x0001`
- This is the **zero-argument MetaCommand** pattern — the simplest possible TC in XTCE

**Simulator (on-board emulation):**

The following code emulates the satellite-side behavior of receiving TC[17,1] and replying with TM[17,2]. This runs in `pus17_simulator.py` — it is not part of YAMCS/MCS.

```python
def handle_tc(data: bytes, addr, tm_sock: socket.socket) -> None:
    if len(data) < 8:
        return
    svc_type    = data[6]
    svc_subtype = data[7]
    if svc_type == 17 and svc_subtype == 1:
        log.info("TC[17,1] from %s — sending TM[17,2]", addr)
        tm_sock.sendto(build_tm_17_2(), (TM_HOST, TM_PORT))
    else:
        log.warning("Unknown TC type=%d subtype=%d — ignored", svc_type, svc_subtype)
```

No payload parsing beyond the secondary header bytes is needed.

---

### TM[17,2] — Are-you-alive connection test report

> **Layer**: **GROUND (YAMCS MCS)** — XTCE decodes this TM packet received from the spacecraft. The on-board software is responsible for generating it.

**PUS Spec §8.17.2.2:**
- Service type = 17, subtype = 2
- Source data field: **omitted** (no payload after the secondary header)

**Packet layout (simplified PUS, test_yamcs style):**

```
Byte  0-1 : CCSDS word 1 — version(3b)=0, type(1b)=0(TM), sec_hdr(1b)=1, apid(11b)
Byte  2-3 : CCSDS word 2 — seq_flags(2b)=3, seq_count(14b)
Byte  4-5 : CCSDS word 3 — packet_data_length = 1 (secondary header only, minus 1)
Byte  6   : service_type = 17 (0x11)
Byte  7   : service_subtype = 2 (0x02)
            [no further bytes]
```

Total packet size: **8 bytes**.

**XTCE encoding (pus17.xml):**

```xml
<!-- Base container for all ST[17] TM — extracts APID, seq_count, service fields -->
<SequenceContainer name="PUS17Packet">
    <EntryList>
        <ParameterRefEntry parameterRef="pus_apid">
            <LocationInContainerInBits referenceLocation="containerStart">
                <FixedValue>5</FixedValue>
            </LocationInContainerInBits>
        </ParameterRefEntry>
        <ParameterRefEntry parameterRef="pus_seqcount">
            <LocationInContainerInBits referenceLocation="containerStart">
                <FixedValue>18</FixedValue>
            </LocationInContainerInBits>
        </ParameterRefEntry>
        <ParameterRefEntry parameterRef="pus_pktlen">
            <LocationInContainerInBits referenceLocation="containerStart">
                <FixedValue>32</FixedValue>
            </LocationInContainerInBits>
        </ParameterRefEntry>
        <ParameterRefEntry parameterRef="service_type">
            <LocationInContainerInBits referenceLocation="containerStart">
                <FixedValue>48</FixedValue>
            </LocationInContainerInBits>
        </ParameterRefEntry>
        <ParameterRefEntry parameterRef="service_subtype" />
    </EntryList>
</SequenceContainer>

<!-- TM[17,2] — no user data; restriction by APID + service/subtype -->
<SequenceContainer name="TM_17_2"
    shortDescription="TM[17,2] Are-you-alive connection test report">
    <EntryList/>   <!-- zero source data per spec §8.17.2.2b -->
    <BaseContainer containerRef="PUS17Packet">
        <RestrictionCriteria>
            <ComparisonList>
                <Comparison parameterRef="pus_apid"         value="170" />
                <Comparison parameterRef="service_type"     value="17" />
                <Comparison parameterRef="service_subtype"  value="2" />
            </ComparisonList>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**XTCE notes:**
- `<EntryList/>` (self-closing or empty) — the **zero-payload TM** pattern
- YAMCS will decode this packet and expose `pus_apid`, `pus_seqcount`, `service_type`,
  `service_subtype` as telemetry parameters; no additional parameters are defined
- This is the simplest possible SequenceContainer in XTCE

**Simulator (on-board emulation):**

The following code emulates the satellite-side behavior of building and sending TM[17,2] in response to TC[17,1]. This runs in `pus17_simulator.py` — it is not part of YAMCS/MCS.

```python
_seq_count = 0
_seq_lock  = threading.Lock()

def _next_seq() -> int:
    global _seq_count
    with _seq_lock:
        s = _seq_count
        _seq_count = (_seq_count + 1) & 0x3FFF
    return s

def build_tm_17_2() -> bytes:
    secondary    = struct.pack(">BB", 17, 2)      # service_type=17, service_subtype=2
    pkt_data_len = len(secondary) - 1             # = 1
    word1 = (1 << 11) | APID                      # TM type=0, sec_hdr=1, APID
    word2 = (0b11 << 14) | _next_seq()
    header = struct.pack(">HHH", word1, word2, pkt_data_len)
    return header + secondary                     # 8 bytes total
```

---

## c) Gaps / Shortcomings

| # | Gap | Severity | Fix |
|---|-----|----------|-----|
| 1 | **None blocking** — fully XTCE-expressible | — | — |
| 2 | APID assignment | Minor | Choose APID ≠ 200 (ST[20]); recommend APID=170 |
| 3 | No PUS-1 ACK/NACK in test_yamcs | Minor | Inherent simplified-PUS limitation; not ST[17]-specific |
| 4 | Subtypes 3/4 out of scope | Info | TC[17,3] adds `uint16 target_apid` arg; TM[17,4] echoes it |

**Gap detail for subtypes 3/4 (if ever needed):**
- TC[17,3]: XTCE adds one `<Argument argumentTypeRef="/dt/uint16" name="target_apid"/>`;
  CommandContainer adds one `<ArgumentRefEntry argumentRef="target_apid"/>`
- TM[17,4]: SequenceContainer adds one `<ParameterRefEntry parameterRef="app_process_id"/>`;
  new Parameter of type `uint16` declared in ParameterSet

---

## Overall Verdict

**MCS scope (YAMCS ground)**: XTCE only — `pus17.xml` is sufficient. No Java changes to `yamcs-core` are needed for ST[17].

**Simulator scope (on-board emulation)**: `pus17_simulator.py` emulates the satellite side for ground testing. It is not part of the MCS.

### Two-layer artifact table

| Artifact | Layer | Purpose |
|---|---|---|
| `pus17.xml` | **MCS / YAMCS ground** | XTCE definition: encodes TC[17,1] for uplink; decodes TM[17,2] from downlink |
| `pus17_simulator.py` | **Simulator (on-board emulation)** | Receives TC[17,1], validates it, generates and sends TM[17,2] back to YAMCS |

> **Key finding**: All on-board logic (receiving the TC, validating it, building and sending TM[17,2]) is purely a satellite responsibility. YAMCS/MCS only defines the packet shapes in XTCE — it does not participate in on-board execution. The simulator emulates this satellite behavior for test purposes only.

---

## Recommended Implementation Sequence

1. **[GROUND]** Add `pus17.xml` to `test_yamcs/src/main/yamcs/mdb/` (modelled on `pus20.xml`)
2. **[GROUND]** Register it in `yamcs.pus-test.yaml` MDB list
3. **[Simulator / on-board emulation]** Write `pus17_simulator.py` (~50 lines): TC listener + TM builder
4. **[GROUND]** Smoke-test: send TC[17,1] from YAMCS UI → confirm TM[17,2] appears in parameter view

ST[17] makes an excellent **integration smoke test** for a new YAMCS + simulator setup — if TC[17,1]
round-trips to TM[17,2], the entire CCSDS framing, XTCE decoding, and UDP link stack is proven.

---

## Full pus17.xml (Reference Implementation — MCS / YAMCS ground layer)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
    PUS ST[17] Test Service

    Packet format (simplified PUS, no full PUS secondary header):

    TC[17,1] Are-you-alive connection test:
      [0-5]  CCSDS primary header (type=TC, sec_hdr=1, APID=170)
      [6]    service_type = 17
      [7]    service_subtype = 1
             [no application data — spec §8.17.2.1b]

    TM[17,2] Are-you-alive connection test report:
      [0-1]  CCSDS word 1: version(3b)=0, type(1b)=0, sec_hdr(1b)=1, apid(11b)=170
      [2-3]  CCSDS word 2: seq_flags(2b)=3, seq_count(14b)
      [4-5]  CCSDS word 3: packet_data_length = 1
      [6]    service_type = 17
      [7]    service_subtype = 2
             [no source data — spec §8.17.2.2b]
-->
<SpaceSystem name="PUS17" xmlns="http://www.omg.org/spec/XTCE/20180204"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.omg.org/spec/XTCE/20180204
        https://www.omg.org/spec/XTCE/20180204/SpaceSystem.xsd">
    <Header validationStatus="Unknown" version="1.0" date="2025-01-01T00:00:00Z" />
    <TelemetryMetaData>
        <!-- No ParameterTypeSet needed — no user-data parameters -->
        <ParameterSet>
            <Parameter parameterTypeRef="/dt/uint11" name="pus_apid" />
            <Parameter parameterTypeRef="/dt/uint14" name="pus_seqcount" />
            <Parameter parameterTypeRef="/dt/uint16" name="pus_pktlen" />
            <Parameter parameterTypeRef="/dt/uint8"  name="service_type" />
            <Parameter parameterTypeRef="/dt/uint8"  name="service_subtype" />
        </ParameterSet>
        <ContainerSet>
            <SequenceContainer name="PUS17Packet">
                <EntryList>
                    <ParameterRefEntry parameterRef="pus_apid">
                        <LocationInContainerInBits referenceLocation="containerStart">
                            <FixedValue>5</FixedValue>
                        </LocationInContainerInBits>
                    </ParameterRefEntry>
                    <ParameterRefEntry parameterRef="pus_seqcount">
                        <LocationInContainerInBits referenceLocation="containerStart">
                            <FixedValue>18</FixedValue>
                        </LocationInContainerInBits>
                    </ParameterRefEntry>
                    <ParameterRefEntry parameterRef="pus_pktlen">
                        <LocationInContainerInBits referenceLocation="containerStart">
                            <FixedValue>32</FixedValue>
                        </LocationInContainerInBits>
                    </ParameterRefEntry>
                    <ParameterRefEntry parameterRef="service_type">
                        <LocationInContainerInBits referenceLocation="containerStart">
                            <FixedValue>48</FixedValue>
                        </LocationInContainerInBits>
                    </ParameterRefEntry>
                    <ParameterRefEntry parameterRef="service_subtype" />
                </EntryList>
            </SequenceContainer>
            <SequenceContainer name="TM_17_2"
                shortDescription="TM[17,2] Are-you-alive connection test report">
                <EntryList/>
                <BaseContainer containerRef="PUS17Packet">
                    <RestrictionCriteria>
                        <ComparisonList>
                            <Comparison parameterRef="pus_apid"        value="170" />
                            <Comparison parameterRef="service_type"    value="17" />
                            <Comparison parameterRef="service_subtype" value="2" />
                        </ComparisonList>
                    </RestrictionCriteria>
                </BaseContainer>
            </SequenceContainer>
        </ContainerSet>
    </TelemetryMetaData>
    <CommandMetaData>
        <!-- No ArgumentTypeSet needed — TC[17,1] has no application data -->
        <MetaCommandSet>
            <MetaCommand name="TC_17_1"
                shortDescription="TC[17,1] Are-you-alive connection test">
                <CommandContainer name="TC_17_1">
                    <EntryList>
                        <FixedValueEntry name="ccsds-version"   binaryValue="00"   sizeInBits="3" />
                        <FixedValueEntry name="ccsds-type"      binaryValue="01"   sizeInBits="1" />
                        <FixedValueEntry name="ccsds-sec-hdr"   binaryValue="01"   sizeInBits="1" />
                        <FixedValueEntry name="ccsds-apid"      binaryValue="0AA"  sizeInBits="11" />
                        <FixedValueEntry name="ccsds-seq-flags" binaryValue="03"   sizeInBits="2" />
                        <FixedValueEntry name="ccsds-seq-count" binaryValue="0000" sizeInBits="14" />
                        <FixedValueEntry name="ccsds-length"    binaryValue="0001" sizeInBits="16" />
                        <FixedValueEntry name="service-type"    binaryValue="11"   sizeInBits="8" />
                        <FixedValueEntry name="service-subtype" binaryValue="01"   sizeInBits="8" />
                    </EntryList>
                </CommandContainer>
            </MetaCommand>
        </MetaCommandSet>
    </CommandMetaData>
</SpaceSystem>
```

---

## Full pus17_simulator.py (Reference Implementation — Simulator / on-board emulation layer)

```python
#!/usr/bin/env python3
"""
PUS ST[17] Test Service Simulator

Implements:
  TC[17,1] - Are-you-alive connection test (receives from YAMCS)
  TM[17,2] - Are-you-alive connection test report (sends to YAMCS)

Packet format (simplified PUS, no full secondary header):
  Both TC[17,1] and TM[17,2] are exactly 8 bytes:
    [0-5]  CCSDS primary header
    [6]    service_type  = 17
    [7]    service_subtype = 1 (TC) or 2 (TM)
    [no application/source data]
"""

import logging
import signal
import socket
import struct
import sys
import threading
import time

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)-8s %(message)s")
log = logging.getLogger("pus17")

TM_HOST = "127.0.0.1"
TM_PORT = 10035   # YAMCS UdpTmDataLink port
TC_HOST = "0.0.0.0"
TC_PORT = 10047   # YAMCS UdpTcDataLink sends here

APID = 170        # 0xAA — distinct from ST[20]'s APID=200

_seq_lock  = threading.Lock()
_seq_count = 0


def _next_seq() -> int:
    global _seq_count
    with _seq_lock:
        s = _seq_count
        _seq_count = (_seq_count + 1) & 0x3FFF
    return s


def build_tm_17_2() -> bytes:
    secondary    = struct.pack(">BB", 17, 2)
    pkt_data_len = len(secondary) - 1            # = 1
    word1 = (1 << 11) | APID                     # TM type=0, sec_hdr=1
    word2 = (0b11 << 14) | _next_seq()
    return struct.pack(">HHH", word1, word2, pkt_data_len) + secondary


def tc_listener(tm_sock: socket.socket) -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((TC_HOST, TC_PORT))
    sock.settimeout(1.0)
    log.info("TC listener bound to %s:%d", TC_HOST, TC_PORT)
    while True:
        try:
            data, addr = sock.recvfrom(256)
            if len(data) < 8:
                continue
            svc_type, svc_sub = data[6], data[7]
            if svc_type == 17 and svc_sub == 1:
                log.info("TC[17,1] from %s — sending TM[17,2]", addr)
                tm_sock.sendto(build_tm_17_2(), (TM_HOST, TM_PORT))
            else:
                log.warning("Unknown TC type=%d subtype=%d — ignored", svc_type, svc_sub)
        except socket.timeout:
            continue
        except Exception as exc:
            log.error("TC listener error: %s", exc)


def main() -> None:
    log.info("=== PUS ST[17] Simulator ===")
    log.info("APID=%d  TM→%s:%d  TC←%s:%d", APID, TM_HOST, TM_PORT, TC_HOST, TC_PORT)
    tm_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    threading.Thread(target=tc_listener, args=(tm_sock,), daemon=True).start()

    def _shutdown(sig, frame):
        log.info("Shutting down"); sys.exit(0)

    signal.signal(signal.SIGINT, _shutdown)
    signal.signal(signal.SIGTERM, _shutdown)
    while True:
        time.sleep(1)


if __name__ == "__main__":
    main()
```

---

## Testing Methodology — Actual Implementation

The `pus17_simulator.py` reference code above assumed a standalone "test_yamcs" style simulator
(8-byte packet, no PUS secondary header). **That is not what was built.** The actual implementation
reuses the same Java `PusSimulator`/`AbstractPusService` framework as every other PUS service in
this repo (ST[05], ST[13], ST[15], ST[19], ...):

| Artifact | Path |
|---|---|
| Simulator service | `simulator/src/main/java/org/yamcs/simulator/pus/Pus17Service.java` |
| MDB | `examples/pus/src/main/yamcs/mdb/pus17.xml` |
| Registration | `PusSimulator.java` (`pus17Service` field/constructor/dispatch, `case 17 -> pus17Service.executeTc(...)`) |

Key differences from the reference pseudocode above:

- **TC header is 11 bytes, not 8**: `PusTcPacket.DATA_OFFSET = 11` (6B CCSDS + 5B PUS secondary
  header), so the raw byte offsets in section b) do not apply to the real wire format.
- **Command name is `ARE_YOU_ALIVE`, not `TC_17_1`**: `pus17.xml` defines an abstract `pus17-tc`
  base command (fixes `apid`/`type`) and a concrete `ARE_YOU_ALIVE` MetaCommand (fixes
  `subtype=1`), reached from the UI/API as `/PUS17/ARE_YOU_ALIVE`.
- **Built-in execution verifier**: `ARE_YOU_ALIVE` carries a `VerifierSet`/`ExecutionVerifier`
  bound to the `are-you-alive-report` container (TM[17,2]) with a 15s check window — YAMCS marks
  the command "completed" on its own once TM[17,2] arrives, in addition to the PUS-1 ACK/NACK
  containers.
- **Invalid subtype is rejected, not ignored**: `Pus17Service.executeTc` sends NACK start with
  `START_ERR_INVALID_PUS_SUBTYPE` for any subtype other than `1` (the doc's simulator pseudocode
  silently drops unknown packets instead).
- **Single application process**: every TC/TM uses `PusSimulator.MAIN_APID = 1`; there is no
  second process to exercise the APID field's real purpose.

### Start the instance

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests   # first build only
mvn -pl examples/pus yamcs:run
```
Web UI: `http://localhost:8090`, instance `pus`. Command lives under `/PUS17/ARE_YOU_ALIVE`.

### Command reference — valid input

| Command | Subtype | Valid example args |
|---|---|---|
| `/PUS17/ARE_YOU_ALIVE` | TC[17,1] | `{}` — no arguments |

Any other subtype value cannot be reached through this MetaCommand (only `subtype=1` is wired up),
so there is no in-UI way to exercise the NACK-start rejection path — that would require crafting a
raw packet (e.g. via a Python client) with a bad subtype byte.

### TMs to check

| Container | Subtype | Triggered by | Layout |
|---|---|---|---|
| `/PUS17/are-you-alive-report` | TM[17,2] | `ARE_YOU_ALIVE` | Empty — PUS-1 header fields only |

Also watch the standard PUS-1 verification containers (`/PUS/pus-tc-ack-*`) for ACK start and ACK
completion, and the command's own "completed" status in the Commanding view once the built-in
`ExecutionVerifier` observes TM[17,2].

### Suggested manual test walkthrough

1. **Send the command**: from the YAMCS web UI, Commanding → issue `/PUS17/ARE_YOU_ALIVE` with no
   arguments.
2. **Confirm ACK start**: `/PUS/pus-tc-ack-start` shows success for the command almost immediately.
3. **Confirm TM[17,2] arrives**: `/PUS17/are-you-alive-report` shows a new packet within a second —
   no payload, just the PUS header fields.
4. **Confirm ACK completion + verifier**: `/PUS/pus-tc-ack-completed` shows success, and the command
   row in the Commanding view flips to "completed" (driven by the `ExecutionVerifier`, not just the
   PUS-1 completion ACK).
5. **Repeat under load** (optional): issue `ARE_YOU_ALIVE` several times back-to-back and confirm
   each gets its own independent ACK/TM/verifier triple with no cross-talk — useful as the baseline
   smoke test before debugging any other PUS service in this simulator.

### Caveats specific to this simulator

- **No round-trip timing/latency simulated**: `executeTc` sends ACK start, TM[17,2], and ACK
  completion synchronously in one call — there is no artificial delay, so this cannot be used to
  test ground-side timeout/retry behavior.
- **No way to trigger the NACK-start path from the UI**: see "Command reference" above; only a raw
  packet with a corrupted subtype byte would exercise `Pus17Service`'s rejection branch.
