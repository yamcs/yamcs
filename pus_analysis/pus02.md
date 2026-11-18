# PUS ST[02] — Device access

Reviewed against commit `90d4a3d1c` on 2026-11-18. Ground support is in
[pus2.xml](../examples/pus/src/main/yamcs/mdb/pus2.xml); spacecraft device behavior is modeled
by [Pus2Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus2Service.java).

## Scope and responsibility

The example implements TC[2,7/8/10/11] and TM[2,9/12]. On/off-device, register and CPDU
operations from the wider service are outside this example. Yamcs encodes commands and
decodes replies; physical bus access and validation are on-board responsibilities. The same
ground/on-board separation applies to ST[11] and ST[21]; neither requires a local on-board
command interceptor in `yamcs-core`.

## Current mission layout

| Message | Service-data fields |
|---|---|
| TC[2,7] | `n:u8`, instructions of `{physical_device_id:u8, protocol_data, command_data:8 bytes}` |
| TC[2,8] | `n:u8`, instructions of `{transaction_id:u16, physical_device_id:u8, protocol_data}` |
| TM[2,9] | `transaction_id:u16`, return code:u8, data block of 4 × uint16 |
| TC[2,10] | `n:u8`, instructions of `{logical_device_id:u8, command_id:u8, command_args:u32}` |
| TC[2,11] | `n:u8`, instructions of `{transaction_id:u16, logical_device_id:u8, parameter_id:u8}` |
| TM[2,12] | `transaction_id:u16`, return code:u8, parameter value:u32 |

`protocol_data` is `{direction:u8, sub_address:u8, word_count:u16}` (4 bytes).
Auxiliary data is absent in this example. All physical/logical device entries use the same
fixed representations, which permits generic multi-instruction aggregate arrays. TM reports
share APID 1 and are selected by service/subtype. The transaction ID can be used by client
logic to correlate a reply, but XTCE restriction criteria do not perform a runtime lookup of
an earlier command's arguments.

The PUS concept of fields whose representation is deduced from a device declaration does not
mean the packet carries a runtime type description. Other missions with differing bus data,
optional auxiliary data or heterogeneous logical values need corresponding MDB definitions
and a reliable discriminator. Per-device commands/containers are one possible design;
separate APIDs are not required by this fixed-layout example.

No new ground Java handler is needed for the sample layout. Real hardware buses, complete
bus-error behavior and arbitrary device protocols are not emulated by these sample values.

## Sending Commands from the Web UI — Valid Inputs

After starting Yamcs + the simulator (see `pus_analysis/pus_simulator_architecture.md` for
build/run), go to **Commanding → Send a command → `/PUS2/...`** in the web UI and fill in the
arguments.

The simulator validates every request against this sample device registry (`Pus2Service.java`):

- **Physical devices:** `1`, `2`
- **Logical devices & commands:** device `1` → command `1` or `2`; device `2` → command `1`
- **Logical parameters (device, parameter → value):** `(1,1)→100`, `(1,2)→25`, `(2,1)→4200`

Set `n` to the number of entries in the `instructions` array (they must match). The examples
below use `n = 1`. Valid inputs get ACK start + ACK completion, and the acquire commands also
emit a TM report. Any device/command/parameter outside the registry gets a **NACK start**
(unknown physical device / logical device / device command / device parameter).

**`DISTRIBUTE_PHYSICAL_CMDS` — TC[2,7]**
```
n = 1
instructions = [
  { physical_device_id: 1,
    protocol_data: { direction: 0, sub_address: 1, word_count: 4 },
    command_data: 0x1122334455667788 }   # 8-byte binary
]
```

**`ACQUIRE_PHYSICAL_DATA` — TC[2,8]** → replies TM[2,9]
```
n = 1
instructions = [
  { transaction_id: 777, physical_device_id: 1,
    protocol_data: { direction: 1, sub_address: 1, word_count: 4 } }
]
```

**`DISTRIBUTE_LOGICAL_CMDS` — TC[2,10]**
```
n = 1
instructions = [
  { logical_device_id: 1, command_id: 2, command_args: 42 }
]
```

**`ACQUIRE_LOGICAL_DATA` — TC[2,11]** → replies TM[2,12]
```
n = 1
instructions = [
  { transaction_id: 777, logical_device_id: 1, parameter_id: 2 }
]
```

To send multiple instructions in one command, set `n` accordingly and add that many entries,
e.g. `n = 2` with two objects in `instructions`.

The TM reports (TM[2,9] / TM[2,12]) and the command acknowledgements appear in the web UI
under the `/PUS2` space system and in the command history.
