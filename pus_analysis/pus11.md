# PUS Service 11 — Time-based Scheduling

## A. General Context

### What is ST[11]?

PUS Service 11 (Time-based Scheduling) lets ground operators pre-load telecommands into an on-board schedule. The spacecraft releases them at their designated release time without requiring live ground contact. This is essential for autonomous operations during communication blackouts.

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Schedule Queue** | Priority queue ordered by absolute release time |
| **Subschedule** | Named group of commands (8-bit ID); can be independently enabled/disabled |
| **Group** | Optional second-level grouping of commands (8-bit ID); enable/disable together |
| **Request ID** | Unique identifier = `(source_id, apid, seqcount)` of the TC used at insert time |
| **Release Time** | Absolute CUC time (8 bytes) when the stored TC shall be executed |
| **Time Window** | Filter type: `0=all`, `1=from–to`, `2=from`, `3=to` — used for bulk operations |

### Execution Flow

```
Ground → TC[11,4] INSERT → ACK[1,1] → queued
                         → ACK[1,3] (start ACK)
                         → command scheduled in priority queue
                         → ACK[1,7] (completion ACK)

At release time:
  scheduler wakes up → checks subschedule/group enabled
  → pusSimulator.processTc(embeddedTC) → executed as normal TC
```

### Architecture Files

| Layer | File |
|-------|------|
| Java simulator | `simulator/src/main/java/org/yamcs/simulator/pus/Pus11Service.java` |
| XTCE MDB | `examples/pus/src/main/yamcs/mdb/pus11.xml` |
| PUS base types | `examples/pus/src/main/yamcs/mdb/pus.xml` |
| Data types | `examples/pus/src/main/yamcs/mdb/dt.xml` |

### Time Encoding

CUC format, 8 bytes: `1B p-field + 4B coarse seconds + 3B fine sub-seconds`. Implemented in `PusTime.java`. Referenced in XTCE as `/PUS/PusTimeType`.

### XTCE / YAMCS Scheduling Note

The primary ground-side workflow uses `yamcs-client` with the `pus11ScheduleAt` command extra. YAMCS's `PusCommandPostprocessor` automatically wraps the command in a TC[11,4] packet. The MDB TC[11,4] definition is for operators who want to send INSERT_ACTIVITIES manually.

---

## B. TM/TC Implementation Plan

### Overall Status Table

| Subtype | Type | Name | MDB | Java | Action Required |
|---------|------|------|-----|------|-----------------|
| 1 | TC | Enable Scheduler | ✅ | ✅ | None |
| 2 | TC | Disable Scheduler | ✅ | ✅ | None |
| 3 | TC | Reset Scheduler | ✅ | ✅ | None |
| 4 | TC | Insert Activities | ❌ | ✅ | Add MDB definition |
| 5 | TC | Delete by Request ID | ✅ | ✅ | None |
| 6 | TC | Delete by Filter | ✅ | ✅ | None |
| 7 | TC | Time-shift by Request ID | ⚠️ | ✅ | Fix MDB: add `time_offset_ms` |
| 8 | TC | Time-shift by Filter | ⚠️ | ✅ | Fix MDB: add `time_offset_ms` |
| 12 | TC | Summary Report by ID | ✅ | ✅ | None |
| 13 | TM | Summary Report | ✅ | ✅ | None |
| 14 | TC | Summary Report by Filter | ✅ | ✅ | None |
| 15 | TC | Time-shift All | ⚠️ | ✅ | Fix MDB: wrong args |
| 16 | TC | Detail Report All | ✅ | ✅ | None |
| 17 | TC | Summary Report All | ✅ | ✅ | None |
| 18 | TC | Report Subschedule Status | ✅ | ✅ | None |
| 19 | TM | Subschedule Status Report | ✅ | ✅ | None |
| 20 | TC | Enable Subschedules | ✅ | ✅ | None |
| 21 | TC | Disable Subschedules | ✅ | ✅ | None |
| 22 | TC | Create Scheduling Groups | ❌ | ❌ | MDB + Java |
| 23 | TC | Delete Scheduling Groups | ❌ | ❌ | MDB + Java |
| 24 | TC | Enable Scheduling Groups | ❌ | ❌ | MDB + Java |
| 25 | TC | Disable Scheduling Groups | ❌ | ❌ | MDB + Java |
| 26 | TC | Report Group Status | ❌ | ❌ | MDB + Java |
| 27 | TM | Group Status Report | ❌ | ❌ | MDB + Java |

