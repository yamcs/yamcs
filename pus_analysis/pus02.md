# PUS Service 2 — Device Access

## A. General Context

### What is ST[02]?

PUS Service 2 (Device Access Service) provides the capability to distribute commands to and acquire data from on-board devices. Unlike most PUS services, it operates at a **low level, bypassing the nominal on-board software functions** — it is primarily used for:

- Direct hardware commanding during assembly, integration, and test (AIT) phases
- In-flight troubleshooting and direct device access when nominal application software is unavailable or bypassed
- Accessing devices that have no direct support for other PUS services (raw actuators, sensors, CPDUs, bus remote terminals)

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Device access subservice** | The single standardized subservice type for all ST[02] operations |
| **Physical access** | Commands directed to hardware by specifying the transmission link, protocol, and protocol-specific data directly |
| **Logical access** | Commands directed to abstract logical device identifiers; on-board software maps these to physical devices and protocols |
| **On/off device** | Hardware entity that accepts simple binary commands (on/off, open/close, reset) by address |
| **Register** | Hardware register accessible by address; supports load (write) and dump (read) operations |
| **CPDU** | Command Pulse Distribution Unit — receives pulse commands via output line IDs |
| **Transaction ID** | Identifier in data acquisition requests that links the TC instruction to the TM response |
| **Deduced** | Field whose size and structure is determined by the declared device properties, not by a generic field in the packet |

### Subservice Types Summary (Full ST[02] Spec)

| Subtype | Type | Name | Capability Group |
|---------|------|------|-----------------|
| 1 | TC | Distribute on/off device commands | On/off devices |
| 2 | TC | Distribute register load commands | Registers |
| 4 | TC | Distribute CPDU commands | CPDU |
| 5 | TC | Distribute register dump commands | Registers |
| 6 | TM | Register dump report | Registers |
| **7** | **TC** | **Distribute physical device commands** | **Physical device** |
| **8** | **TC** | **Acquire data from physical devices** | **Physical device** |
| **9** | **TM** | **Physical device data report** | **Physical device** |
| **10** | **TC** | **Distribute logical device commands** | **Logical device** |
| **11** | **TC** | **Acquire data from logical devices** | **Logical device** |
| **12** | **TM** | **Logical device data report** | **Logical device** |

Bold = required by task.

### Architecture Role in YAMCS Context

**Critical architectural distinction**: ST[02] is fundamentally different from ST[11] or ST[21].

- ST[11] and ST[21] manage **internal on-board state** (schedules, sequences) that YAMCS can replicate natively — hence a `StreamTcCommandReleaser` subclass that intercepts TCs and manages that state makes sense.
- ST[02] is a **device bus interface protocol** — its entire purpose is to talk to physical hardware over specific bus protocols (MIL-STD-1553B, SpaceWire, I2C, CAN, etc.). The on-board software maps commands to bus transactions.

When **YAMCS acts as the ground station** (normal deployment):
- YAMCS encodes and sends TC[2,7/8/10/11] to the spacecraft
- The spacecraft's on-board software handles the actual device access
- The spacecraft sends back TM[2,9/12] data reports
- YAMCS decodes the incoming TM packets

**No native Java service (CommandReleaser interceptor) is needed.** The implementation is pure XTCE MDB definitions that allow YAMCS to encode TCs correctly and decode TM responses. The spacecraft does all device access work.

The only scenario where a native YAMCS implementation would be required is if YAMCS itself were acting as the on-board computer controlling physical hardware buses — not the intended deployment.

---

## B. Required TM/TC Implementation Plan

### TC[2,7] — Distribute Physical Device Commands

**Spec (§6.2.7.1.2, §8.2.2.6):**

```
N                       (unsigned integer)       — number of instructions
repeated N times:
  physical_device_id    (enumerated)             — declares the device
  protocol_specific_data (deduced)               — determined by the device's declared protocol
  command_data          (deduced)                — data words / command payload for that device
```

**Packet binary layout (Figure 8-15):**

```
[ N | physical_device_id | protocol_specific_data | command_data ] × N
```

