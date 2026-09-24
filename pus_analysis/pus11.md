# PUS Service 11 — Time-based Scheduling

Reference: ECSS-E-ST-70-41C (15 April 2016) — §6.11 (requirements), §8.11 (packet layouts).

## A. Context

### Scope

This codebase is a **ground MCS**. The spacecraft (and its on-board scheduler) is on the other end of the link.

| Area | Files | Status |
|---|---|---|
| **Native (production)** | `yamcs-core/.../pus/PusCommandPostprocessor.java`, `PusCommandPostprocessorTest.java`, `docs/server-manual/links/command-postprocessor/pus.rst` | Production — correctness matters (Part B) |
| PUS-1 verifiers | `examples/pus/src/main/yamcs/mdb/pus.xml` (lines 260–350) | Example, but the pattern real missions copy (Gap #5) |
| MDB | `examples/pus/src/main/yamcs/mdb/pus11.xml` | Demo only |
| Simulator | `simulator/.../pus/Pus11Service.java` | Demo only — stands in for the on-board scheduler |

§8.11 gives field **types** only (enumerated, unsigned integer, absolute/relative time, TC packet).
All byte widths in this doc are mission choices of this codebase, not spec values.

### Key concepts

| Concept | Meaning (spec ref) |
|---|---|
| Scheduled activity | Request + release time + optional sub-schedule ID + optional group ID (§6.11.4.2.a) |
| Activity identifier | `(source_id, apid, seqcount)` of the **embedded** request, not of the TC[11,4] carrying it (§6.11.4.2.b) |
| Sub-schedule | Optional. Auto-created **disabled** on first insert, auto-deleted when empty (§6.11.1.2, §6.11.4.5.j) |
| Group | Optional, **independent** of sub-schedules. Created/deleted explicitly, may exist empty (§6.11.1.2, §6.11.6) |
| Time window type | `0` select all, `1` from–to, `2` from, `3` to; bounds inclusive (Table 8-5, §6.11.10.2.2) |
| Time margin | Insert/time-shift rejected if release time < now + margin (§6.11.4.2.d) |

### Release logic (§6.11.4.6)

An activity is **disabled** if any of these is disabled: the execution function (TC[11,1]/[11,2]), its
sub-schedule, its group. When its release time is reached it is released only if not disabled, and
**deleted either way**. No notification is sent for unreleased deletions (NOTE 2).

> **Operational consequence**: TC[11,2] is not a safe pause. It silently discards every activity whose
> release time passes while disabled. To retain activities across an outage, time-shift them (TC[11,15]).

---

## B. Native implementation — `pus11ScheduleAt`

ST[11] needs **no native service on the ground**. All TCs are XTCE MetaCommands, all TMs
(TM[11,10/13/19/27]) are plain XTCE containers. Contrast ST[05], where `PusEventDecoder` must turn TM
into Yamcs events. A `Pus11Service` in yamcs-core would only be needed if Yamcs itself hosted the
on-board scheduler (HITL / Yamcs-as-spacecraft).

The one native ST[11] feature is the `pus11ScheduleAt` command option in `PusCommandPostprocessor`,
which lets an operator time-tag **any** existing TC from Yamcs Web or a client.

### How it works

1. Both options are registered globally in a static initializer (`:50–53`): `pus11ScheduleAt`
   (timestamp) and `pus11SubscheduleId` (number).
2. `process()` finalises the inner TC as usual: length, seqcount (published as `ccsds-seqcount`), CRC,
   `binary`.
3. If `pus11ScheduleAt` is set, `buildScheduledTc()` (`:188–242`) wraps it in TC[11,4] and returns the
   wrapper. Any exception fails the command (`"Error building the TC(11,4) command …"`).
4. `getBinaryLength()` adds `getScheduledTcOverhead()` for scheduled commands, so frame-based links
   size frames correctly.

### TC[11,4] produced

| Field | Size | Source | Spec (Fig 8-91) |
|---|---|---|---|
| CCSDS primary header | 6 B | APID = `pus11Apid` (required; range-checked 0–0x7FF); own seqcount | — |
| PUS-C secondary header | 5 B | version 2, `pus11AckFlags`, type 11, subtype 4, `pus11SourceId` (16 bit) | — |
| sub-schedule ID | 1 B | option `pus11SubscheduleId`, else config (default `0`) | optional in the spec; **always written** by design (Gap #10) |
| N | 1 B | always `1` | unsigned integer |
| group ID | 1 B | option `pus11GroupId`, else config (default `0`) | optional in the spec; **always written** by design (Gap #10), per instruction, after `N` |
| release time | CUC, 6 B default / 8 B example | `tcoService.getObt()` → `encodeRaw`, else epoch-shifted Yamcs time → `encode` | absolute time |
| request | inner length | inner TC verbatim, incl. its CRC | TC packet |
| CRC | 2 B if `pus11Crc` | `errorDetection` calculator | — |

Overhead = `14 + timeEncoder.getEncodedLength() + (pus11Crc ? 2 : 0)`. That is 24 B with the example
config (`pfield: 0x2f`, explicit → 1+4+3 = 8 B time) and 22 B with the default encoder (implicit
`0x2e` → 4+2 = 6 B).

Command history keys: `ccsds-seqcount` (inner), `binary` (inner), `pus11-apid`, `pus11-ccsds-seqcount`,
`pus11-source-id`, `pus11-subschedule-id`, `pus11-group-id`, `pus11-binary`.

### Configuration

```yaml
commandPostprocessorClassName: org.yamcs.pus.PusCommandPostprocessor
commandPostprocessorArgs:
    errorDetection:
        type: CRC-16-CCIIT
    timeEncoding:
        implicitPfield: false
        pfield: 0x2f
        # epoch: TAI | J2000 | UNIX | GPS | CUSTOM | NONE (default NONE = Yamcs internal time)
    tcoService: tco0      # if set, release time is OBT from TCO; otherwise set timeEncoding.epoch
    pus11Apid: 1          # APID of the on-board scheduler. Required for pus11ScheduleAt
    pus11Crc: true        # default = errorDetection configured; true without it fails at startup
    pus11SourceId: 0      # 0-65535
    pus11AckFlags: 0xD    # 0-15
    pus11SubscheduleId: 0 # 0-255, default 0; per-command override via the pus11SubscheduleId option
    pus11GroupId: 0       # 0-255, default 0; per-command override via the pus11GroupId option
```

### Spec compliance

| Requirement | Status |
|---|---|
| TC[11,4] layout, sub-schedules and groups both written (Fig 8-91) | ✅ |
| Embedded request unchanged, so activity ID = inner request ID (§6.11.4.2.b) | ✅ |
| Released request reports go to the original source (§6.11.1.2, §6.11.3.1 NOTE 3) | ✅ inner source ID untouched |
| TC[11,4] addressed to the scheduler's application process (§6.11.2.2, §6.11.3.1 NOTE 2) | ✅ `pus11Apid` required; command fails if unset |
| On-board with sub-schedules and groups (§6.11.4.5.b.1, e.1) | ✅ both fields always written (defaults `0`, Gap #10); an on-board scheduler without either is out of scope |
| Insert verification visible on ground | ❌ Gap #5 |
| Release / silent deletion visible on ground (§6.11.4.6 NOTE 2) | ❌ Gap #8 |
| Time margin (§6.11.4.2.d) | On-board check. No ground pre-check (minor note) |

Unit tests: `PusCommandPostprocessorTest` — defaults, configured fields + option override, and
`getBinaryLength() == process().length` (CRC on/off × scheduled/unscheduled). 3/3 pass (2026-09-17).

---

## C. Gaps

### Summary

| Gap | Severity | Scope | Status |
|---|---|---|---|
| #5 Scheduled commands never complete (verifiers time out) | High | yamcs-core + `pus.xml` | Part 1 ✅ Fixed, part 2 → #8 |
| #9 Wrapper APID defaults to the inner command's APID | Med–High | yamcs-core | ✅ Fixed |
| #8 No ground model of the on-board schedule | Med–High | yamcs-core + web | Open (planned) |
| #10 TC[11,4] group ID never written | Medium | yamcs-core + demo MDB/simulator | ✅ Fixed |
| #11 `pus11Apid` not range-checked; corrupts header | Low | yamcs-core | ✅ Fixed |
| #4 `getBinaryLength()` ignored the wrapper | High | yamcs-core | ✅ Fixed |
| #6 `pus11Crc` default + no `errorDetection` → NPE | Medium | yamcs-core | ✅ Fixed |
| #7 Wrapper sub-schedule / source ID / ack flags hardcoded | Medium | yamcs-core | ✅ Fixed (`N=1` remains) |
| #1 `filter_type` hardcoded in MDB | Low | MDB (demo) | Open |
| #2 Groups bookkeeping-only | Low | MDB + simulator (demo) | Open |
| #3 Execution-function status never read | Low | Simulator (demo) | ✅ Fixed |

### Native — open

#### Gap 5 — Commands scheduled via `pus11ScheduleAt` never complete

**Problem**: there is one `PreparedCommand` (the inner TC); scheduling is only an attribute. The
`pus.xml` verifiers take `sentApid` from `/yamcs/cmd/arg/apid` and `sentSeq` from
`/yamcs/cmdHist/ccsds-seqcount` — both the **inner** TC's (lines 301/305, 324/325, 340/341). Check
windows are 5 s / 5 s / 15 s from `commandRelease`.

1. Within the windows only the **wrapper's** PUS-1 reports arrive. Their seqcount is
   `pus11-ccsds-seqcount` (and their APID is `pus11-apid`), so `Pus1Verifier` returns `NO_RESULT`.
2. The inner reports, which would match, arrive at release time — long after the windows expire.

Every stage ends `TIMEOUT`, and `CommandComplete` is never published. `onTimeout` is unset, so
`CommandVerificationHandler.onVerifierFinished()` never calls `stop()`: the `ActiveCommand`, its
command-history subscription and its algorithm context leak.

**Impact**: operators can't tell whether the insert was accepted; every scheduled command leaks.

**Fix**:
1. ✅ **Done (2026-09-17).** Verify the **wrapper** for the insert. XTCE can't switch inputs
   conditionally, so:
   - `PusCommandPostprocessor.process()`/`buildScheduledTc()` publish a new `ccsds-apid` alongside
     the existing `ccsds-seqcount`; for a scheduled command `buildScheduledTc()` preserves the inner
     request ID under `pus11-inner-apid`/`pus11-inner-seqcount` (for Gap #8) before overwriting
     `ccsds-apid`/`ccsds-seqcount` with the **wrapper's** own apid/seqcount.
   - `pus.xml`'s three verifiers (`Accepted`/`Started`/`Complete`) now read `sentApid` from
     `/yamcs/cmdHist/ccsds-apid` instead of the `/yamcs/cmd/arg/apid` command argument.
     Republishing only the seqcount would not have been enough: whenever `pus11Apid` differs from
     the inner APID (which Gap #9 requires), the APID match would still fail.
   - Tests: `PusCommandPostprocessorTest` (`testScheduledCommandRepublishesWrapperRequestIdForVerifiers`,
     `testUnscheduledCommandPublishesInnerApidOnly`).
2. Open. Track **release** outside the verifier framework (Gap #8). `CheckWindow` has no "relative
   to a command attribute" option.

**Effort**: ~half a day for part 1 (done); part 2 is Gap #8.

#### Gap 8 — No ground model of the on-board schedule

**Problem**: nothing in yamcs-core records what is scheduled on board, when it is due, or whether it
was released. `CommandQueue`/`CommandQueueManager` are ground pre-release queues, unrelated to ST[11].
TM[11,10]/[11,13] are decoded as parameters and not reconciled.

**Impact**: pending time-tagged commands are invisible without a manual summary report. Silent
deletions (disabled function / sub-schedule / group, §6.11.4.6 NOTE 2) go unnoticed. Duplicate
activity IDs go undetected: the `change-seq-count` link action or the 14-bit seqcount wrap can reuse
an inner `(source, apid, seq)` that is still on board, making TC[11,5/7/9/12] ambiguous (§6.11.4.2.b).

**Fix**: planned in `.claude/time-tag-queue-plan.md` (branch `feat/time_tag_command_queue`). Include
duplicate-ID detection at insert time. **Effort**: Large.

> **TODO — open design question, table key for `pus11_schedule` (plan §8.4).** The plan's current
> decision (§8.3) keys the new RocksDB/yarch table's PK on `releaseTime` first
> (`releaseTime, gentime, origin, seqNum, entryIdx`), re-keying the whole row on every time-shift.
> Raised 2026-09-17: key on the **time-tag command's own identity**
> (`gentime, origin, seqNum` of the `pus11ScheduleAt`/`INSERT_ACTIVITIES` command) instead, with
> `releaseTime` as an ordinary value column — a time-shift then updates that column in place rather
> than delete+reinsert. Needs a decision before the table lands: pending user clarification, not yet
> resolved.

### Native — fixed

- **Gap 4 — `getBinaryLength()` ignored the wrapper.** Frame links (`UplinkPacketHandler`,
  `Cop1UplinkPacketHandler`) size frames before `process()`, so every scheduled command crashed
  (`ArrayIndexOutOfBoundsException`) or was dropped. Now both methods share
  `getScheduledTcOverhead()`; covered by `testBinaryLengthMatchesProcessedLength`.
- **Gap 6 — `pus11Crc` defaulted to `true` with no `errorDetection` → NPE.** Now it defaults to
  "`errorDetection` configured"; an explicit `true` without it fails at startup.
- **Gap 7 — wrapper fields hardcoded.** Added config `pus11SourceId`, `pus11AckFlags`,
  `pus11SubscheduleId` (range-checked) and the `pus11SubscheduleId` command option (invalid value fails
  the command). `N` is still `1` — one uplink packet per scheduled TC; batching would need a
  command-stack feature.
- **Gap 10 — TC[11,4] group ID never written.** Design: the sub-schedule ID and group ID are
  **always present**; they are optional in Fig 8-91 only for on-board schedulers without
  sub-schedules/groups, which are out of scope. (Omitting a field would need XTCE `IncludeCondition`,
  which Yamcs's reader skips for argument entries and whose command encoder ignores it.) Added config
  `pus11GroupId` (0–255, default `0`) and the `pus11GroupId` command option, written after `N` and
  before the release time; the `pus11SubscheduleId` default also changed from `1` to `0`.
  `PUS11_FIXED_LENGTH` is now 14; `pus11-group-id` is published to the command history. Demo:
  `group_id` added to `ActivityEntryType` in both `pus11.xml` files and parsed by `Pus11Service`
  (bookkeeping only, Gap #2). Both `subschedule_id` and `group_id` carry `initialValue="0"` in the
  MDB, so a command that omits them is filled with `0` rather than failing (Yamcs applies aggregate
  member `initialValue`s in `AggregateDataType.fromMap`/`fromJson`; verified for arrays of aggregates
  given as JSON lists/objects, as REST clients send them). Covered by `PusCommandPostprocessorTest`.
- **Gap 9 — wrapper APID defaulted to the inner command's APID.** On a multi-APID spacecraft the
  TC[11,4] went to an AP with no ST[11] provider (§6.11.2.2). Now `pus11Apid` is required:
  `buildScheduledTc()` throws `IllegalStateException` naming `pus11Apid`, and `process()` fails the
  command. `pus.rst` updated; covered by `testScheduledCommandFailsWithoutApid`.
- **Gap 11 — `pus11Apid` not range-checked.** A value > `0x7FF` corrupted the packet-type /
  sec-header bits. Now validated with `getIntInRange(config, "pus11Apid", -1, -1, 0x7FF)`.

### Native — minor notes (not tracked as gaps)

- **No ground-side time check**: a past or too-close `pus11ScheduleAt` is uplinked and only rejected
  on board (§6.11.4.5.g.2). A pre-check should use a configurable margin mirroring the on-board
  "time-based schedule time margin" (§6.11.4.2.d), not a plain `> now`.
- **Options registered globally**: they appear on every instance and link; setting them on a link
  without `PusCommandPostprocessor` is silently ignored.
- **Default epoch is `NONE`**: without `tcoService` and `timeEncoding.epoch`, the release time is
  encoded as Yamcs internal time, which no OBC expects.
- **`CucTimeEncoder` 2-octet P-field bug** (upstream): `(pfield1 & 0x80) == 1` is never true, so
  `pfieldCont` extra coarse/fine octets are ignored in the size computation. Only affects configs with
  an extended P-field.
- **TCO failure path**: when OBT is unavailable the command fails, but the inner seqcount has already
  been consumed and `ccsds-seqcount`/`binary` published.

### Demo (MDB / simulator) — open

#### Gap 1 — `filter_type` hardcoded to `0x01` (TC[11,6/8/11/14])

**Problem**: all four filter commands use `<FixedValueEntry binaryValue="01" …/>`, so only from–to
windows can be sent.

**Fix**: time tags are present only for some window types (Fig 8-93 "deduced presence"). Yamcs's
command encoder does **not** evaluate `IncludeCondition` (only `SequenceContainerProcessor` does), so
simply making `filter_type` an argument would still send both time tags and break parsing for types
0/2/3. Define one MetaCommand per window type (e.g. `DELETE_ACTIVITIES_ALL`, `…_FROM_TO`, `…_FROM`,
`…_TO`) over a shared abstract base. **Effort**: ~1–2 h for the four commands.

#### Gap 2 — Scheduling groups are bookkeeping-only

**Problem**: TC[11,22–26]/TM[11,27] maintain `groupStatus`, but nothing consumes it:
1. No membership — `insertActivities()` parses `group_id` (Gap #10) but discards it; `ScheduledCommand`
   has no group field.
2. No release gate — `runSchedule()` checks `subschStatus` only.
3. No group in reports/filters — TM[11,10]/[11,13] and the four filter TCs lack group fields.

**Fix** (all mandatory once groups are supported):
1. `group_id` in each activity entry, before `release_time` (Fig 8-91): now parsed (Gap #10) but not
   stored or validated. **Reject** the instruction if
   the group is unknown (§6.11.4.5.g.3) — do not auto-create.
2. Group gate in `runSchedule()`.
3. Group ID in TM[11,10]/[11,13] entries (§6.11.7.1.b, §6.11.7.2.b) and `N2` + group IDs in the filter
   TCs (Figs 8-93/95/98/101).
4. Reject TC[11,23] for groups with activities (§6.11.6.2.2.d.2).

### Demo (MDB / simulator) — fixed

- **Gap 3 — Execution-function status never read.** `enabled` was written but never read, so
  TC[11,1]/[11,2] had no effect, and it started as `true`. Now `enabled` starts `false`
  (§6.11.4.3.1.b) and `runSchedule()` deletes, without releasing, any activity that falls due while the
  function is disabled (§6.11.4.6). **Send `ENABLE_SCHEDULER` after every simulator start or
  `RESET_SCHEDULER`**, or scheduled commands are silently dropped.
- **Time-shift into the past.** TC[11,7]/[11,8]/[11,15] now NACK completion
  (`COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST`) and leave the schedule untouched if any selected activity
  would move before now (§6.11.4.2.d, no margin).
- **Insert partially applied.** TC[11,4] now validates every instruction before inserting any; a past
  release time rejects the whole TC and nothing is queued.
- **Detail report split bug.** The activity that overflows a 1400-byte TM[11,10] now starts the next
  packet instead of being dropped; an activity larger than the limit goes out alone.
- **`time_offset_ms` unsigned.** Now `/dt/int32` (new signed type in `dt.xml`), so negative shifts can
  be commanded.

### Other simulator deviations (demo, not tracked)

The simulator is a permissive stand-in; do not infer on-board behaviour from it.

- **Sub-schedule auto-created enabled** (`insertActivities()`); spec: disabled (§6.11.4.5.j.1.b). Never deleted
  when empty (§6.11.4.6.c.3).
- **TC[11,3] reset** keeps sub-schedules and does not enable all groups (§6.11.4.4.c.3–4).
- **`N=0` = "all"** not implemented for TC[11,20/21/23/24/25] (§8.11.2.20.c etc.) — no-op.
- **Insert rejection**: sends start ACK, then NACK completion if any release time is in the past, and
  queues nothing. Spec: failed **start** per bad instruction, valid ones still processed
  (§6.11.4.5.h–i). No time margin. Time-shift rejection works the same way (whole TC).
- **No other rejection checks**: unknown request ID, unknown sub-schedule/group, duplicate group,
  invalid window type, from > to — all ACK successfully.
- **Report order**: "all"/filter reports iterate `PriorityQueue.iterator()` (heap order); spec requires
  release-time order (§6.11.7.1.c, §6.11.7.2.c).
- **Empty detail report**: no TM[11,10] at all when the selection is empty.
- **Report padding**: TM[11,10]/[11,13] reserve 4 bytes for the `uint16` `n`, leaving 2 zero bytes
  before the CRC (ignored by the decoder).
- **MDB types**: `CREATE_SCHEDULING_GROUPS.group_status` is `/dt/uint8`, not enumerated (Table 8-4).

---

## D. Demo MDB reference

Commands under `/PUS11/`. Simulator mission layout: IDs `uint8`, counts `uint16` (request lists,
reports) or `uint8` (sub-schedules, groups), status-report counts `uint32`, time CUC 8 B
(`PusTime`), offset `int32` ms (signed; negative = earlier).

| Subtype | MDB name | Layout (after secondary header) |
|---|---|---|
| TC 1 / 2 / 3 | `ENABLE_SCHEDULER` / `DISABLE_SCHEDULER` / `RESET_SCHEDULER` | — |
| TC 4 | `INSERT_ACTIVITIES` | `subschedule_id:u8 (default 0), n:u8, n×{group_id:u8 (default 0), release_time, tc_packet}` |
| TC 5 / 9 / 12 | `DELETE_ACTIVITIES_BY_ID` / `GET_DETAIL_REPORT_BY_ID` / `GET_SUMMARY_REPORT_BY_ID` | `num_requests:u16, n×{source_id:u16, apid:u16, seqcount:u16}` |
| TC 7 | `TIME_SHIFT_ACTIVITIES_BY_ID` | `time_offset_ms, num_requests:u16, requests[]` |
| TC 6 / 11 / 14 | `DELETE_ACTIVITIES_BY_FILTER` / `GET_DETAIL_REPORT_BY_FILTER` / `GET_SUMMARY_REPORT_BY_FILTER` | `filter_type=0x01 (fixed), start_time, end_time, num_schedules:u8, schedules[]` (Gap #1) |
| TC 8 | `TIME_SHIFT_ACTIVITIES_BY_FILTER` | `time_offset_ms` + filter as above |
| TC 15 | `TIME_SHIFT_ACTIVITIES` | `time_offset_ms` |
| TC 16 / 17 / 18 / 26 | `GET_DETAIL_REPORT` / `GET_SUMMARY_REPORT` / `GET_SCHEDULE_STATUS` / `REPORT_GROUP_STATUS` | — |
| TC 20 / 21 | `ENABLE_SCHEDULE` / `DISABLE_SCHEDULE` | `num_schedules:u8, schedules[]` |
| TC 22 | `CREATE_SCHEDULING_GROUPS` | `num_groups:u8, n×{group_id:u8, group_status:u8}` |
| TC 23 / 24 / 25 | `DELETE_` / `ENABLE_` / `DISABLE_SCHEDULING_GROUPS` | `num_groups:u8, group_ids[]` |
| TM 10 | `/PUS11/DETAIL_REPORT/DETAIL_REPORT` | `n:u16, n×{schedule_id:u8, release_time, TC packet}`; `tc_data` sized `8·length − 48` bits |
| TM 13 | `/PUS11/SUMMARY_REPORT/SUMMARY_REPORT` | `n:u16, n×{schedule_id:u8, release_time, source:u16, apid:u16, seq:u16}` |
| TM 19 | `/PUS11/SUBSCHEDULE_STATUS_REPORT` | `status_report_n:u32, n×{schedule_id:u8, schedule_status:u8}` |
| TM 27 | `/PUS11/GROUP_STATUS_REPORT` | `group_report_n:u32, n×{group_id:u8, group_status:u8}` |

TM[11,10]/[11,13] live one `SpaceSystem` deeper than the other containers (nicer names in Yamcs Web).
Count fields (`n`, `num_*`) must be supplied explicitly alongside their arrays.

---

## E. Manual Testing

### E.1 Start the instance

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests   # first build only
mvn -pl examples/pus yamcs:run
```

Web UI: `http://localhost:8090`, instance `pus`.

### E.2 Example arguments

| Command | Example args |
|---|---|
| `INSERT_ACTIVITIES` | `{"subschedule_id": 1, "n": 1, "activities": [{"release_time": "<future>", "tc_packet": "<hex, see E.2.1>"}]}` |
| `*_BY_ID` | `{"num_requests": 1, "requests": [{"source_id": 0, "apid": 1, "seqcount": <inner seq>}]}` (+ `"time_offset_ms": 5000` for TC[11,7]) |
| `*_BY_FILTER` | `{"start_time": "<past>", "end_time": "<far future>", "num_schedules": 0, "schedules": []}` (+ `time_offset_ms` for TC[11,8]); `num_schedules=0` = any sub-schedule |
| `TIME_SHIFT_ACTIVITIES` | `{"time_offset_ms": 5000}` |
| `ENABLE_SCHEDULE` / `DISABLE_SCHEDULE` | `{"num_schedules": 1, "schedules": [1]}` |
| `CREATE_SCHEDULING_GROUPS` | `{"num_groups": 1, "groups": [{"group_id": 1, "group_status": 1}]}` |
| `DELETE_/ENABLE_/DISABLE_SCHEDULING_GROUPS` | `{"num_groups": 1, "group_ids": [1]}` |
| No-arg commands | `{}` |

Simulator responses: a past `release_time`, or a time-shift that would move an activity into the past
→ NACK completion `COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST` (schedule unchanged);
TM-only subtypes (10, 13, 19, 27) → NACK start `START_ERR_INVALID_PUS_SUBTYPE`; everything else ACKs.

#### E.2.1 Building `tc_packet` for TC[11,4]

Dry-run a zero-argument TC and reuse its binary:

```python
from yamcs.client import YamcsClient

client = YamcsClient("localhost:8090")
processor = client.get_processor("pus", "realtime")
issued = processor.issue_command("/PUS17/ARE_YOU_ALIVE", args={}, dry_run=True)
tc_packet_bytes = issued.binary   # complete, CRC'd TC packet
```

Request IDs for `*_BY_ID` are the **embedded** TC's `(source_id, apid, seqcount)`.

Hand-built alternative (APID 1, ackflags 7, 13 bytes, CRC zeroed):

```
1801 C000 0006 27 11 01 0000 0000
```

The simulator does not validate TC CRCs, so the zeroed CRC works here — it would not on real hardware.

### E.3 What to watch

- The containers in the table in D.
- `/PUS/pus-tc-ack-*` for the TC[11,x] ACKs, **and** a second ACK set for the embedded TC at its
  release time with no matching ground command — the proof of on-board release.
- Detail reports over 1400 B are split into several TM[11,10] packets; together they list every
  selected activity.

### E.4 Walkthrough — native `pus11ScheduleAt` path (production-relevant)

1. In Yamcs Web, send `/PUS17/ARE_YOU_ALIVE` with option **Schedule Time** ~20 s ahead
   (and optionally **Sub-schedule ID**). Send `ENABLE_SCHEDULER` first if not done since the simulator
   started — the execution function starts disabled and a disabled activity is dropped at release.
2. Command history for that command shows `pus11-apid`, `pus11-ccsds-seqcount`, `pus11-source-id`,
   `pus11-subschedule-id`, `pus11-binary`. Check `pus11-binary` bytes 11–12 = sub-schedule, `01`, then
   the release time, then the inner `binary`.
3. `GET_SUMMARY_REPORT` → one entry whose `apid/seq` = the inner `ccsds-seqcount`, not `pus11-*`.
4. Within 5 s: wrapper ACKs (TC[11,4]) arrive, but the command's `Verifier_*` stages end
   `TIMEOUT` — Gap #5 reproducer.
5. At release: TM[17,2] plus a second ACK set for TC[17,1].

### E.5 Walkthrough — simulator scheduler (demo)

1. **Baseline**: `ENABLE_SCHEDULER` (required — the execution function starts disabled);
   `GET_SCHEDULE_STATUS` → `status_report_n=0`.
2. **Insert**: `INSERT_ACTIVITIES` into sub-schedule 1, ~10 s ahead. `GET_SCHEDULE_STATUS` shows
   sub-schedule 1 **enabled** (simulator deviation — spec creates it disabled).
3. **Reports**: `GET_SUMMARY_REPORT` shows the embedded request ID; `GET_DETAIL_REPORT` shows the
   embedded TC bytes (compare to `tc_packet_bytes`).
4. **Release**: after `release_time`, a second ACK set for TC[17,1] + TM[17,2]; summary `n=0`.
5. **Sub-schedule gate**: insert, `DISABLE_SCHEDULE` [1], wait past release → no ACK, log
   "Dropping command … subschedule … is disabled", activity gone from the summary.
6. **Time-shift by ID**: insert ~30 s out, `ENABLE_SCHEDULE` [1], `TIME_SHIFT_ACTIVITIES_BY_ID`
   `+60000` → summary release time +60 s.
7. **Time-shift by filter / all**: two activities; filter window covering one → only it moves;
   `TIME_SHIFT_ACTIVITIES` → all move. `TIME_SHIFT_ACTIVITIES` `-5000` → all move 5 s earlier.
8. **Past release time**: an insert with one future and one past entry → NACK completion, summary
   count unchanged (neither queued). A time-shift that would push any activity before now (e.g.
   `-3600000`) → NACK completion, release times unchanged.
9. **Delete**: `DELETE_ACTIVITIES_BY_ID` removes exactly one; `DELETE_ACTIVITIES_BY_FILTER` with a wide
   window removes the rest.
10. **Groups**: create (status 1) → report; disable → report shows 0; enable; delete → gone. A disabled
    group does **not** block releases (Gap #2).
11. **Reset**: `RESET_SCHEDULER` → `GET_SUMMARY_REPORT` returns `n=0` (`GET_DETAIL_REPORT` emits no
    packet when empty). Insert one without re-enabling: at release it does **not** fire, the log shows
    "Dropping command … execution function is disabled", and it is gone from the summary.
12. **Execution function gate**: `ENABLE_SCHEDULER`, insert ~10 s ahead, `DISABLE_SCHEDULER`, wait past
    release → same drop as step 11.