---

### TC[11,1] — Enable the time-based schedule execution function

**Spec**: No application data. Enables the scheduler so stored commands are released at their times.  
**MDB**: ✅ `ENABLE_SCHEDULER` defined, no args.  
**Java**: ✅ Sets `enabled = true`, sends ACK[1,3] + ACK[1,7].  
**Action**: None.

---

### TC[11,2] — Disable the time-based schedule execution function

**Spec**: No application data. Disables the scheduler; queued commands are retained but not released.  
**MDB**: ✅ `DISABLE_SCHEDULER`, no args.  
**Java**: ✅ Sets `enabled = false`.  
**Action**: None.

---

### TC[11,3] — Reset the time-based schedule

**Spec**: No application data. Clears all scheduled activities and disables the scheduler.  
**MDB**: ✅ `RESET_SCHEDULER`, no args.  
**Java**: ✅ Clears `commands` queue, sets `enabled = false`.  
**Action**: None.

---

### TC[11,4] — Insert activities into the time-based schedule

**Spec**:
```
subschedule_id  (uint8)
N               (uint8)   — number of activities
repeat N times:
  release_time  (PusTime, 8 bytes)
  tc_packet     (variable-length CCSDS TC, length = CCSDS_len_field + 7)
```

**MDB**: ❌ No `INSERT_ACTIVITIES` MetaCommand defined.  
**Java**: ✅ `insertActivities()` reads subschedule + N, then for each: reads `PusTime`, reads CCSDS packet (using length field at offset +4), schedules into priority queue.

**Implementation plan** (MDB change):

The N-activity repetition IS expressible using YAMCS's nested array support. Each activity is an aggregate of `{release_time, tc_packet}`. The tc_packet must be declared as fixed-size binary (all scheduled TCs must have the same encoded size — typically the smallest common TC size for the mission).

```xml
<!-- Add to ArgumentTypeSet: -->
<!-- Fixed-size TC packet binary (adjust sizeInBits to match mission's common TC size) -->
<BinaryArgumentType name="TcPacketType">
    <BinaryDataEncoding>
        <SizeInBits><FixedValue>128</FixedValue></SizeInBits>  <!-- 16 bytes example -->
    </BinaryDataEncoding>
</BinaryArgumentType>

<!-- Activity entry: one {release_time, tc_packet} pair -->
<AggregateArgumentType name="ActivityEntryType">
    <MemberList>
        <Member name="release_time" typeRef="/PUS/PusTimeType"/>
        <Member name="tc_packet"    typeRef="TcPacketType"/>
    </MemberList>
</AggregateArgumentType>

<!-- Array of N activity entries; N is a top-level argument -->
<ArrayArgumentType arrayTypeRef="ActivityEntryType" name="ActivityArrayType">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="n"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<!-- MetaCommand: -->
<MetaCommand name="INSERT_ACTIVITIES" shortDescription="TC[11,4] insert activities into the time-based schedule">
    <BaseMetaCommand metaCommandRef="pus11-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="4" />
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument argumentTypeRef="/dt/uint8" name="subschedule_id"/>
        <Argument argumentTypeRef="/dt/uint8" name="n"/>
        <Argument argumentTypeRef="ActivityArrayType" name="activities"/>
    </ArgumentList>
    <CommandContainer name="INSERT_ACTIVITIES">
        <EntryList>
            <ArgumentRefEntry argumentRef="subschedule_id"/>
            <ArgumentRefEntry argumentRef="n"/>
            <ArgumentRefEntry argumentRef="activities"/>
        </EntryList>
        <BaseContainer containerRef="pus11-tc"/>
    </CommandContainer>
</MetaCommand>
```

