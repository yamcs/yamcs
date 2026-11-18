# PUS ST[09] — Time management

Reviewed against commit `90d4a3d1c` on 2026-11-18. ST[09] is implemented by the Yamcs example
MDB and the Java simulator. Yamcs sends TC[9,1] over the TC link and receives spacecraft time
reports; it does not run an on-board time-report generator in `yamcs-core`.

## Current artifacts

| Source | Role |
|---|---|
| [pus9.xml](../examples/pus/src/main/yamcs/mdb/pus9.xml) | `SET_TIME_REPORT_RATE` command |
| [pus.xml](../examples/pus/src/main/yamcs/mdb/pus.xml) | APID-zero `pus-time` container |
| [Pus9Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus9Service.java) | Rate validation and periodic time reports |
| [PusTmTimePacket](../simulator/src/main/java/org/yamcs/simulator/pus/PusTmTimePacket.java) | Time-packet construction |
| [PusPacketPreprocessor](../yamcs-core/src/main/java/org/yamcs/pus/PusPacketPreprocessor.java) | Ground time extraction and correlation |

There is no `org.yamcs.pus.Pus9TimeManagementService` in this checkout. Keep
`StreamTcCommandReleaser` in the example processor configuration.

## Messages and default layout

TC[9,1], `/PUS9/SET_TIME_REPORT_RATE`, contains one `rateExponent:u8`. The simulator accepts
0–8 and schedules a report every `2^rateExponent` seconds, initially exponent 2 (4 seconds).
An invalid exponent produces NACK start with `START_ERR_INVALID_RATE_EXPONENT = 4` and leaves
the current rate unchanged. Successful changes produce ST[01] start/completion reports.

Time TM[9,2] has APID 0 and **no secondary header**: there are no service/subtype bytes in it.
The normal example has this 17-byte packet:

| Bytes | Field |
|---|---|
| 0–5 | CCSDS primary header, TM, APID 0, secondary-header flag 0 |
| 6 | Rate exponent |
| 7 | Explicit CUC P-field `0x2F` |
| 8–11 | Coarse time, 4 bytes |
| 12–14 | Fine time, 3 bytes |
| 15–16 | CRC |

Length is `6 + 1 + encodedTimeLength + 2`; the CCSDS length field is total length minus 7.
Time width is configurable via `PusTimeEncoding`; changing it also requires a matching ground
MDB/preprocessor. The default simulator clock is a drifting elapsed counter, not UTC.

`Pus9Service.sendTimePacket()` uses `tmLink.sendImmediate` and/or queues the packet on frame
VC0. It bypasses `transmitRealtimeTM()`, so ST[14] forwarding and ST[15] storage hooks on that
method do not apply to these time reports. Ground correlation must be checked through the
actual configured reception path, not by injecting tuples directly.

## Manual checks

Start the [PUS example](pus_native_arch.md#build-and-verification). Observe `/PUS/time-rate`,
`/PUS/obt-coarse`, and `/PUS/obt-fine`; send `SET_TIME_REPORT_RATE` with `rateExponent=0` and
check an approximately one-second report cadence. Send exponent 9 and check the failed-start
report and unchanged cadence. A decoded APID-zero time packet is not itself proof that
`/PUS/pus-time` is emitted: that container declares the component fields above, whereas the
ordinary `pus-tm` container declares the decoded `pus-time` parameter.

## Limitations

The simulator schedules reports with its scheduled executor, rather than deriving their
cadence from a transfer-frame counter. Rate changes are in memory and reset at restart.
Only the CUC time report path described here is implemented; do not infer CDS or TM[9,3]
support from the general PUS service scope. Packet timing, epoch, transfer-frame association,
and optional time-reference fields must be checked against a mission ICD.
