# PUS ST[20] — On-board parameter management

Reviewed against commit `90d4a3d1c` on 2026-11-18. Ground support is declarative in
[pus20.xml](../examples/pus/src/main/yamcs/mdb/pus20.xml). The Java
[Pus20Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus20Service.java) owns the
simulated on-board parameter store. No on-board handler runs in `yamcs-core`.

## Scope and wire contract

| Message | Command/container | Service data |
|---|---|---|
| TC[20,1] | `/PUS20/REPORT_PARAMETER_VALUES` | `num_params:u8`, N × `param_id:u16` |
| TM[20,2] | `/PUS20/TM_20_2` | `num_params:u8`, N × `{param_id:u16, param_value:u32}` |
| TC[20,3] | `/PUS20/SET_PARAMETER_VALUES` | `num_params:u8`, N × `{param_id:u16, param_value:u32}` |

Counts must agree with their arrays. Service data follows a full PUS header: TC byte 11,
ordinary TM byte 21 for the default 8-byte time encoding. The example has outer CRCs.
ST[20,4–7] memory parameter definition management is outside its scope.

All values are unsigned 32-bit integers by example convention. The general PUS notion of a
value whose representation is deduced from the parameter ID is not implemented as arbitrary
runtime type selection. A mission with mixed value types needs matching MDB definitions.

## Store and error semantics

The store is seeded with `0x1001 → 42` and `0x1002 → 3000`. Reads omit unknown IDs from the
response; writes skip unknown IDs and apply valid entries. Both handlers log the unknown IDs
and end with completion acknowledgement, even for an all-unknown batch. They do **not** emit
a failed-start report for an unknown parameter. An unsupported subtype gets the generic
invalid-subtype NACK start.

These are the current simulator semantics, not evidence of complete standard rejection
coverage. The earlier claim that unknown parameter IDs cause failed-start acknowledgement
contradicted the implementation. Duplicate IDs in a read collapse to one map entry, so a
report's count need not equal the requested count. No command adds a new parameter ID.

## Manual checks

Start the [example](pus_native_arch.md#build-and-verification).

1. Read with `{"num_params": 2, "param_ids": [4097, 4098]}` and check the seeded values.
2. Set with `{"num_params": 1, "param_entries": [{"param_id": 4097, "param_value": 99}]}`;
   read again and check 99.
3. Read unknown ID 39321 and check completion plus a zero-entry TM[20,2].
4. Mix valid and unknown IDs in a set; check the valid entry changes and the unknown entry
   does not appear in later reports.
5. Restart the simulator and check that the seeded values return.

The store is in memory only. Values outside uint32 fail ground encoding; that is different
from an on-board rejection. Reporting a zero-entry array and mixed valid/unknown requests
should be checked separately from the normal two-parameter path.