**Constraint**: All N TCs inside `activities` must have the same declared binary size (`TcPacketType` `FixedValue`). Operators supply each `tc_packet` as a hex string. For heterogeneous TC sizes, use `yamcs-client` `pus11ScheduleAt` instead.

**Limitation**: YAMCS supports repeating a structured aggregate N times in a single command using nested `AggregateArgumentType` + `ArrayArgumentType` (confirmed by `array-in-array-arg.xml`). The `{release_time, tc_packet}` pair COULD be defined as a fixed-size aggregate array — BUT only if `tc_packet` has a known fixed size. Since `tc_packet` is a variable-length CCSDS TC packet (its length is determined by the CCSDS length field embedded within it, not by a preceding count in the outer command), this is the actual barrier. The practical workaround is: define the command for a single activity (n=1) with a fixed-size tc_packet, or use the `yamcs-client` `pus11ScheduleAt` extra which bypasses this entirely. See Section C — Gap #1.

---

### TC[11,5] — Delete scheduled activities by request identifier

**Spec**:
```
N              (uint16)
repeat N times:
  source_id    (uint16)
  apid         (uint16)
  seqcount     (uint16)
```

**MDB**: ✅ `DELETE_ACTIVITIES_BY_ID` with `num_requests (uint16)` + `requests[]` array.  
**Java**: ✅ `deleteByRequestId()` → `filterById(bb, true)`.  
**Action**: None.

---

### TC[11,6] — Delete scheduled activities by filter

**Spec**:
```
filter_type    (uint8)   — 0=all, 1=from-to, 2=from, 3=to
time_tag_1     (PusTime) — present if type 1 or 2
time_tag_2     (PusTime) — present if type 1 or 3
N              (uint8)   — subschedule count
repeat N:
  subschedule_id (uint8)
```

**MDB**: ✅ `DELETE_ACTIVITIES_BY_FILTER`. Note: MDB hardcodes `filter_type=0x01` as a `FixedValueEntry` — this locks it to "from-to" window only.  
**Java**: ✅ `deleteByFilter()` → `filterByFilter(bb, true)` handles all 4 types.  
**Action**: None for basic use. If all filter types are needed, the `filter_type` FixedValueEntry should be changed to an `ArgumentRefEntry`.

---

### TC[11,7] — Time-shift scheduled activities by request identifier

**Spec**:
```
time_offset    (relative time, int32 milliseconds)
N              (uint16)
repeat N:
  source_id / apid / seqcount
```

**MDB**: ⚠️ `TIME_SHIFT_ACTIVITIES_BY_ID` is defined but **missing the `time_offset` argument**. The ArgList starts directly with `num_requests`.  
**Java**: ✅ `timeShiftById()` reads `int timeShiftMillis = bb.getInt()` first, then calls `filterById`.

**Fix required** in pus11.xml:
```xml
<ArgumentList>
    <Argument argumentTypeRef="/dt/uint32" name="time_offset_ms"/>   <!-- ADD THIS -->
    <Argument argumentTypeRef="NumRequestsType" name="num_requests"/>
    <Argument argumentTypeRef="RequestArrayType" name="requests"/>
</ArgumentList>
<CommandContainer name="TIME_SHIFT_ACTIVITIES_BY_ID">
    <EntryList>
        <ArgumentRefEntry argumentRef="time_offset_ms"/>             <!-- ADD THIS -->
        <ArgumentRefEntry argumentRef="num_requests"/>
        <ArgumentRefEntry argumentRef="requests"/>
    </EntryList>
    ...
</CommandContainer>
```

---

### TC[11,8] — Time-shift scheduled activities by filter

**Spec**:
```
time_offset    (int32 ms)
filter_type    (uint8)
[time tags]
N subschedule_ids
```