**Semantics:**
- All N instructions are validated before execution. If ANY instruction refers to an unknown device or contains invalid protocol-specific data → entire request is rejected, failed start-of-execution notification generated.
- If all valid → execute in order of appearance.
- Per instruction: transmit command data to the physical device via the applicable protocol; check transmission result; if failed → generate failed execution notification with `{instruction_index, transmission_return_code, auxiliary_data}`.
- If any instruction fails → generate a single failed completion-of-execution report containing the first failed progress notification.

**MDB/XTCE implementation plan:**

Because `protocol_specific_data` and `command_data` are "deduced" (their type and size are declared per physical device, not generic), a **single generic MetaCommand is not possible**. Instead, define **one MetaCommand per physical device type** (or per device × command combination):

```xml
<!-- For a specific device, e.g., a MIL-STD-1553B bus remote terminal: -->
<MetaCommand name="PHYS_CMD_RT_01" shortDescription="TC[2,7] command RT-01 on 1553 bus">
    <BaseMetaCommand metaCommandRef="pus2-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="7"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <!-- N is fixed=1 for single-device commands; use FixedValueEntry -->
        <!-- physical_device_id is fixed for this specific command -->
        <!-- protocol_specific_data: transaction direction + sub-address + word count (device-specific) -->
        <Argument argumentTypeRef="/dt/uint8" name="direction"/>
        <Argument argumentTypeRef="/dt/uint8" name="sub_address"/>
        <Argument argumentTypeRef="/dt/uint8" name="word_count"/>
        <!-- command_data: up to 32 × 16-bit words for 1553B -->
        <Argument argumentTypeRef="DataWordsType" name="command_data"/>
    </ArgumentList>
    <CommandContainer name="PHYS_CMD_RT_01">
        <EntryList>
            <FixedValueEntry binaryValue="01" sizeInBits="8" name="N"/>
            <FixedValueEntry binaryValue="..." sizeInBits="..." name="physical_device_id"/>
            <ArgumentRefEntry argumentRef="direction"/>
            <ArgumentRefEntry argumentRef="sub_address"/>
            <ArgumentRefEntry argumentRef="word_count"/>
            <ArgumentRefEntry argumentRef="command_data"/>
        </EntryList>
        <BaseContainer containerRef="pus2-tc"/>
    </CommandContainer>
</MetaCommand>
```

**N > 1 with uniform field sizes**: If the mission declares a uniform fixed size for `protocol_specific_data` and `command_data` across all physical device types (e.g., all 1553B devices use a 4-byte protocol header + 64-byte data block), the `AggregateArgumentType` + `ArrayArgumentType` sibling-member pattern enables a generic N-instruction MetaCommand:

```xml
<AggregateArgumentType name="PhysicalCmdEntry">
    <MemberList>
        <Member name="device_id"      typeRef="PhysDevIdType"/>
        <Member name="protocol_data"  typeRef="ProtocolDataType"/>  <!-- FixedValue sizeInBits declared per mission -->
        <Member name="command_data"   typeRef="CommandDataType"/>   <!-- FixedValue sizeInBits declared per mission -->
    </MemberList>
</AggregateArgumentType>

<ArrayArgumentType name="PhysicalCmdArray" arrayTypeRef="PhysicalCmdEntry">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<MetaCommand name="PHYS_CMD_BATCH" shortDescription="TC[2,7] physical device commands (N instructions, uniform sizes)">
    <BaseMetaCommand metaCommandRef="pus2-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="7"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument argumentTypeRef="/dt/uint8"      name="N"/>
        <Argument argumentTypeRef="PhysicalCmdArray" name="instructions"/>
    </ArgumentList>
    <CommandContainer name="PHYS_CMD_BATCH">
        <EntryList>
            <ArgumentRefEntry argumentRef="N"/>
            <ArgumentRefEntry argumentRef="instructions"/>
        </EntryList>
        <BaseContainer containerRef="pus2-tc"/>
    </CommandContainer>
</MetaCommand>
```

**Key constraint**: This only works when `protocol_specific_data` and `command_data` are the **same fixed size for every device type** in the mission. If different device types have different field sizes — the spec's intent for "deduced" — per-device MetaCommands with N=1 remain required.

---

### TC[2,8] — Acquire Data from Physical Devices

**Spec (§6.2.7.1.3, §8.2.2.7):**

