# PUS ST[13] — Large packet transfer

Reviewed against commit `90d4a3d1c` on 2026-11-18. The Yamcs ground example encodes uplink
fragments and decodes downlink parts with [pus13.xml](../examples/pus/src/main/yamcs/mdb/pus13.xml).
The Java [Pus13Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus13Service.java)
emulates fragmentation, reassembly, reception timers and execution of reconstructed TCs.

## Message and application-data layouts

All fields below are after the full PUS header: TC application data begins at byte 11;
ordinary TM source data begins at byte 21 with default time encoding. Each packet also has
its outer CRC. These offsets are not part of ST[13]'s service data.

| Message | Application/source data in this example |
|---|---|
| TM[13,1], TM[13,2] | `transaction_id:u16`, `part_seq_num:u16`, `part_data:64 bytes` |
| TM[13,3] | `transaction_id:u16`, `part_seq_num:u16`, `part_len_last:u8`, `part_data_last:part_len_last bytes` |
| TC[13,9], TC[13,10] | `transaction_id:u16`, `part_seq_num:u16`, `part_data:64 bytes` |
| TC[13,11] | `transaction_id:u16`, `part_seq_num:u16`, `part_len:u8`, `part_data_last:part_len bytes` |
| TM[13,16] | `transaction_id:u16`, `failure_reason:u8` |

Non-last parts are fixed at 64 bytes. Last-part length is explicitly encoded and the binary
size is `length × 8`; the command length argument permits 1–64. Omitting `SizeInBits` is not
used to infer a last TM part's size. The original claim that Yamcs automatically consumes
remaining packet bytes for this case was unsupported.

**Wire-format deviation:** the extra last-part length byte is present in both the shipped
MDB and simulator. The original service analysis describes the standard last-part size as
deduced from the surrounding packet. An explicit extra byte must therefore be treated as an
example-specific deviation requiring ICD agreement, not as universally conformant framing.
No wire-format change is made by this documentation review.

## Simulator behavior

Every 20 seconds the service creates a synthetic 150–300-byte payload and emits a downlink
sequence with 200 ms between parts. The transaction ID advances and part numbering starts at
1. Ground does not trigger this periodic downlink with a separate ST[13] TC.

Only one uplink transaction is tracked. A new first part aborts the old transaction if one
exists. Intermediate/last parts validate transaction ID and the expected next part number;
timeout is 5 seconds. On completion the reassembled bytes are dispatched as a PUS TC through
`PusSimulator.processTc()`, which generates the embedded command's own verification reports.

| Failure reason | Value |
|---|---|
| Reception timeout | 1 |
| Sequence discontinuity | 2 |
| Internal error | 3 |

The TC completion errors are separate: no active uplink (5), sequence discontinuity (6), and
reconstruction failed (7). A valid final part gets completion acknowledgement; successful
reassembly has no additional ST[13] success-report subtype.

## Ground responsibility and checks

The operator/client must build and split a valid large command into the explicit fragment
commands. The example does not automatically fragment arbitrary commands or reassemble
received TM into a new ground packet. XTCE decoding of individual parts does not provide
those orchestration features.

Start the [example](pus_native_arch.md#build-and-verification). Check periodic TM[13,1/2/3],
transaction/part ordering and the last-part length. For uplink use full first/intermediate
64-byte parts and a valid final part. Exercise a missing continuation, a sequence gap, and a
new transaction replacing an active one; inspect TM[13,16] and the ST[01] reports separately.
Large-packet sizing, the added length byte, timeout policy and one-transaction limit remain
mission-specific constraints.