**MDB**: ⚠️ Same issue as TC[11,7] — `time_offset` missing.  
**Java**: ✅ `timeShiftByFilter()` reads `int timeShiftMillis = bb.getInt()` first.

**Fix required**: Same pattern as TC[11,7] — add `time_offset_ms (uint32)` as first argument.

---

### TC[11,12] — Summary-report activities by request identifier

**Spec**:
```
N              (uint16)
repeat N:
  source_id / apid / seqcount
```

**MDB**: ✅ `GET_SUMMARY_REPORT_BY_ID`.  
**Java**: ✅ `summaryReportById()` → `filterById(bb, false)` → `sendSummaryReport()`.  
**Action**: None.

---

### TM[11,13] — Time-based schedule summary report

**Spec**:
```
N              (uint16)
repeat N:
  schedule_id  (uint8)
  release_time (PusTime, 8 bytes)
  source_id    (uint16)
  apid         (uint16)
  seqcount     (uint16)
```

**MDB**: ✅ `SUMMARY_REPORT` in `SUMMARY_REPORT` SpaceSystem with `SUMMARY_REPORT_ELEMENT` container repeated N times.  
**Java**: ✅ `sendSummaryReport()` encodes each entry as: subschedule(1) + releaseTime(8) + source(2) + apid(2) + seq(2) = 15 bytes/entry.  
**Action**: None.

---

### TC[11,14] — Summary-report activities by filter

**Spec**: Same filter structure as TC[11,6].  
**MDB**: ✅ `GET_SUMMARY_REPORT_BY_FILTER`. Same filter_type hardcoded limitation as TC[11,6].  
**Java**: ✅ `summaryReportByFilter()`.  
**Action**: None for basic use.

---

### TC[11,15] — Time-shift all scheduled activities

**Spec**:
```
time_offset    (relative time, int32 milliseconds)
```
No other arguments — applies the offset to every scheduled activity.

**MDB**: ⚠️ **WRONG**. `TIME_SHIFT_ACTIVITIES` currently has `num_requests (uint16)` + `requests[]` array — this is TC[11,7]'s signature, not TC[11,15].  
**Java**: ✅ `timeShiftAll()` correctly reads only `int timeShiftMillis = bb.getInt()` and iterates all commands.

**Fix required** in pus11.xml — replace the entire ArgumentList and CommandContainer:
```xml
<MetaCommand name="TIME_SHIFT_ACTIVITIES" shortDescription="TC[11,15] time-shift all scheduled activities">
    <BaseMetaCommand metaCommandRef="pus11-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="15" />
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument argumentTypeRef="/dt/uint32" name="time_offset_ms"/>
    </ArgumentList>
    <CommandContainer name="TIME_SHIFT_ACTIVITIES">
        <EntryList>
            <ArgumentRefEntry argumentRef="time_offset_ms"/>
        </EntryList>
        <BaseContainer containerRef="pus11-tc" />
    </CommandContainer>
</MetaCommand>
```

---

### TC[11,16] — Detail-report all scheduled activities

**Spec**: No application data. Responds with TM[11,10] packet(s).  
**MDB**: ✅ `GET_DETAIL_REPORT`, no args.  
**Java**: ✅ `detailReportAll()` → `sendDetailReport(commands)`.  
**Action**: None.

---

### TC[11,17] — Summary-report all scheduled activities

**Spec**: No application data. Responds with TM[11,13].  
**MDB**: ✅ `GET_SUMMARY_REPORT`, no args.  
**Java**: ✅ `summaryReportAll()` → `sendSummaryReport(commands)`.  
**Action**: None.

---

### TC[11,18] — Report the status of each time-based sub-schedule

**Spec**: No application data. Responds with TM[11,19].  
**MDB**: ✅ `GET_SCHEDULE_STATUS`, no args.  
**Java**: ✅ `scheduleStatusReport()` emits TM[11,19] with count + {id, status} entries.  
**Action**: None.

---