```
N                       (unsigned integer)       — number of instructions
repeated N times:
  transaction_id        (unsigned integer)       — echoed in TM[2,9] response
  physical_device_id    (enumerated)             — declares the device
  protocol_specific_data (deduced)               — device-specific acquisition parameters
```

**Packet binary layout (Figure 8-16):**

```
[ N | transaction_id | physical_device_id | protocol_specific_data ] × N
```

**Semantics:**
- Reject if unknown device or invalid protocol-specific data → failed start-of-execution notification.
- For each valid instruction: transmit acquisition command to physical device; check return code; if successful → generate TM[2,9] physical device data notification; if failed → generate failed execution notification with `{transaction_id, transaction_execution_status}`.
- Each TM[2,9] report contains exactly one physical device data notification.
- If any instruction fails → generate a single failed completion-of-execution report.

**MDB/XTCE implementation plan:**

Same device-specific approach as TC[2,7]. Define one MetaCommand per device type (N=1):

```xml
<MetaCommand name="PHYS_ACQ_RT_01" shortDescription="TC[2,8] acquire data from RT-01">
    <BaseMetaCommand metaCommandRef="pus2-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="8"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument argumentTypeRef="/dt/uint16" name="transaction_id"/>
        <!-- physical_device_id fixed for this device -->
        <!-- protocol_specific_data: sub-address, word_count for 1553B read -->
        <Argument argumentTypeRef="/dt/uint8" name="sub_address"/>
        <Argument argumentTypeRef="/dt/uint8" name="word_count"/>
    </ArgumentList>
    <CommandContainer name="PHYS_ACQ_RT_01">
        <EntryList>
            <FixedValueEntry binaryValue="01" sizeInBits="8" name="N"/>
            <ArgumentRefEntry argumentRef="transaction_id"/>
            <FixedValueEntry binaryValue="..." sizeInBits="..." name="physical_device_id"/>
            <ArgumentRefEntry argumentRef="sub_address"/>
            <ArgumentRefEntry argumentRef="word_count"/>
        </EntryList>
        <BaseContainer containerRef="pus2-tc"/>
    </CommandContainer>
</MetaCommand>
```

**N > 1 with uniform field sizes**: The same `AggregateArgumentType` + `ArrayArgumentType` pattern described for TC[2,7] applies. Each instruction entry is `{transaction_id, physical_device_id, protocol_specific_data}`. Works only when `protocol_specific_data` size is mission-uniform.

---

### TM[2,9] — Physical Device Data Report

**Spec (§6.2.7.1.3, §8.2.2.8):**

```
transaction_id                  (unsigned integer)
transaction_execution_status:
  data_acquisition_return_code  (enumerated)      — device-specific return code
  auxiliary_data                (deduced, optional) — device-specific additional status
data_block                      (deduced)          — acquired data words
```

**Packet binary layout (Figure 8-17):**

```
[ transaction_id | data_acq_return_code | auxiliary_data (opt) | data_block ]
```

Note: `auxiliary_data` has "deduced presence" — whether it is present and its size is determined by the return code's declared association with auxiliary data.

**MDB/XTCE implementation plan:**

Define one SequenceContainer per physical device type (since data_block structure is device-specific):

```xml
<!-- Base PUS2 TM container (from pus.xml base pattern) -->
<SequenceContainer name="pus2-tm" abstract="true">
    <BaseContainer containerRef="/PUS/PusTm">
        <RestrictionCriteria>
            <Comparison parameterRef="/PUS/service" comparisonOperator="==" value="2"/>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>

<!-- TM[2,9] for a specific device type RT-01 -->
<SequenceContainer name="PHYS_DATA_REPORT_RT_01"
                   shortDescription="TM[2,9] physical device data report from RT-01">
    <EntryList>
        <ParameterRefEntry parameterRef="transaction_id"/>
        <ParameterRefEntry parameterRef="data_acq_return_code"/>
        <!-- auxiliary_data: conditional on return code, mission-specific -->
        <ParameterRefEntry parameterRef="data_block_word_1"/>
        <!-- ... additional words based on declared word_count from TC -->
    </EntryList>
    <BaseContainer containerRef="pus2-tm">
        <RestrictionCriteria>
            <Comparison parameterRef="/PUS/subtype" comparisonOperator="==" value="9"/>
            <!-- Additional discrimination by APID or device-specific parameter if needed -->
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Key constraint**: The variable-presence of `auxiliary_data` and the variable-size `data_block` require device-specific containers. A single generic TM[2,9] container can only cover the fixed-prefix fields (`transaction_id`, `data_acq_return_code`). The data block fields must be defined per device type using separate containers with APID or context discrimination.

---

### TC[2,10] — Distribute Logical Device Commands

**Spec (§6.2.7.2.2, §8.2.2.9):**

```
N                       (unsigned integer)       — number of instructions
repeated N times:
  logical_device_id     (enumerated)             — mission-defined logical device
  command_id            (deduced)                — specific to the logical device
  command_arguments     (deduced)                — specific to the command
