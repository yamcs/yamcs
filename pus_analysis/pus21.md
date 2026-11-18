# PUS ST[21] — Request sequencing

Reviewed against commit `90d4a3d1c` on 2026-11-18. The Yamcs ground example defines commands
and reports in [pus21.xml](../examples/pus/src/main/yamcs/mdb/pus21.xml). The Java
[Pus21Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus21Service.java) emulates the
on-board sequence store, file repository and execution. There is no ground-side
`Pus21RequestSequencingService` or `Pus21EmbeddedEntryBuilder` in this checkout.

## Message contract

Sequence IDs are fixed 16-byte strings. Each direct-load/content-report entry is a complete
raw TC packet followed by `delay:u32` milliseconds. The packet boundary is determined from
its own CCSDS length (`length + 7`); no additional `tc_len` prefix is present.

| TC | MetaCommand under `/PUS21/` | Response |
|---|---|---|
| 1 | `TC_21_1` | Direct load; `seq_id`, `num_entries`, `entries` |
| 2 | `TC_21_2_NO_PATH` / `TC_21_2_WITH_PATH` | Load by reference |
| 3 | `TC_21_3` | Unload inactive sequence |
| 4 | `TC_21_4` | Activate |
| 5 | `TC_21_5` | Abort |
| 6 | `TC_21_6` | TM[21,7] execution statuses |
| 8 | `TC_21_8_NO_PATH` / `TC_21_8_WITH_PATH` | Load by reference and activate |
| 9 | `TC_21_9` | TM[21,10] checksum |
| 11 | `TC_21_11` | TM[21,12] content |
| 13 | `TC_21_13` | TM[21,14] aborted sequence IDs |

TC[21,6] and TC[21,13] have no service data. ID-based operations use `seq_id`; inspect the
MDB for path length/binary arguments of the `_WITH_PATH` variants. Two variants implement
that example's optional-path choice; this is not a general claim that XTCE lacks variable
string encodings.

| Report container | Source-data layout |
|---|---|
| `TM_21_7` | Count:u8 + `{seq_id:16 bytes, exec_status:u8}` entries |
| `TM_21_10` | `seq_id:16 bytes`, `checksum_value:u16` |
| `TM_21_12` | `seq_id:16 bytes`, count:u8 + `{embedded TC, delay:u32}` entries |
| `TM_21_14` | Count:u8 + aborted 16-byte IDs |

Use the common [packet offsets](pus_simulator_architecture.md#packet-layouts); offsets 6/7
are not the service/subtype positions of this example's full PUS packets.

## Ground construction of embedded entries

`TC_21_1` accepts `entries = [{tc_packet: <bytes>, delay: 500}, ...]`. Its current
`tc_packet_arg_type` is **unannotated**, unlike ST[19]'s `/PUS/EmbeddedTcType`. Therefore the
link postprocessor does not automatically finalize the ST[21] embedded packet: the caller
must supply a correct length, sequence count and CRC. Building or dry-running a command
without link postprocessing is not sufficient evidence of wire-ready bytes.

Yamcs already has generic `Yamcs:EmbeddedTc` postprocessing, including nested aggregate-array
members, as documented in the [postprocessor manual](../docs/server-manual/links/command-postprocessor/pus.rst).
Applying it to ST[21] would require an explicit MDB change and verification, not a claim that
an absent builder class is available. This review changes documentation only.

TM[21,12] uses repeated containers, extracting the embedded primary header and a trailer sized
by its length. Dynamic references without `instance` now select the latest decoded occurrence,
so each entry uses its own length. Explicit `instance="0"` selects the first occurrence.
Aggregate-member array dimensions remain a separate representation; the sequence MDB does not
need that marker for this repeated-container binary-size lookup. Continue checking unequal
inner packet lengths when changing the sequence layout.

## Simulator state and execution

`sequenceStore` starts empty. `fileRepo` is an in-memory repository seeded with
`/seq/demo-seq-1.bin`, containing two valid TC[17,1] pings with 500 ms delay values. Both
TC[21,2] and TC[21,8] work against it. No ST[23] service populates arbitrary files.

Activation releases entry zero immediately. A cancellable `ScheduledFuture` chain releases
subsequent entries after each delay and eventually returns the sequence to inactive. Abort
cancels the pending step; it does not require a background thread to finish a sleep.
Each release calls `PusSimulator.processTc()` and generates the embedded TC's verification
telemetry. It does not independently issue a ground command or create a new ground history row.

| Completion rejection | Code |
|---|---|
| Already loaded | 3 |
| Not found | 4 |
| Not unloadable | 5 |
| Not activatable | 6 |
| Not abortable | 7 |
| File not found | 8 |
| File not recognized | 9 (reserved, not exercised by the well-formed seeded repository) |

The checksum is the simulator's CRC-16/CCITT over its entry representation; validate the
mission's checksum convention independently before relying on interoperability. State is
lost at restart. String helpers truncate/pad to 16 bytes. Java stores delay in a signed int
even though the MDB exposes uint32; values above `Integer.MAX_VALUE` need separate validation.

## Manual walkthrough

Start the [example](pus_native_arch.md#build-and-verification).

1. `TC_21_2_NO_PATH` with `{"seq_id": "demo-seq-1"}` loads the seeded sequence. Repeating it
   while loaded must reject with code 3.
2. Request status, content and checksum using `TC_21_6`, `TC_21_11`, and `TC_21_9`.
3. Activate with `TC_21_4`; observe two TM[17,2] reports approximately 500 ms apart and the
   later inactive status. Observe telemetry, rather than expecting new ground command rows.
4. Activate the still-loaded inactive sequence again and abort within the inter-entry delay.
   Do not reload it first: completed execution leaves it loaded, so reloading would fail.
5. Repeating abort while inactive must reject; unload with `TC_21_3` before testing another
   load. Use `TC_21_13` during execution to check abort-all and TM[21,14].
6. Exercise unknown sequence/file names and verify codes 4/8 separately.

Direct-load tests additionally need finalized embedded bytes and heterogeneous packet lengths;
the seeded-file path bypasses those ground construction cases.