### TM[11,19] — Time-based sub-schedule status report

**Spec**:
```
N              (uint32)
repeat N:
  schedule_id  (uint8)
  status       (uint8, 0=disabled 1=enabled)
```

**MDB**: ✅ `SUBSCHEDULE_STATUS_REPORT` with `StatusReportType` array.  
**Java**: ✅ Written by `scheduleStatusReport()`.  
**Action**: None.

---

### TC[11,20] — Enable time-based sub-schedules

**Spec**:
```
N              (uint8)
repeat N:
  subschedule_id (uint8)
```

**MDB**: ✅ `ENABLE_SCHEDULE` with `num_schedules (uint8)` + `schedules[]`.  
**Java**: ✅ `enableSubschedule()` — note: current Java reads only a single subschedule ID (`bb.get()`), ignoring `num_schedules`. Handles N=1 only.  
**Action**: Java should loop N times. Minor fix.

---

### TC[11,21] — Disable time-based sub-schedules

**Spec**: Same structure as TC[11,20].  
**MDB**: ✅ `DISABLE_SCHEDULE`.  
**Java**: ✅ Same single-read limitation as TC[11,20].  
**Action**: Same minor Java fix as TC[11,20].

---

### TC[11,22] — Create time-based scheduling groups

**Spec**:
```
N              (uint8)
repeat N:
  group_id     (uint8)
  group_status (uint8, 0=disabled 1=enabled)
```

**MDB**: ❌ Not defined.  
**Java**: ❌ Returns `NACK(START_ERR_NOT_IMPLEMENTED)`.

**Implementation plan**:

*MDB additions* (pus11.xml):
```xml
<!-- Parameter types (TelemetryMetaData): -->
<IntegerParameterType name="GroupIdType" signed="false">
    <IntegerDataEncoding sizeInBits="8"/>
</IntegerParameterType>
<EnumeratedParameterType name="GroupStatusType">
    <IntegerDataEncoding sizeInBits="8"/>
    <EnumerationList>
        <Enumeration value="0" label="disabled"/>
        <Enumeration value="1" label="enabled"/>
    </EnumerationList>
</EnumeratedParameterType>

<!-- Argument types (CommandMetaData): -->
<IntegerArgumentType name="GroupIdType" baseType="/dt/uint8"/>
<IntegerArgumentType name="NumGroupsType" baseType="/dt/uint8"/>
<AggregateArgumentType name="GroupElementType">
    <MemberList>
        <Member typeRef="GroupIdType" name="group_id"/>
        <Member typeRef="/dt/uint8" name="group_status"/>
    </MemberList>
</AggregateArgumentType>
<ArrayArgumentType arrayTypeRef="GroupElementType" name="GroupArrayType">
    <DimensionList>
        <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
                <DynamicValue>
                    <ArgumentInstanceRef argumentRef="num_groups"/>
                    <LinearAdjustment intercept="-1"/>
                </DynamicValue>
            </EndingIndex>
        </Dimension>
    </DimensionList>
</ArrayArgumentType>

<!-- MetaCommand: -->
<MetaCommand name="CREATE_SCHEDULING_GROUPS"
             shortDescription="TC[11,22] create time-based scheduling groups">
    <BaseMetaCommand metaCommandRef="pus11-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="22"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument argumentTypeRef="NumGroupsType" name="num_groups"/>
        <Argument argumentTypeRef="GroupArrayType" name="groups"/>
    </ArgumentList>
    <CommandContainer name="CREATE_SCHEDULING_GROUPS">
        <EntryList>
            <ArgumentRefEntry argumentRef="num_groups"/>
            <ArgumentRefEntry argumentRef="groups"/>
        </EntryList>
        <BaseContainer containerRef="pus11-tc"/>
    </CommandContainer>
</MetaCommand>
```