```

**Packet binary layout (Figure 8-18):**

```
[ N | logical_device_id | command_id | command_arguments ] × N
```

**Semantics:**
- Reject if unknown logical device or unknown command → failed start-of-execution notification.
- For valid instructions: on-board software maps logical_device_id → physical device + communication link + protocol; maps command_id → protocol-specific data; formats command data from arguments; transmits; checks result.
- Per-instruction failure: `{instruction_index, transmission_return_code, auxiliary_data}`.
- Any failure → single failed completion report.

**MDB/XTCE implementation plan:**

This subtype is more tractable than TC[2,7/8]. The `command_id` and `command_arguments` are declared per logical device in the mission's subservice specification with fixed sizes. Two complementary approaches:

**Option A — Per-device MetaCommand (N=1, fully typed arguments)**

Best when different commands have different argument structures:

```xml
<!-- Logical device: SENSOR_IMU, Command: SET_SAMPLE_RATE -->
<MetaCommand name="LOG_CMD_IMU_SET_RATE" shortDescription="TC[2,10] IMU set sample rate">
    <BaseMetaCommand metaCommandRef="pus2-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="10"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument argumentTypeRef="/dt/uint8" name="sample_rate_hz"/>
    </ArgumentList>
    <CommandContainer name="LOG_CMD_IMU_SET_RATE">
        <EntryList>
            <FixedValueEntry binaryValue="01" sizeInBits="8" name="N"/>
            <FixedValueEntry binaryValue="..." sizeInBits="..." name="logical_device_id"/>
            <FixedValueEntry binaryValue="..." sizeInBits="..." name="command_id"/>
            <ArgumentRefEntry argumentRef="sample_rate_hz"/>
        </EntryList>
        <BaseContainer containerRef="pus2-tc"/>
    </CommandContainer>
</MetaCommand>
```

**Option B — Generic N-instruction MetaCommand (N > 1, uniform argument sizes)**

When all logical device commands share the same `command_id` size and `command_arguments` size, use the `AggregateArgumentType` + `ArrayArgumentType` sibling-member pattern (confirmed working in YAMCS — see `pus_simulator_architecture.md` §Nested Dynamic Array Patterns):

```xml
<!-- Each instruction: fixed sizes for logical_device_id, command_id, command_args -->
<AggregateArgumentType name="LogicalCmdEntry">
    <MemberList>
        <Member name="logical_device_id" typeRef="LogDevIdType"/>   <!-- e.g., uint8 -->
        <Member name="command_id"        typeRef="CmdIdType"/>      <!-- e.g., uint8 -->
        <Member name="command_args"      typeRef="CmdArgsType"/>    <!-- fixed-size binary per mission -->
    </MemberList>
</AggregateArgumentType>

<ArrayArgumentType name="LogicalCmdArray" arrayTypeRef="LogicalCmdEntry">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<MetaCommand name="DISTRIBUTE_LOGICAL_CMDS" shortDescription="TC[2,10] distribute logical device commands">
    <BaseMetaCommand metaCommandRef="pus2-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="10"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument argumentTypeRef="/dt/uint8"      name="N"/>
        <Argument argumentTypeRef="LogicalCmdArray" name="instructions"/>
    </ArgumentList>
    <CommandContainer name="DISTRIBUTE_LOGICAL_CMDS">
        <EntryList>
            <ArgumentRefEntry argumentRef="N"/>
            <ArgumentRefEntry argumentRef="instructions"/>
        </EntryList>
        <BaseContainer containerRef="pus2-tc"/>
    </CommandContainer>
