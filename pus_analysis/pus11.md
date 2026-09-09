# PUS Service 11 — Time-based Scheduling

## A. General Context

### Deployment Context

This codebase implements a **ground-station MCS only** — the spacecraft is on the other end of the link. The Java simulator (`Pus11Service.java`) and all example MDB/XML/XTCE files exist solely for **testing and demonstration**. Production concerns are limited to MDB/XTCE definitions (operator interface and TC encoding/TM decoding in YAMCS). Java simulator gaps are test/demo concerns; MDB/XTCE gaps directly affect the ground operator interface.

---

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

All files below are **test/demonstration** — not production MCS code.

| Layer | File | Purpose |
|-------|------|---------|
| Java simulator | `simulator/src/main/java/org/yamcs/simulator/pus/Pus11Service.java` | Demo/test only |
| XTCE MDB | `examples/pus/src/main/yamcs/mdb/pus11.xml` | Demo/test only |
| PUS base types | `examples/pus/src/main/yamcs/mdb/pus.xml` | Demo/test only |
| Data types | `examples/pus/src/main/yamcs/mdb/dt.xml` | Demo/test only |

### Time Encoding

CUC format, 8 bytes: `1B p-field + 4B coarse seconds + 3B fine sub-seconds`. Implemented in `PusTime.java`. Referenced in XTCE as `/PUS/PusTimeType`.

### XTCE / YAMCS Scheduling Note

The primary ground-side workflow uses `yamcs-client` with the `pus11ScheduleAt` command extra. YAMCS's `PusCommandPostprocessor` automatically wraps the command in a TC[11,4] packet. The MDB TC[11,4] definition is for operators who want to send INSERT_ACTIVITIES manually.

---

## B. TM/TC Implementation Plan

### Overall Status Table

> **Java column** = simulator (`Pus11Service.java`) — **test/demo code only**. MDB column reflects the YAMCS operator interface, which is the production-relevant concern.
>
> All subtypes are now implemented in both MDB and the Java simulator. Three items remain: Gap #1 (`filter_type` hardcoded to `0x01` on the four filter-based commands), Gap #2 (scheduling groups are bookkeeping-only — activities cannot be assigned to a group, so group enable/disable has no effect on release), and Gap #3 (the global `enabled` flag is written but never read, so `DISABLE_SCHEDULER` is inert).