*Java addition* (Pus11Service.java):
```java
Map<Integer, Boolean> groupStatus = new HashMap<>();

case 22 -> createGroups(tc);

private void createGroups(PusTcPacket tc) {
    ack_start(tc);
    ByteBuffer bb = tc.getUserDataBuffer();
    int n = bb.get() & 0xFF;
    for (int i = 0; i < n; i++) {
        int groupId = bb.get() & 0xFF;
        boolean enabled = (bb.get() & 0xFF) == 1;
        groupStatus.put(groupId, enabled);
    }
    ack_completion(tc);
}
```

---

### TC[11,23] — Delete time-based scheduling groups

**Spec**:
```
N              (uint8)
repeat N:
  group_id     (uint8)
```

**MDB**: ❌ Not defined. **Java**: ❌ NACK.

**Implementation plan**:

*MDB*: `DELETE_SCHEDULING_GROUPS` — args: `num_groups (uint8)` + `GroupIdArrayType` (array of uint8 group IDs).  
*Java*: `case 23 -> deleteGroups(tc)` — reads N group IDs, calls `groupStatus.remove(groupId)` for each.

```xml
<ArrayArgumentType arrayTypeRef="GroupIdType" name="GroupIdArrayType">
    <DimensionList><Dimension>
        <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
        <EndingIndex><DynamicValue><ArgumentInstanceRef argumentRef="num_groups"/>
            <LinearAdjustment intercept="-1"/></DynamicValue></EndingIndex>
    </Dimension></DimensionList>
</ArrayArgumentType>

<MetaCommand name="DELETE_SCHEDULING_GROUPS"
             shortDescription="TC[11,23] delete time-based scheduling groups">
    <BaseMetaCommand metaCommandRef="pus11-tc">
        <ArgumentAssignmentList>
            <ArgumentAssignment argumentName="subtype" argumentValue="23"/>
        </ArgumentAssignmentList>
    </BaseMetaCommand>
    <ArgumentList>
        <Argument argumentTypeRef="NumGroupsType" name="num_groups"/>
        <Argument argumentTypeRef="GroupIdArrayType" name="group_ids"/>
    </ArgumentList>
    <CommandContainer name="DELETE_SCHEDULING_GROUPS">
        <EntryList>
            <ArgumentRefEntry argumentRef="num_groups"/>
            <ArgumentRefEntry argumentRef="group_ids"/>
        </EntryList>
        <BaseContainer containerRef="pus11-tc"/>
    </CommandContainer>
</MetaCommand>
```

---

### TC[11,24] — Enable time-based scheduling groups

**Spec**:
```
N              (uint8)
repeat N:
  group_id     (uint8)
```

**MDB**: ❌. **Java**: ❌ NACK.  

**Implementation plan**: Same structure as TC[11,23]. MetaCommand `ENABLE_SCHEDULING_GROUPS`, subtype=24. Java sets `groupStatus.put(groupId, true)`.

---

### TC[11,25] — Disable time-based scheduling groups

**Spec**: Same as TC[11,24].  
**MDB**: ❌. **Java**: ❌ NACK.  

**Implementation plan**: MetaCommand `DISABLE_SCHEDULING_GROUPS`, subtype=25. Java sets `groupStatus.put(groupId, false)`.

---

### TC[11,26] — Report the status of each time-based scheduling group

**Spec**: No application data. Responds with TM[11,27].  
**MDB**: ❌. **Java**: ❌ NACK.

**Implementation plan**:

*MDB*: `REPORT_GROUP_STATUS`, no args, subtype=26.  
*Java*:
```java
case 26 -> groupStatusReport(tc);

private void groupStatusReport(PusTcPacket tc) {
    ack_start(tc);
    var pkt = newPacket(27, 4 + groupStatus.size() * 2);
    var bb = pkt.getUserDataBuffer();
    bb.putInt(groupStatus.size());
    for (var me : groupStatus.entrySet()) {
        bb.put(me.getKey().byteValue());
        bb.put((byte)(me.getValue() ? 1 : 0));
    }
    pusSimulator.transmitRealtimeTM(pkt);
    ack_completion(tc);
}
```

---