</MetaCommand>
```

The operator sets N and fills in an array of `{logical_device_id, command_id, command_args}` entries in the YAMCS UI. `N=0` produces a zero-length array (valid per spec "add all" semantics).

---

### TC[2,11] — Acquire Data from Logical Devices

**Spec (§6.2.7.2.3, §8.2.2.10):**

```
N                       (unsigned integer)       — number of instructions
repeated N times:
  transaction_id        (unsigned integer)       — echoed in TM[2,12]
  logical_device_id     (enumerated)             — mission-defined logical device
  parameter_id          (enumerated)             — specific to the logical device
```

**Packet binary layout (Figure 8-19):**

```
[ N | transaction_id | logical_device_id | parameter_id ] × N
```

**Semantics:**
- Reject if unknown logical device or unknown parameter → failed start-of-execution notification.
- For valid: on-board maps logical_device_id → physical + protocol; maps parameter_id → protocol-specific acquisition command; transmits; checks return code; if successful → TM[2,12] with acquired parameter value; if failed → failed execution notification.
- Each TM[2,12] report contains exactly one logical device data notification.

**MDB/XTCE implementation plan:**

TC[2,11] is the most tractable of the required subtypes. All fields (`transaction_id`, `logical_device_id`, `parameter_id`) have well-defined, fixed-size enumerated types with no "deduced" variability. A fully generic N-instruction MetaCommand is achievable using the sibling-member nested array pattern:

```xml
<!-- Single instruction entry: all fixed-size fields -->
<AggregateArgumentType name="LogicalAcqEntry">
    <MemberList>
        <Member name="transaction_id"    typeRef="/dt/uint16"/>
        <Member name="logical_device_id" typeRef="LogDevIdType"/>   <!-- e.g., uint8 enumerated -->
        <Member name="parameter_id"      typeRef="ParamIdType"/>    <!-- e.g., uint8 enumerated -->
    </MemberList>
</AggregateArgumentType>

<ArrayArgumentType name="LogicalAcqArray" arrayTypeRef="LogicalAcqEntry">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="N"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<MetaCommand name="ACQUIRE_LOGICAL_DEVICE_DATA"
             shortDescription="TC[2,11] acquire data from logical devices">
    <BaseMetaCommand metaCommandRef="pus2-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="11"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument argumentTypeRef="/dt/uint8"      name="N"/>
        <Argument argumentTypeRef="LogicalAcqArray" name="instructions"/>
    </ArgumentList>
    <CommandContainer name="ACQUIRE_LOGICAL_DEVICE_DATA">
        <EntryList>
            <ArgumentRefEntry argumentRef="N"/>
            <ArgumentRefEntry argumentRef="instructions"/>
        </EntryList>
        <BaseContainer containerRef="pus2-tc"/>
    </CommandContainer>
</MetaCommand>
```

This single MetaCommand covers all device × parameter combinations for any N ≥ 1. If `parameter_id` enumerations overlap across devices (same value means different things per device), per-device MetaCommands remain cleaner for type safety, but the generic command still encodes correctly.

---

### TM[2,12] — Logical Device Data Report

**Spec (§6.2.7.2.3, §8.2.2.11):**

```
transaction_id                  (unsigned integer)
transaction_execution_status:
  data_acquisition_return_code  (enumerated)      — device-specific return code
  auxiliary_data                (deduced, optional) — device-specific additional status
parameter_value                 (deduced)          — value of the acquired parameter
```

**Packet binary layout (Figure 8-20):**

```
[ transaction_id | data_acq_return_code | auxiliary_data (opt) | parameter_value ]
```

**Semantics**: Each TM[2,12] contains exactly one logical device data notification. The `parameter_value` type and size depend on the declared parameter type for that logical device's parameter_id.

**MDB/XTCE implementation plan:**

Define one SequenceContainer per logical device × parameter type:

```xml
<!-- TM[2,12] for IMU angular rate parameter (float32 vector) -->
<SequenceContainer name="LOG_DATA_IMU_ANGULAR_RATE"
                   shortDescription="TM[2,12] IMU angular rate data report">
    <EntryList>
        <ParameterRefEntry parameterRef="transaction_id"/>
        <ParameterRefEntry parameterRef="data_acq_return_code"/>
        <!-- auxiliary_data optional: omit if never present for this device -->
        <ParameterRefEntry parameterRef="imu_angular_rate_x"/>
        <ParameterRefEntry parameterRef="imu_angular_rate_y"/>
        <ParameterRefEntry parameterRef="imu_angular_rate_z"/>
    </EntryList>
    <BaseContainer containerRef="pus2-tm">
        <RestrictionCriteria>
            <Comparison parameterRef="/PUS/subtype" comparisonOperator="==" value="12"/>
            <!-- Discrimination by APID or additional context parameter -->
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