| Subtype | Type | Name | MDB | Java (sim) | Action Required |
|---------|------|------|-----|------|-----------------|
| 1 | TC | Enable Scheduler | ✅ | ⚠️ | `enabled` never read (Gap #3) |
| 2 | TC | Disable Scheduler | ✅ | ⚠️ | `enabled` never read — command is inert (Gap #3) |
| 3 | TC | Reset Scheduler | ✅ | ⚠️ | queue clear works; `enabled = false` inert (Gap #3) |
| 4 | TC | Insert Activities | ✅ | ✅ | No `group_id` per activity (Gap #2) |
| 5 | TC | Delete by Request ID | ✅ | ✅ | None |
| 6 | TC | Delete by Filter | ✅ | ✅ | `filter_type` hardcoded (Gap #1) |
| 7 | TC | Time-shift by Request ID | ✅ | ✅ | None |
| 8 | TC | Time-shift by Filter | ✅ | ✅ | `filter_type` hardcoded (Gap #1) |
| 9 | TC | Detail Report by ID | ✅ | ✅ | None |
| 10 | TM | Detail Report | ✅ | ✅ | None |
| 11 | TC | Detail Report by Filter | ✅ | ✅ | `filter_type` hardcoded (Gap #1) |
| 12 | TC | Summary Report by ID | ✅ | ✅ | None |
| 13 | TM | Summary Report | ✅ | ✅ | None |
| 14 | TC | Summary Report by Filter | ✅ | ✅ | `filter_type` hardcoded (Gap #1) |
| 15 | TC | Time-shift All | ✅ | ✅ | None |
| 16 | TC | Detail Report All | ✅ | ✅ | None |
| 17 | TC | Summary Report All | ✅ | ✅ | None |
| 18 | TC | Report Subschedule Status | ✅ | ✅ | None |
| 19 | TM | Subschedule Status Report | ✅ | ✅ | None |
| 20 | TC | Enable Subschedules | ✅ | ✅ | None |
| 21 | TC | Disable Subschedules | ✅ | ✅ | None |
| 22 | TC | Create Scheduling Groups | ✅ | ✅ | Bookkeeping only (Gap #2) |
| 23 | TC | Delete Scheduling Groups | ✅ | ✅ | Bookkeeping only (Gap #2) |
| 24 | TC | Enable Scheduling Groups | ✅ | ✅ | Bookkeeping only (Gap #2) |
| 25 | TC | Disable Scheduling Groups | ✅ | ✅ | Bookkeeping only (Gap #2) |
| 26 | TC | Report Group Status | ✅ | ✅ | Bookkeeping only (Gap #2) |
| 27 | TM | Group Status Report | ✅ | ✅ | Bookkeeping only (Gap #2) |

---

### TC[11,1] — Enable the time-based schedule execution function

**Spec**: No application data. Enables the scheduler so stored commands are released at their times.
**MDB**: ✅ `ENABLE_SCHEDULER` defined, no args.
**Java**: ⚠️ Sets `enabled = true` (`Pus11Service.java:53`) and sends ACK[1,3] + ACK[1,7], but
the flag is never read — see Gap #3.
**Action**: None for the MDB. Simulator: make `runSchedule()` honour `enabled` (Gap #3).

---

### TC[11,2] — Disable the time-based schedule execution function

**Spec**: No application data. Sets the execution function status to "disabled". Per
§6.11.4.6.b.1 a scheduled activity is then *disabled*, and per §6.11.4.6.c the activity is
**still deleted from the schedule when its release time is reached** — it is simply not
released first. **Queued commands are therefore discarded as their release times pass, not
retained.** §6.11.4.6 NOTE 2 confirms no notification is sent to ground when this happens.
**MDB**: ✅ `DISABLE_SCHEDULER`, no args.
**Java**: ⚠️ Sets `enabled = false` (`Pus11Service.java:60`), but the flag is never read, so
this command currently has **no effect at all** — see Gap #3.
**Action**: None for the MDB. Simulator: Gap #3.

> **Operational consequence**: disabling the scheduler is *not* a safe "pause". It silently
> destroys every activity whose release time elapses while disabled. To genuinely retain
> activities, time-shift them (TC[11,15]) past the outage instead.

---

### TC[11,3] — Reset the time-based schedule

**Spec**: No application data. Clears all scheduled activities and disables the scheduler.
**MDB**: ✅ `RESET_SCHEDULER`, no args.
**Java**: ⚠️ Clears the `commands` queue (this part works) and sets `enabled = false`
(`Pus11Service.java:67`), which has no effect — see Gap #3.
**Action**: None for the MDB. Simulator: Gap #3.

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

**MDB**: ✅ `INSERT_ACTIVITIES` defined. `ActivityEntryType` aggregate = `{release_time (/PUS/PusTimeType), tc_packet (TcPacketType)}`, repeated via `ActivityArrayType` sized by argument `n`. `TcPacketType` is a `BinaryArgumentType` with no `SizeInBits` — `BinaryDataEncoding` defaults to `FIXED_SIZE` with `sizeInBits = -1`, so the encoder writes exactly the bytes supplied per entry, with no uniform-size constraint and no framing. Heterogeneous TC sizes are fully supported (see Gap history below and `pus21.md` §b/§e for the same technique).
**Java**: ✅ `insertActivities()` reads subschedule + N, then for each: reads `PusTime`, reads CCSDS packet (using length field at offset +4), schedules into priority queue. The subschedule is auto-created as enabled if unknown (`Pus11Service.java:126`).
**Action**: None for subschedule-level scheduling. **No `group_id` field** exists on the activity entry in either the MDB aggregate or `insertActivities()`, so an activity can never be a member of a scheduling group — see Gap #2.

**Production note**: `yamcs-client`'s `pus11ScheduleAt` extra remains the ergonomic path for scheduling any existing TC (see Section D) — the `INSERT_ACTIVITIES` MetaCommand above is for operators who want to build a multi-activity TC[11,4] manually.

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
**Action**: None for basic use. If all filter types are needed, the `filter_type` FixedValueEntry should be changed to an `ArgumentRefEntry` (Gap #1).

---

### TC[11,7] — Time-shift scheduled activities by request identifier

**Spec**:
```
time_offset    (relative time, int32 milliseconds)
N              (uint16)
repeat N:
  source_id / apid / seqcount
```

**MDB**: ✅ `TIME_SHIFT_ACTIVITIES_BY_ID` — `time_offset_ms (/dt/uint32)` is the first argument, followed by `num_requests` + `requests[]`.
**Java**: ✅ `timeShiftById()` reads `int timeShiftMillis = bb.getInt()` first, then calls `filterById`.
**Action**: None.

---

### TC[11,8] — Time-shift scheduled activities by filter

**Spec**:
```
time_offset    (int32 ms)
filter_type    (uint8)
[time tags]
N subschedule_ids
```

**MDB**: ✅ `TIME_SHIFT_ACTIVITIES_BY_FILTER` — `time_offset_ms (/dt/uint32)` is the first argument, followed by the filter fields (`filter_type` still hardcoded to `0x01`, see Gap #1).
**Java**: ✅ `timeShiftByFilter()` reads `int timeShiftMillis = bb.getInt()` first.
**Action**: None for basic use; see Gap #1 for `filter_type`.

---

### TC[11,9] — Detail-report activities by request identifier

**Spec**:
```
N              (uint16)
repeat N:
  source_id / apid / seqcount
```

**MDB**: ✅ `GET_DETAIL_REPORT_BY_ID`.
**Java**: ✅ `detailReportById()` → `filterById(bb, false)` → `sendDetailReport()`.
**Action**: None.

---

### TM[11,10] — Time-based schedule detail report

**Spec**:
```
N              (uint16)
repeat N:
  schedule_id  (uint8)
  release_time (PusTime, 8 bytes)
  tc_packet    (full embedded CCSDS TC, verbatim)
```

**MDB**: ✅ `DETAIL_REPORT` (in its own `DETAIL_REPORT` SpaceSystem for nicer parameter naming in YAMCS Web). `tc_data` is a `DetailReportTcPacketDataType` binary parameter sized dynamically from the embedded CCSDS `length` field (`LinearAdjustment slope="8" intercept="-48"`).
**Java**: ✅ `sendDetailReport()` batches entries up to `MAX_DETAIL_REPORT_SIZE` (1400 bytes) per TM packet and writes the full TC bytes per entry.
**Action**: None.

---

### TC[11,11] — Detail-report activities by filter

**Spec**: Same filter structure as TC[11,6].
**MDB**: ✅ `GET_DETAIL_REPORT_BY_FILTER`. Same `filter_type` hardcoded limitation as TC[11,6] (Gap #1).
**Java**: ✅ `detailReportByFilter()`.
**Action**: None for basic use.

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
**MDB**: ✅ `GET_SUMMARY_REPORT_BY_FILTER`. Same filter_type hardcoded limitation as TC[11,6] (Gap #1).
**Java**: ✅ `summaryReportByFilter()`.
**Action**: None for basic use.

---

### TC[11,15] — Time-shift all scheduled activities

**Spec**:
```
time_offset    (relative time, int32 milliseconds)
```
No other arguments — applies the offset to every scheduled activity.

**MDB**: ✅ `TIME_SHIFT_ACTIVITIES` has exactly one argument, `time_offset_ms (/dt/uint32)` — matches the spec.
**Java**: ✅ `timeShiftAll()` correctly reads only `int timeShiftMillis = bb.getInt()` and iterates all commands.
**Action**: None.

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
**Java**: ✅ `enableSubschedule()` reads `n` then loops `n` times over `subschStatus.put(subschedule, true)`.
**Action**: None.

---

### TC[11,21] — Disable time-based sub-schedules

**Spec**: Same structure as TC[11,20].
**MDB**: ✅ `DISABLE_SCHEDULE`.
**Java**: ✅ `disableSubschedule()` — same loop pattern as TC[11,20].
**Action**: None.

---

### TC[11,22] — Create time-based scheduling groups

**Spec**:
```
N              (uint8)
repeat N:
  group_id     (uint8)
  group_status (uint8, 0=disabled 1=enabled)
```

**MDB**: ✅ `CREATE_SCHEDULING_GROUPS` — `num_groups (uint8)` + `groups[]` array of `{group_id, group_status}`.
**Java**: ✅ `createGroups()` reads N, then for each `{groupId, enabled}` pair populates `groupStatus`.
**Action**: None for the command itself. `groupStatus` is never consulted when releasing — see Gap #2.

---

### TC[11,23] — Delete time-based scheduling groups

**Spec**:
```
N              (uint8)
repeat N:
  group_id     (uint8)
```

**MDB**: ✅ `DELETE_SCHEDULING_GROUPS` — `num_groups (uint8)` + `GroupIdArrayType` array of uint8 group IDs.
**Java**: ✅ `deleteGroups()` reads N group IDs, calls `groupStatus.remove(groupId)` for each.
**Action**: None.

---

### TC[11,24] — Enable time-based scheduling groups

**Spec**: Same structure as TC[11,23].
**MDB**: ✅ `ENABLE_SCHEDULING_GROUPS`.
**Java**: ✅ `enableGroups()` sets `groupStatus.put(groupId, true)` for each ID.
**Action**: None for the command itself; enabling has no effect on release (Gap #2).

---

### TC[11,25] — Disable time-based scheduling groups

**Spec**: Same structure as TC[11,23].
**MDB**: ✅ `DISABLE_SCHEDULING_GROUPS`.
**Java**: ✅ `disableGroups()` sets `groupStatus.put(groupId, false)` for each ID.
**Action**: None for the command itself; **disabling does not suppress any release** (Gap #2) — unlike `DISABLE_SCHEDULE` (TC[11,21]), which does.

---

### TC[11,26] — Report the status of each time-based scheduling group

**Spec**: No application data. Responds with TM[11,27].
**MDB**: ✅ `REPORT_GROUP_STATUS`, no args.
**Java**: ✅ `groupStatusReport()` emits TM[11,27] with count + `{group_id, group_status}` entries.
**Action**: None.

---

### TM[11,27] — Time-based scheduling group status report

**Spec**:
```
N              (uint32)
repeat N:
  group_id     (uint8)
  group_status (uint8, 0=disabled 1=enabled)
```

**MDB**: ✅ `GROUP_STATUS_REPORT` with `GroupStatusReportType` array, mirroring `SUBSCHEDULE_STATUS_REPORT`'s pattern.
**Java**: ✅ Written by `groupStatusReport()`.
**Action**: None.

---

## C. Gaps & Shortcomings

> Previous revisions of this doc tracked five other gaps (missing `INSERT_ACTIVITIES` MDB definition, missing `time_offset_ms` on TC[11,7]/[11,8], wrong argument list on TC[11,15], unimplemented scheduling groups TC[11,22–26]/TM[11,27], and a single-ID-only bug in the simulator's TC[11,20]/[11,21] handlers). All five are now resolved in both the MDB (`pus11.xml`) and the Java simulator (`Pus11Service.java`) — see the per-subtype sections in Part B for details. Three gaps remain open.
>
> **Scheduling capability summary**: sub-scheduling works end to end (assign → gate at release → report). Group scheduling does **not** — the group commands maintain state that nothing acts on (Gap #2). Neither does the global enable/disable — same failure shape (Gap #3). Of the three release gates §6.11.4.6.b requires, the simulator implements exactly one.

### Gap 1 — TC[11,6/8/11/14]: `filter_type` hardcoded to `0x01`

**Problem**: The MDB for all four filter-based commands uses `<FixedValueEntry binaryValue="01" sizeInBits="8" name="filter_type"/>` which always sends `type=1` (from-to time window). Operators cannot select other filter types (select-all, from-time, to-time) from YAMCS Web.

**Impact**: Only time-range based filtering is accessible via UI. Select-all (type=0) is most common and is not reachable.

**Fix**: Change `FixedValueEntry` to `ArgumentRefEntry` with an enumerated `FilterTypeType` argument, on `DELETE_ACTIVITIES_BY_FILTER` (TC[11,6]), `TIME_SHIFT_ACTIVITIES_BY_FILTER` (TC[11,8]), `GET_DETAIL_REPORT_BY_FILTER` (TC[11,11]), and `GET_SUMMARY_REPORT_BY_FILTER` (TC[11,14]).

**Scope**: MCS (operator interface). The Java simulator already handles all 4 filter types correctly (`filterByFilter()`); only the MDB restricts what an operator can send.

**Effort**: Minor — one new enumerated argument type, four `EntryList` edits.

---

### Gap 2 — Scheduling groups are bookkeeping-only; activities cannot join a group

**Problem**: Three things are missing for group scheduling to actually function:

1. **No membership.** `INSERT_ACTIVITIES` (TC[11,4]) carries only `subschedule_id`. The `ActivityEntryType` aggregate in `pus11.xml` is `{release_time, tc_packet}` — no `group_id` member — and `Pus11Service.insertActivities()` never reads one. `ScheduledCommand` (`Pus11Service.java:573–583`) has fields `releaseTime`, `subschedule`, `tc` and no group field. Every scheduled activity is therefore group-less.
2. **No gate at release.** `runSchedule()` (`Pus11Service.java:531–559`) checks `subschStatus` only. `groupStatus` is never read there, so `DISABLE_SCHEDULING_GROUPS` (TC[11,25]) suppresses nothing.
3. **No group in filters.** `filterByFilter()` (`Pus11Service.java:478–495`) matches on a `BitSet` of subschedule IDs only; there is no group-based selection for the `*_BY_FILTER` commands.

Net effect: TC[11,22–26] and TM[11,27] faithfully maintain a `Map<Integer, Boolean> groupStatus` that no other code path consumes. Group commands ACK and the status report is accurate, but toggling a group has zero observable effect on scheduling.

**Impact**: Medium for a full PUS-C ST[11] implementation, low for the current test/demo scope. An operator can create/enable/disable/report groups and see consistent TM[11,27], which reads as working — the behaviour is silently inert. Sub-scheduling (the primary grouping mechanism) is unaffected and works correctly.

**Fix**:
1. Add a `group_id (uint8)` member to `ActivityEntryType` in `pus11.xml` (ahead of `release_time`, per PUS-C's activity-entry layout).
2. Read it in `insertActivities()`; add a `group` field to `ScheduledCommand` and populate it. Auto-create the group as enabled if unknown, mirroring the subschedule behaviour at `Pus11Service.java:126`.
3. Add a `groupStatus.getOrDefault(cmd.group, true)` check alongside the existing `subschStatus` check in `runSchedule()`.
4. Optional, for full spec coverage: extend `filterByFilter()` with group-based selection.

**Scope**: Both — MDB (new `group_id` argument, operator-visible) **and** Java simulator (membership + release gate). Not XTCE-only.

**Effort**: Moderate — ~2–3 hours across `pus11.xml` and `Pus11Service.java`, plus a walkthrough step to verify a disabled group blocks a release.

---

---

### Gap 3 — TC[11,1]/[11,2]/[11,3]: the `enabled` flag is written but never read

**Problem**: `Pus11Service.enabled` is assigned at lines 29, 53 (TC[11,1]), 60 (TC[11,2]) and
67 (TC[11,3]), and **read nowhere**. `runSchedule()` (lines 531–559) gates only on
`subschStatus`. So `DISABLE_SCHEDULER` has no observable effect: activities continue to be
released on schedule while the scheduler reports itself disabled.

This is the same failure shape as Gap #2. §6.11.4.6.b requires three independent release
gates — function status, sub-schedule, group — and the simulator implements only the
sub-schedule one.

**Impact**: Medium. TC[11,2] ACKs successfully and the state is tracked internally, so it
reads as working while doing nothing — the same silent-inertness trap as the group commands.
Anyone using `DISABLE_SCHEDULER` as a safing action in a test scenario gets no safing.

**Fix**: add the function-status check to `runSchedule()`, alongside the existing
`subschStatus` check and the `groupStatus` check from Gap #2:

```java
if (!enabled) { /* disabled: fall through to the unconditional delete, do not release */ }
```

Note the correct behaviour is **not** "skip and keep the activity". Per §6.11.4.6.c the
activity is deleted when its release time is reached regardless of whether it was released —
so a disabled scheduler discards activities as their times pass. The existing
`commands.remove()` in the drop branches is already right; only the gate is missing.

**Scope**: Java simulator only. The MDB is correct.

**Effort**: Minor — one condition, plus a walkthrough step to verify a disabled scheduler
blocks a release *and* still drops the activity.

---

### Summary

| Gap | Severity | Scope | XTCE-only fix? | Effort |
|-----|----------|-------|----------------|--------|
| #1 TC[11,6/8/11/14] filter_type hardcoded | Low | MCS (operator interface) | ✅ Yes | Minor |
| #2 Scheduling groups bookkeeping-only (no `group_id` on activities, no release gate) | Medium | MDB + Java simulator | ❌ No | Moderate |
| #3 `enabled` flag never read — TC[11,2] inert | Medium | Java simulator | ❌ No | Minor |

---

## D. Native MCS Implementation — Java vs XTCE-only

### Verdict: XTCE-only (with one built-in exception)

ST[11] is **XTCE-only on the ground side**. No `Pus11Service.java` exists in `yamcs-core` and none is required for normal MCS operation. This is the opposite of ST[05], where `PusEventDecoder` is mandatory because TM[5,1–4] must be promoted to YAMCS native events — a conversion that cannot be expressed in XTCE.

For ST[11]:
- All **TC sends** are encoded as XTCE MetaCommands + `PusCommandPostprocessor`
- All **TM receives** (TM[11,10], TM[11,13], TM[11,19], TM[11,27]) are ordinary XTCE parameter containers — no native event emission needed

The one "Java" piece is `PusCommandPostprocessor.buildScheduledTc()` in `yamcs-core/src/main/java/org/yamcs/pus/PusCommandPostprocessor.java` (lines 128–190), but that code already exists and requires no new implementation. It handles the `pus11ScheduleAt` command extra transparently.

---

### Per-message table

| Message | MCS Role | XTCE Sufficient? | Java Required? | Notes |
|---------|----------|-----------------|----------------|-------|
| TC[11,1] | Send | **Yes** | No | `ENABLE_SCHEDULER`, no args |
| TC[11,2] | Send | **Yes** | No | `DISABLE_SCHEDULER`, no args |
| TC[11,3] | Send | **Yes** | No | `RESET_SCHEDULER`, no args |
| TC[11,4] | Send | **Yes** | No | `INSERT_ACTIVITIES` fully expressible via unbounded `BinaryArgumentType` per entry; `pus11ScheduleAt` extra remains the ergonomic production path |
| TC[11,5] | Send | **Yes** | No | Array of `{source_id, apid, seqcount}` |
| TC[11,6] | Send | **Yes** | No | filter_type hardcoded to 0x01 (Gap #1) |
| TC[11,7] | Send | **Yes** | No | `time_offset_ms` present |
| TC[11,8] | Send | **Yes** | No | `time_offset_ms` present; filter_type hardcoded (Gap #1) |
| TC[11,9] | Send | **Yes** | No | Array of request IDs |
| TC[11,11] | Send | **Yes** | No | filter_type hardcoded to 0x01 (Gap #1) |
| TC[11,12] | Send | **Yes** | No | Array of request IDs |
| TC[11,14] | Send | **Yes** | No | Same as TC[11,6] |
| TC[11,15] | Send | **Yes** | No | Single `time_offset_ms` arg, matches spec |
| TC[11,16] | Send | **Yes** | No | No args |
| TC[11,17] | Send | **Yes** | No | No args |
| TC[11,18] | Send | **Yes** | No | No args |
| TC[11,20] | Send | **Yes** | No | Array of subschedule IDs |
| TC[11,21] | Send | **Yes** | No | Same as TC[11,20] |
| TC[11,22–25] | Send | **Yes** | No | Array of group IDs / `{group_id, group_status}`. XTCE is sufficient to *send* these, but group membership + release gating need simulator/on-board changes (Gap #2) |
| TC[11,26] | Send | **Yes** | No | No args |
| TM[11,10] | Receive | **Yes** | No | XTCE container with dynamically-sized embedded TC binary |
| TM[11,13] | Receive | **Yes** | No | XTCE container with dynamic array of summary entries |
| TM[11,19] | Receive | **Yes** | No | XTCE container with `{id, status}` array |
| TM[11,27] | Receive | **Yes** | No | XTCE container with `{group_id, group_status}` array |

---

### `pus11ScheduleAt` command extra (already implemented)

`org.yamcs.pus.PusCommandPostprocessor` registers the `pus11ScheduleAt` command option at class load time:

```java
public static final CommandOption OPTION_SCHEDULE_TIME = new CommandOption(
    "pus11ScheduleAt", "Schedule Time", CommandOptionType.TIMESTAMP);
```

When a TC is issued with this attribute set, `buildScheduledTc()` wraps the encoded TC binary inside a TC[11,4] packet:
- Writes `subschedule_id=1`, `N=1`
- Encodes the CUC release time
- Appends the original TC binary verbatim
- Fills CCSDS sequence count and optional CRC

This means **operators can schedule any existing TC from YAMCS Web** by setting the Schedule Time option. No MDB change or new Java is needed for this path.

Config in `yamcs.*.yaml` (tc_realtime data link):
```yaml
commandPostprocessorClassName: org.yamcs.pus.PusCommandPostprocessor
commandPostprocessorArgs:
    errorDetection:
        type: CRC-16-CCIIT
    timeEncoding:
        implicitPfield: false
        pfield: 0x2f
    pus11Crc: true        # optional, default true — add CRC to TC[11,4]
    pus11Apid: 1          # optional — override APID on the TC[11,4] wrapper
    tcoService: tco0
```

---

### Contrast with ST[05]

| | ST[05] | ST[11] |
|--|--------|--------|
| Native Java needed? | **Yes** — `PusEventDecoder` | **No** |
| Why Java for TM? | TM[5,1–4] must be promoted to YAMCS native events (events stream) — no XTCE mechanism exists for this | TM[11,10/13/19/27] are parameter containers — decoded by XTCE directly |
| Existing Java in yamcs-core | `Pus5Service`, `PusEventDecoder` | `PusCommandPostprocessor.buildScheduledTc()` (already present) |
| XTCE for TC? | Yes (TC[5,5/6/7]) | Yes (all subtypes) |
| XTCE for TM? | Partial — XTCE decodes params, but Java needed for event emission | Full — XTCE decodes all TM packets |

---

### When would a `Pus11Service` in yamcs-core be needed?

Only if you run the on-board scheduler **inside the YAMCS process** (HITL or a YAMCS-as-spacecraft scenario). In that case:

1. Create `yamcs-core/src/main/java/org/yamcs/pus/Pus11Service.java` extending `PusTcHandler`
2. Register under `PusCommandReleaser` with `serviceType: 11`
3. Implement `handleTc(PreparedCommand)` dispatching to subtypes 1–26

This mirrors `Pus11Service.java` in `simulator/` exactly, but uses `emitTm(serviceType, subtype, appData)` from `PusCommandReleaser` instead of `pusSimulator.transmitRealtimeTM(pkt)`.

For a ground-only MCS, no such service is needed.

---

## E. Manual Testing

Reflects the actual implementation: `Pus11Service.java` and `examples/pus/src/main/yamcs/mdb/pus11.xml`.
Command paths, argument names, and byte layouts below are taken directly from those files.

### E.1 Start the instance

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests   # first build only
mvn -pl examples/pus yamcs:run
```
Web UI: `http://localhost:8090`, instance `pus`. Commands live under `/PUS11/...`. TM containers
are also under `/PUS11/...`, except TM[11,10] and TM[11,13] which live one level deeper — see E.3.

### E.2 Command reference — valid inputs

All commands are under `/PUS11/`. The `n`/`num_requests`/`num_schedules`/`num_groups` count fields
must be supplied explicitly alongside their corresponding arrays — YAMCS does not infer them from
array length (same convention as PUS12/PUS14's `N` fields).

| Command | Subtype | Valid example args |
|---|---|---|
| `ENABLE_SCHEDULER` | TC[11,1] | `{}` (no arguments) |
| `DISABLE_SCHEDULER` | TC[11,2] | `{}` (no arguments) |
| `RESET_SCHEDULER` | TC[11,3] | `{}` (no arguments) — clears the queue and disables the scheduler |
| `INSERT_ACTIVITIES` | TC[11,4] | `{"subschedule_id": 1, "n": 1, "activities": [{"release_time": "<a few seconds in the future>", "tc_packet": "<hex bytes of an already-encoded TC — see E.2.1>"}]}` |
| `DELETE_ACTIVITIES_BY_ID` | TC[11,5] | `{"num_requests": 1, "requests": [{"source_id": 0, "apid": 1, "seqcount": <seq of the embedded TC>}]}` |
| `DELETE_ACTIVITIES_BY_FILTER` | TC[11,6] | `{"start_time": "<past>", "end_time": "<far future>", "num_schedules": 0, "schedules": []}` — `filter_type` is fixed at `0x01` (from–to) in the MDB (Gap #1), so this always matches by time window only; `num_schedules=0` means "any subschedule" |
| `TIME_SHIFT_ACTIVITIES_BY_ID` | TC[11,7] | `{"time_offset_ms": 5000, "num_requests": 1, "requests": [{"source_id": 0, "apid": 1, "seqcount": <seq>}]}` — positive shift only, see E.5 |
| `TIME_SHIFT_ACTIVITIES_BY_FILTER` | TC[11,8] | `{"time_offset_ms": 5000, "start_time": "<past>", "end_time": "<far future>", "num_schedules": 0, "schedules": []}` |
| `GET_DETAIL_REPORT_BY_ID` | TC[11,9] | `{"num_requests": 1, "requests": [{"source_id": 0, "apid": 1, "seqcount": <seq>}]}` |
| `GET_DETAIL_REPORT_BY_FILTER` | TC[11,11] | `{"start_time": "<past>", "end_time": "<far future>", "num_schedules": 0, "schedules": []}` |
| `GET_SUMMARY_REPORT_BY_ID` | TC[11,12] | `{"num_requests": 1, "requests": [{"source_id": 0, "apid": 1, "seqcount": <seq>}]}` |
| `GET_SUMMARY_REPORT_BY_FILTER` | TC[11,14] | `{"start_time": "<past>", "end_time": "<far future>", "num_schedules": 0, "schedules": []}` |
| `TIME_SHIFT_ACTIVITIES` | TC[11,15] | `{"time_offset_ms": 5000}` — shifts every queued activity |
| `GET_DETAIL_REPORT` | TC[11,16] | `{}` (no arguments) — reports all queued activities |
| `GET_SUMMARY_REPORT` | TC[11,17] | `{}` (no arguments) |
| `GET_SCHEDULE_STATUS` | TC[11,18] | `{}` (no arguments) |
| `ENABLE_SCHEDULE` | TC[11,20] | `{"num_schedules": 1, "schedules": [1]}` |
| `DISABLE_SCHEDULE` | TC[11,21] | `{"num_schedules": 1, "schedules": [1]}` |
| `CREATE_SCHEDULING_GROUPS` | TC[11,22] | `{"num_groups": 1, "groups": [{"group_id": 1, "group_status": 1}]}` |
| `DELETE_SCHEDULING_GROUPS` | TC[11,23] | `{"num_groups": 1, "group_ids": [1]}` |
| `ENABLE_SCHEDULING_GROUPS` | TC[11,24] | `{"num_groups": 1, "group_ids": [1]}` |
| `DISABLE_SCHEDULING_GROUPS` | TC[11,25] | `{"num_groups": 1, "group_ids": [1]}` |
| `REPORT_GROUP_STATUS` | TC[11,26] | `{}` (no arguments) |

Rejection conditions to exercise: `INSERT_ACTIVITIES` with a `release_time` in the past responds
with **NACK completion**, code `COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST` (`insertActivities` rejects the
whole batch as soon as one entry fails the check — see E.4 step 8). An unrecognized subtype (e.g.
hand-crafting TC[11,10] or TC[11,13], which are TM-only) gets **NACK start**
(`START_ERR_INVALID_PUS_SUBTYPE`) since `Pus11Service.executeTc`'s `switch` has no case for it.
Every other command in the table above unconditionally ACKs — the simulator does no further
validation (no "unknown request ID" or "unknown subschedule/group ID" rejection anywhere in ST[11]).

#### E.2.1 Building the embedded `tc_packet` bytes for TC[11,4]

`tc_packet` must be a complete, already-encoded CCSDS/PUS TC packet (same convention as ST[19]'s
embedded `request` — see `pus19.md`). The simplest command to embed is `/PUS17/ARE_YOU_ALIVE`
(`TC[17,1]`, zero arguments, responds with `TM[17,2]`) since its effect is trivially observable.
Preferred way to get real, correctly-encoded bytes — a dry-run command issue via `yamcs-client`:

```python
from yamcs.client import YamcsClient

client = YamcsClient("localhost:8090")
processor = client.get_processor("pus", "realtime")
issued = processor.issue_command("/PUS17/ARE_YOU_ALIVE", args={}, dry_run=True)
tc_packet_bytes = issued.binary   # complete, CRC'd raw TC packet, ready to embed
print(len(tc_packet_bytes), issued.hex, issued.generation_time)
```

`dry_run=True` prepares and encodes the command without transmitting it, so `issued.binary` is safe
to reuse as `tc_packet` in `INSERT_ACTIVITIES`. Note the `seqcount` YAMCS assigns it (visible on the
`PreparedCommand`/via `StringConverter.arrayToHexString` in the simulator log when it's released) —
that value is what you'll need for `DELETE_ACTIVITIES_BY_ID`/`TIME_SHIFT_ACTIVITIES_BY_ID`/
`GET_*_REPORT_BY_ID`'s `seqcount` argument, since request IDs are `(source_id, apid, seqcount)` of
the *embedded* TC, not of the outer `INSERT_ACTIVITIES` command.

For a purely manual/offline reference, an `ARE_YOU_ALIVE` packet targeting `MAIN_APID=1` with the
default `ackflags=7` looks like this (13 bytes; CRC zeroed since `pusSimulator.processTc` re-dispatches
it exactly like an externally-uplinked TC when released, so the CRC **is** checked at release time —
unlike ST[19]'s bypass, see `pus19.md`):

```
1801 C000 0006 27 11 01 0000 0000
```
Use a real dry-run instead of hand-computing the CRC for anything beyond a smoke test.

### E.3 TMs to check

| Container | Subtype | Triggered by | Layout |
|---|---|---|---|
| `/PUS11/SUBSCHEDULE_STATUS_REPORT` | TM[11,19] | `GET_SCHEDULE_STATUS` | `status_report_n:u32`, then `status_report_n` × `{schedule_id:u8, schedule_status:u8}` (0=disabled/1=enabled) |
| `/PUS11/GROUP_STATUS_REPORT` | TM[11,27] | `REPORT_GROUP_STATUS` | `group_report_n:u32`, then `group_report_n` × `{group_id:u8, group_status:u8}` |
| `/PUS11/DETAIL_REPORT/DETAIL_REPORT` | TM[11,10] | `GET_DETAIL_REPORT`, `GET_DETAIL_REPORT_BY_ID`, `GET_DETAIL_REPORT_BY_FILTER` | `n:u16`, then `n` × `{schedule_id:u8, release_time:PusTime(8B), <embedded TC packet verbatim>}` — nested one `SpaceSystem` level down for a cleaner display name in YAMCS Web (see the XML comment) |
| `/PUS11/SUMMARY_REPORT/SUMMARY_REPORT` | TM[11,13] | `GET_SUMMARY_REPORT`, `GET_SUMMARY_REPORT_BY_ID`, `GET_SUMMARY_REPORT_BY_FILTER` | `n:u16`, then `n` × `{schedule_id:u8, release_time:PusTime(8B), source:u16, apid:u16, seq:u16}` — also nested one level down |

Large detail reports are split across multiple TM[11,10] packets if the encoded size would exceed
`MAX_DETAIL_REPORT_SIZE` (1400 bytes) — `sendDetailReport()` batches entries and emits one packet per
batch, so expect >1 `/PUS11/DETAIL_REPORT/DETAIL_REPORT` packet if you've queued many activities.

Also watch the standard PUS-1 verification containers (`/PUS/pus-tc-ack-*`) for ACK/NACK
start/completion of every TC[11,x] you send, **and** for the embedded command's own ACK/NACK when it
is autonomously released at its `release_time` — that second ACK/completion pair, with no
corresponding TC[11,x] in the command history at that timestamp, is the tell that the release
actually happened on-board rather than being sent from ground (same pattern as ST[19], see
`pus19.md`).

### E.4 Suggested manual test walkthrough

1. **Baseline**: `ENABLE_SCHEDULER`, confirm ACK completion, then `GET_SCHEDULE_STATUS` →
   `/PUS11/SUBSCHEDULE_STATUS_REPORT` reports `status_report_n=0` (no subschedules exist until an
   activity is inserted into one).
2. **Build and insert an activity**: dry-run `/PUS17/ARE_YOU_ALIVE` per E.2.1 to get `tc_packet` and
   note its `seqcount`. Send `INSERT_ACTIVITIES` with `subschedule_id=1`, `release_time` ~10s in the
   future. Confirm ACK completion. `GET_SCHEDULE_STATUS` now shows subschedule `1` with
   `schedule_status=1` (`insertActivities` auto-creates the subschedule as enabled — §6.19's
   equivalent behaviour for ST[11]).
3. **Verify the summary and detail reports**: `GET_SUMMARY_REPORT` → `/PUS11/SUMMARY_REPORT/SUMMARY_REPORT`
   shows one entry with `schedule_id=1` and the request-ID fields matching the embedded TC.
   `GET_DETAIL_REPORT` → `/PUS11/DETAIL_REPORT/DETAIL_REPORT` shows the same entry, but with the full
   embedded TC bytes instead of just the request ID — byte-for-byte compare against `tc_packet_bytes`
   from E.2.1.
4. **Confirm autonomous release**: wait for `release_time` to pass. Confirm a second ACK
   start/completion pair appears for `TC[17,1]` (the embedded `ARE_YOU_ALIVE`) with no matching
   TC[17,1] ground command, and `TM[17,2]` is emitted — the core insert→release chain working
   end to end. `GET_SUMMARY_REPORT` now returns `n=0` (the released activity is removed from the
   queue).
5. **Subschedule gate**: insert a second activity (repeat step 2, new dry-run so you get a fresh
   `seqcount`), then `DISABLE_SCHEDULE` with `{"num_schedules": 1, "schedules": [1]}`. Wait past its
   `release_time` and confirm the command is **not** released and produces no ACK — `runSchedule()`
   silently drops it because `subschStatus.get(1) == false` (check the simulator log for "Dropping
   command ... subschedule ... is disabled"). `GET_SUMMARY_REPORT` confirms it's gone from the queue.
6. **Re-enable and time-shift by ID**: insert a third activity ~30s out, `ENABLE_SCHEDULE` for
   subschedule 1, then immediately `TIME_SHIFT_ACTIVITIES_BY_ID` targeting its `(source_id=0, apid=1,
   seqcount=<its seq>)` with `time_offset_ms=60000`. Confirm ACK completion, then `GET_SUMMARY_REPORT`
   shows the `release_time` moved 60s later.
7. **Time-shift by filter and time-shift all**: insert two more activities into subschedule 1 at
   different times. `TIME_SHIFT_ACTIVITIES_BY_FILTER` with a `start_time`/`end_time` window covering
   only one of them and confirm only that one's `release_time` moves (`GET_SUMMARY_REPORT` before/after
   compare). Then `TIME_SHIFT_ACTIVITIES` (subtype 15, all-activities) and confirm every remaining
   queued entry's `release_time` shifts.
8. **Reject a past release time**: `INSERT_ACTIVITIES` with `release_time` set to a timestamp already
   in the past. Confirm **NACK completion** with code matching `COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST`,
   and that nothing was added to the queue (`GET_SUMMARY_REPORT` count unchanged).
9. **Delete by ID and by filter**: insert two activities, `DELETE_ACTIVITIES_BY_ID` for one specific
   request ID (confirm `GET_SUMMARY_REPORT` count drops by exactly one), then `DELETE_ACTIVITIES_BY_FILTER`
   with `num_schedules=0` (any subschedule) and a wide time window (confirm the rest are removed too).
10. **Scheduling groups lifecycle**: `CREATE_SCHEDULING_GROUPS` with `{"group_id": 1, "group_status": 1}`,
    then `REPORT_GROUP_STATUS` → `/PUS11/GROUP_STATUS_REPORT` shows `group_id=1, group_status=1`.
    `DISABLE_SCHEDULING_GROUPS` for group 1, re-check the report shows `group_status=0`, then
    `ENABLE_SCHEDULING_GROUPS` to flip it back, then `DELETE_SCHEDULING_GROUPS` and confirm the entry
    is gone from the next `REPORT_GROUP_STATUS`. Note groups are bookkeeping-only in this simulator —
    `runSchedule()` never consults `groupStatus`, only `subschStatus` (step 5), so releasing an
    activity while its group is disabled will **not** block it; only the subschedule gate does.
11. **Reset**: `RESET_SCHEDULER` and confirm `GET_SUMMARY_REPORT`/`GET_DETAIL_REPORT` both return
    `n=0`. Note that per spec a subsequent `ENABLE_SCHEDULER` *should* be required before newly
    inserted activities release, but in this simulator it is **not** — `enabled` is never read
    (Gap #3), so activities inserted after a reset release regardless. Insert one and confirm it
    still fires; that is the reproducer for Gap #3.

### E.5 Caveats specific to this simulator

- **Single fixed APID**: every TC and TM in this simulator uses `MAIN_APID = 1`
  (`PusSimulator.newPacket`), so request-ID `apid` fields in test data should always be `1` — there is
  no second process to exercise an APID mismatch against (same caveat noted for ST[14]/ST[19]).
- **`release_time` must be a genuine future timestamp**: unlike ST[19] where the stored `request`
  bytes are never re-validated, `insertActivities()` actively checks `releaseTime.isBefore(now)`
  against `pusSimulator.timeEncoding.now()` and NACKs the whole batch if any entry fails — pick a
  `release_time` comfortably ahead of the instance's current time (visible in YAMCS Web), not a fixed
  hardcoded timestamp, since real elapsed wall-clock time between building the command and sending it
  varies.
- **`time_offset_ms` cannot go negative through this MDB**: the argument type is `/dt/uint32`
  (unsigned), even though PUS-C allows a negative offset (shift earlier) and `Pus11Service` happily
  applies a negative `int` via `bb.getInt()` if one arrived. Negative shifts are exercisable only by
  hand-crafting the TC bytes outside YAMCS Web/`yamcs-client`'s argument validation — not a blocker
  for forward-shift testing, but worth knowing before assuming a "negative shift" test case is
  reachable from the MDB as-is.
- **`filter_type` is fixed at `0x01`**: every `*_BY_FILTER` command in the table above can only be
  tested as a from–to time window (Gap #1) — select-all (`type=0`), from-only (`type=2`), and to-only
  (`type=3`) are exercisable in the simulator (`filterByFilter()` handles all four) but not reachable
  through the current MDB without hand-crafting bytes.
- **Nested `SpaceSystem` paths for TM[11,10]/TM[11,13]**: both containers live one level below
  `/PUS11` (`/PUS11/DETAIL_REPORT/DETAIL_REPORT` and `/PUS11/SUMMARY_REPORT/SUMMARY_REPORT`) — easy to
  miss if you're tab-completing container names expecting a flat `/PUS11/...` layout like the other
  TM containers in this service.
- **A disabled scheduler still destroys activities** (spec behaviour, not a simulator quirk):
  §6.11.4.6.c deletes each activity when its release time is reached whether or not it was
  released, and §6.11.4.6 NOTE 2 states no notification is sent to ground for those deletions.
  So `DISABLE_SCHEDULER` is not a safe pause — on real hardware it silently discards everything
  whose time elapses while disabled. (In *this* simulator it does nothing at all, per Gap #3, so
  the destructive behaviour is not reproducible here — do not conclude from a simulator run that
  the flight software is equally harmless.)
- **`enabled` is never read** (Gap #3): `ENABLE_SCHEDULER`/`DISABLE_SCHEDULER` ACK and update
  internal state, but `runSchedule()` gates only on `subschStatus`. Only the subschedule gate
  (walkthrough step 5) actually suppresses a release.
- **Groups are bookkeeping-only** (Gap #2): `groupStatus` is maintained faithfully by
  TC[11,22–26]/TM[11,27] but never consulted by `runSchedule()`, and there is no `group_id` on an
  activity in the first place — `INSERT_ACTIVITIES` takes only `subschedule_id` — so no scheduled
  command is a member of any group. See walkthrough step 10. Don't expect a disabled group to
  suppress a release; only `DISABLE_SCHEDULE` (subschedule-level) does that.