### TM[11,27] — Time-based scheduling group status report

**Spec**:
```
N              (uint32)
repeat N:
  group_id     (uint8)
  group_status (uint8, 0=disabled 1=enabled)
```

**MDB**: ❌ Not defined. **Java**: ❌ Not emitted.

**Implementation plan**:

*MDB additions* (pus11.xml TelemetryMetaData):
```xml
<!-- ParameterTypeSet: -->
<AggregateParameterType name="GroupStatusElementType">
    <MemberList>
        <Member typeRef="GroupIdType" name="group_id"/>
        <Member typeRef="GroupStatusType" name="group_status"/>
    </MemberList>
</AggregateParameterType>
<ArrayParameterType arrayTypeRef="GroupStatusElementType" name="GroupStatusReportType">
    <DimensionList><Dimension>
        <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
        <EndingIndex><DynamicValue>
            <ParameterInstanceRef parameterRef="group_report_n"/>
            <LinearAdjustment intercept="-1"/>
        </DynamicValue></EndingIndex>
    </Dimension></DimensionList>
</ArrayParameterType>

<!-- ParameterSet: -->
<Parameter parameterTypeRef="/dt/uint32" name="group_report_n"/>
<Parameter parameterTypeRef="GroupStatusReportType" name="group_report"/>

<!-- ContainerSet: -->
<SequenceContainer name="GROUP_STATUS_REPORT"
                   shortDescription="TM[11,27] time-based scheduling group status report">
    <EntryList>
        <ParameterRefEntry parameterRef="group_report_n"/>
        <ArrayParameterRefEntry parameterRef="group_report"/>
    </EntryList>
    <BaseContainer containerRef="pus11-tm">
        <RestrictionCriteria>
            <Comparison parameterRef="/PUS/subtype" comparisonOperator="==" value="27"/>
        </RestrictionCriteria>
    </BaseContainer>
</SequenceContainer>
```

---

## C. Gaps & Shortcomings

### Gap 1 — TC[11,4]: Variable-length embedded TC packet (Critical)

**Problem**: PUS 11 INSERT_ACTIVITIES embeds a complete, variable-length CCSDS TC packet inside each scheduled activity. The embedded TC's length is determined by its **own CCSDS length field** (at byte offset +4 from the packet start), not by a preceding count field in the outer TC.

**What nested array support resolves**: YAMCS supports repeating a `{release_time, tc_packet}` pair N times as a single MetaCommand using `AggregateArgumentType` + `ArrayArgumentType` (confirmed by `array-in-array-arg.xml`). The N-activity repetition is NOT the barrier.

**What it does NOT resolve**: The `tc_packet` field within each activity is variable-length. Its size cannot be declared as a static `FixedValue` and cannot be derived from a sibling argument (the CCSDS length is embedded inside the binary payload, not in a separate argument). XTCE `BinaryArgumentType` requires either a `FixedValue` or a `DynamicValue` that references an **argument in the same command** — not a field buried inside the binary blob.

**Impact**: TC[11,4] cannot be fully defined in XTCE for multi-activity scheduling with heterogeneous TC types. For single-activity scheduling with one known fixed-size TC, a workaround exists:

- Define an `ActivityEntryType` aggregate: `{release_time (PusTimeType), tc_packet (BinaryArgumentType, FixedValue size)}`.
- Use `ArrayArgumentType` over `ActivityEntryType` sized by `N`.
- Operators supply the pre-encoded TC as a hex string; all TCs must be the same declared size.

**Recommended approach**: Use `yamcs-client` with the `pus11ScheduleAt` extra for all production scheduling. The MDB definition of TC[11,4] is for documentation and manual testing only (N=1 or N>1 with uniform fixed-size TCs).

---

### Gap 2 — TC[11,7] and TC[11,8]: Missing `time_offset_ms` in MDB