**Note**: If all TM[2,12] packets use the same APID, discrimination between parameter types requires either:
1. A `parameter_id` field echoed in the TM (not in PUS spec — spec only echoes `transaction_id`)
2. Using separate APIDs per device/parameter type
3. Accepting that all TM[2,12] are mapped to a single generic container with raw binary data_block, decoded externally

---

## C. Gaps and Shortcomings per TM/TC

### Gap 1 — TC[2,7/8]: "Deduced" protocol-specific fields prevent fully generic XTCE definition (High)

**Problem**: `protocol_specific_data` and `command_data` in TC[2,7], and `protocol_specific_data` in TC[2,8], are "deduced" — their structure and size are declared per physical device in the mission's device access subservice specification, not fixed by the PUS standard.

**Impact**: Cannot define a single generic MetaCommand covering all physical device types when those devices have different field sizes.

**XTCE feasibility**: Partial.
- Per-device MetaCommands (N=1, fixed device ID): fully expressible — no limitation.
- Generic N-instruction MetaCommand via `AggregateArgumentType` + `ArrayArgumentType`: **works when `protocol_specific_data` and `command_data` are mission-uniform fixed sizes** across all device types in the request. The nested array sibling-member pattern (confirmed working in YAMCS) handles N > 1 in this case.
- Generic multi-device command with heterogeneous field sizes: ❌ not expressible.

**Workaround**: Define per-device MetaCommands with N=1 as the safe baseline. Add a generic N-instruction variant if the mission standardizes field sizes.

**Effort**: MDB authoring only. No Java needed.

---

### Gap 2 — TM[2,9/12]: "Deduced presence" auxiliary_data and variable data_block (High)

**Problem**: Both TM[2,9] and TM[2,12] contain fields with "deduced presence" (auxiliary_data) and a `data_block` / `parameter_value` whose type and size is determined by the specific device/parameter, not by a generic preceding size field accessible to XTCE.

**Impact**: Cannot define a single generic TM[2,9] or TM[2,12] SequenceContainer that correctly decodes all possible payloads. Must define per-device containers.

**Discrimination problem**: TM[2,9] and TM[2,12] packets can only be discriminated by APID + subtype (9 or 12). If different devices use the same APID, multiple TM[2,9] containers cannot be selected by XTCE restriction criteria alone unless an additional discriminating field (device ID echoed in TM) is present — which PUS spec does not mandate.

**XTCE feasibility**: Partial. Device-specific containers work if discrimination is possible. If not, raw binary parameters are the fallback.

**Workaround**: Assign separate APIDs per device type (mission design decision), or accept a single generic container with a raw `data_block` binary parameter.

**Effort**: MDB authoring + potential mission APID allocation decision.

---

### Gap 3 — N > 1 multi-instruction requests (Resolved for TC[2,10/11]; Partial for TC[2,7/8])

**Resolution**: The `AggregateArgumentType` + `ArrayArgumentType` sibling-member `ArgumentInstanceRef` pattern (confirmed working in YAMCS — see `pus_simulator_architecture.md` §Nested Dynamic Array Patterns, TC side) directly solves N > 1 for subtypes with fixed field sizes.

**TC[2,11]**: ✅ Fully resolved. All fields (`transaction_id`, `logical_device_id`, `parameter_id`) are fixed-size enumerations — a single generic N-instruction MetaCommand covers all combinations. See implementation plan above.

**TC[2,10]**: ✅ Resolved when `command_args` size is mission-uniform. Each instruction entry `{logical_device_id, command_id, command_args}` is a fixed-size aggregate. If different commands have different `command_args` sizes, fall back to per-device MetaCommands (N=1, Option A).

