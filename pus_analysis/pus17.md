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

### End-to-end semantics

1. Ground operator sends TC[17,1]
2. Spacecraft test subservice receives and validates the TC
3. Spacecraft generates TM[17,2] (no payload, just the header)
4. Ground receives TM[17,2] — confirms both uplink and downlink are live

The reception of TM[17,2] on the ground confirms:
- Uplink path (TC) is operational
- The application process is alive and executing
- Downlink path (TM) is operational

---

## b) Per-subtype Implementation Plan

### TC[17,1] — Perform are-you-alive connection test

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

**Python simulator code:**

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

**Python simulator code:**

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

## Recommended Implementation Sequence

1. Add `pus17.xml` to `test_yamcs/src/main/yamcs/mdb/` (modelled on `pus20.xml`)
2. Register it in `yamcs.pus-test.yaml` MDB list
3. Write `pus17_simulator.py` (~50 lines): TC listener + TM builder
4. Smoke-test: send TC[17,1] from YAMCS UI → confirm TM[17,2] appears in parameter view

ST[17] makes an excellent **integration smoke test** for a new YAMCS + simulator setup — if TC[17,1]
round-trips to TM[17,2], the entire CCSDS framing, XTCE decoding, and UDP link stack is proven.

---

## Full pus17.xml (Reference Implementation)

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

## Full pus17_simulator.py (Reference Implementation)

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