**Problem**: Both `TIME_SHIFT_ACTIVITIES_BY_ID` and `TIME_SHIFT_ACTIVITIES_BY_FILTER` in pus11.xml are missing the first argument `time_offset_ms`. The Java simulator reads this field first (`bb.getInt()`), so any TC sent via YAMCS using the current MDB will be mis-parsed — the first 4 bytes of `num_requests` will be interpreted as the time offset.

**Impact**: Both subtypes are silently broken when used via YAMCS Web. The TC appears to succeed (ACK returned) but the shift value and target commands are wrong.

**Fix**: Add `<Argument argumentTypeRef="/dt/uint32" name="time_offset_ms"/>` as the first arg in both commands.

---

### Gap 3 — TC[11,15]: Wrong argument signature in MDB

**Problem**: `TIME_SHIFT_ACTIVITIES` (subtype 15) currently has `num_requests + requests[]` — the same signature as TC[11,7]. The PUS spec defines subtype 15 as "shift ALL activities by a single time offset" with no selection arguments. The Java `timeShiftAll()` correctly reads only `bb.getInt()`.

**Impact**: Sending TC[11,15] via YAMCS will send spurious `num_requests` + request-ID data that the Java simulator ignores (after reading the first 4 bytes as the time offset, remaining data is discarded). However the operator interface is misleading and extra bytes are transmitted.

**Fix**: Replace the ArgumentList and CommandContainer with just `time_offset_ms (uint32)`.

---

### Gap 4 — Group management (TC[11,22–26] + TM[11,27]): Not implemented

**Problem**: All group management subtypes return `NACK(NOT_IMPLEMENTED)` and have no MDB definitions. Groups are an optional PUS 11 feature, but they are listed as required in the task spec.

**Impact**: TC[11,22–26] cannot be sent or processed. TM[11,27] is never emitted.

**Effort**: Low-to-medium. Java needs a new `Map<Integer, Boolean> groupStatus`, 5 new case handlers, and 1 new TM emitter (~80 lines). MDB needs new parameter types and 6 new definitions. No architectural change required — mirrors the existing subschedule pattern exactly.

**XTCE feasibility**: ✅ Fully implementable in XTCE. Group management uses the same patterns as subschedule management (TC[11,20/21] + TM[11,19]) which already work.

---

### Gap 5 — TC[11,20/21]: Java only handles N=1

**Problem**: `enableSubschedule()` and `disableSubschedule()` each read only a single `bb.get()` — they ignore `num_schedules` and only process the first subschedule ID. The MDB correctly encodes an array.

**Impact**: Sending ENABLE_SCHEDULE or DISABLE_SCHEDULE with N > 1 from YAMCS will only enable/disable the first subschedule. No error is reported.

**Fix** (minor, Java only): Loop `num_schedules` times.

---

### Gap 6 — TC[11,6/8/14]: `filter_type` hardcoded to `0x01`

**Problem**: The MDB for filter-based commands uses `<FixedValueEntry binaryValue="01" sizeInBits="8" name="filter_type"/>` which always sends `type=1` (from-to time window). Operators cannot select other filter types (select-all, from-time, to-time) from YAMCS Web.

**Impact**: Only time-range based filtering is accessible via UI. Select-all (type=0) is most common and is not reachable.

**Fix**: Change `FixedValueEntry` to `ArgumentRefEntry` with an enumerated `FilterTypeType` argument.

---

### Summary

| Gap | Severity | XTCE-only fix? | Effort |
|-----|----------|----------------|--------|
| #1 TC[11,4] embedded TC | High | ❌ No — N-activity array is expressible, but variable-length tc_packet binary is not | N/A (by design) |
| #2 TC[11,7/8] missing offset | High | ✅ Yes | Trivial |
| #3 TC[11,15] wrong args | High | ✅ Yes | Trivial |
| #4 Groups TC[11,22–26]+TM[11,27] | Medium | Partial — MDB ✅, Java ❌ | Low |
| #5 TC[11,20/21] N>1 | Low | ❌ Java only | Trivial |
| #6 TC[11,6/8/14] filter_type | Low | ✅ Yes | Minor |