**TC[2,7/8]**: Partial. N > 1 is expressible when `protocol_specific_data` and `command_data` are declared with a mission-uniform fixed size. If device types have heterogeneous field sizes, N=1 per-device MetaCommands remain the only option.

**XTCE feasibility summary**:
- TC[2,11]: ✅ Fully generic N-instruction MetaCommand
- TC[2,10]: ✅ Generic if `command_args` size is uniform; per-device otherwise
- TC[2,7/8]: Partial — generic only with mission-uniform field sizes; per-device (N=1) otherwise

---

### Gap 4 — No native Java service needed (Informational)

**Clarification**: Unlike ST[11] and ST[21], ST[02] does NOT benefit from a native YAMCS Java interceptor. The service purpose is hardware device access, which is inherently spacecraft-side. Implementing a `Pus2DeviceAccessService` in YAMCS would require YAMCS to directly drive hardware buses — not applicable to the standard ground-station deployment.

**Implication**: The ST[02] implementation is purely MDB (XTCE definitions). There are no "minor code changes" required unless custom TM post-processing or deduplication is needed.

---

### Gap 5 — TM[2,12] parameter type discrimination (Medium)

**Problem**: The `transaction_id` field in TM[2,12] can be used to correlate back to the TC[2,11] instruction that generated it, but XTCE restriction criteria cannot perform this correlation dynamically (it would require matching `transaction_id` against a command history value, not a static parameter comparison).

**Impact**: Cannot select different TM[2,12] SequenceContainers based on which parameter was requested. The container selection must be based on a static field in the packet (APID, or a device discriminator field if present).

**Workaround**: If the spacecraft echoes the `logical_device_id` and `parameter_id` in the TM[2,12] packet (as an extension beyond the minimum PUS spec), XTCE restriction criteria can discriminate. Without this, the mission must use APID-based discrimination.

---

### Summary Table

| Subtype | XTCE Only? | Java Needed? | N>1 support | Key Remaining Gap | Effort |
|---------|-----------|-------------|-------------|-------------------|--------|
| TC[2,7] | ✅ Yes | ❌ No | Partial — generic array if field sizes mission-uniform; else N=1 per-device | Heterogeneous device field sizes | MDB authoring |
| TC[2,8] | ✅ Yes | ❌ No | Same as TC[2,7] | Same as TC[2,7] | MDB authoring |
| TM[2,9] | Partial | ❌ No | N/A (1 notification per packet per spec) | Variable data_block + optional aux_data; APID discrimination needed | MDB + mission APID design |
| TC[2,10] | ✅ Yes | ❌ No | ✅ Generic array if command_args uniform; per-device otherwise | command_args size variability | MDB authoring |
| TC[2,11] | ✅ Yes | ❌ No | ✅ Fully generic single MetaCommand | None — fully resolved | MDB authoring (minimal) |
| TM[2,12] | Partial | ❌ No | N/A (1 notification per packet per spec) | Variable parameter_value + optional aux_data; APID discrimination needed | MDB + mission design |

### Overall Assessment

ST[02] is implementable **entirely with XTCE MDB definitions and zero Java code changes**:

1. **TC[2,11]** is fully generic — one MetaCommand covers all devices and parameters for any N, using the `AggregateArgumentType` + `ArrayArgumentType` nested array pattern.
2. **TC[2,10]** is generic for any N when `command_args` is mission-uniformly sized; otherwise per-device MetaCommands with N=1.
3. **TC[2,7/8]** require per-device MetaCommands with N=1 for heterogeneous device types; a generic N-instruction MetaCommand is possible when the mission standardizes `protocol_specific_data` and `command_data` sizes.
4. **TM[2,9/12]** are the hardest: "deduced presence" auxiliary data and variable-type payload fields are not expressible generically. Per-device `SequenceContainer`s with APID-based discrimination are the correct approach. Discrimination without separate APIDs requires the spacecraft to echo a device discriminator field in the TM (a mission extension beyond the minimum PUS spec).
5. The PUS spec's "deduced" fields are a design-time declaration — they become concrete fixed types per mission device declaration. XTCE handles this correctly by requiring per-device definitions.
