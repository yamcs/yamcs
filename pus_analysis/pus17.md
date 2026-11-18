# PUS ST[17] — Connection tests

Reviewed against commit `90d4a3d1c` on 2026-11-18.

| Artifact | Source |
|---|---|
| Ground command/report definitions | [pus17.xml](../examples/pus/src/main/yamcs/mdb/pus17.xml) |
| Spacecraft emulation | [Pus17Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus17Service.java) |
| Packed APID width | [dt.xml](../examples/pus/src/main/yamcs/mdb/dt.xml), [PusPackedFields](../simulator/src/main/java/org/yamcs/simulator/pus/PusPackedFields.java) |

## Messages

| Command | Response | Service data |
|---|---|---|
| `/PUS17/ARE_YOU_ALIVE` (TC[17,1]) | `/PUS17/are-you-alive-report` (TM[17,2]) | Both empty |
| `/PUS17/ON_BOARD_CONNECTION_TEST` (TC[17,3]) | `/PUS17/on-board-connection-test-report` (TM[17,4]) | `target_apid:u11` in each |

Empty service data does not mean an empty packet. The default CRC-enabled TC[17,1] is
13 bytes: 6-byte CCSDS primary + 5-byte PUS secondary + 2-byte CRC. The default TM[17,2] is
23 bytes: 6-byte primary + 7 pre-time secondary bytes + 8-byte CUC + 2-byte CRC. Service type
and subtype are at bytes 7 and 8, not bytes 6 and 7.

Both commands use destination APID 1 in the example MDB. The target of TC[17,3] is a separate
11-bit service-data field accepting 0–2047. It starts at byte 11 of the TC; the final octet's
unused five bits are zero. TM[17,4] echoes the same packed value. The simulator does not
instantiate a second application process for this test.

## Verification and manual checks

Start the [PUS example](pus_native_arch.md#build-and-verification), then issue
`ARE_YOU_ALIVE` with no arguments. The simulator synchronously emits ST[01] start acknowledgement,
TM[17,2], and ST[01] completion acknowledgement. The MDB additionally defines an execution
container verifier for TM[17,2] with a 15-second window.

That container verifier has no request ID to correlate: concurrent identical pings can share
the same matching report. Do not interpret it as proof that each command received a distinct
response. ST[01] request identifiers provide separate command correlation.

Issue `ON_BOARD_CONNECTION_TEST` with `{"target_apid": 256}` and then
`{"target_apid": 2047}`. Confirm TM[17,4] preserves the full target value. An unknown subtype
receives NACK start; this requires a raw packet because the MDB exposes only subtypes 1 and 3.

The normal example uses TCP packet links. This smoke test exercises its configured transport,
TC postprocessing and TM decoding; it is not a test of every transfer-frame link or a measured
latency/retry model. No `yamcs-core` on-board handler is needed.
