# PUS ST[12] — On-board monitoring

Reviewed against commit `90d4a3d1c` on 2026-11-18. The Java
[Pus12Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus12Service.java) is implemented
and registered; it samples synthetic signals, evaluates rules and raises ST[05] events.
[pus12.xml](../examples/pus/src/main/yamcs/mdb/pus12.xml) defines ground commands and reports.
There is no additional ground on-board-monitoring handler in `yamcs-core`.

## Coverage and representation

The example implements TC[12,1/2/5/6/7/8/11/13] and TM[12,9/12/14]. Functional monitoring and
optional validity-condition fields are outside this implementation. The simulator has a
100 ms master tick and four preloaded monitoring definitions. ST[05] event definitions 10–15
and event templates are already supplied; they do not need to be created from scratch.

TC[12,5] and TC[12,7] use three typed command variants: float32 limit checking, uint8 expected
value checking, and float32 delta checking. This is the supported encoder path. The previous
claim that `IncludeCondition` on command argument entries could replace these variants was
incorrect: the current command encoder does not implement that conditional-entry behavior.

TM[12,9] uses repeated `PMON_ENTRY` containers with conditional parameter entries selected by
`check_type`. TM-side comparisons use current-entry parameter lookup. Ordinary XML
`DynamicValue` references now also use the latest decoded occurrence when `instance` is omitted;
an explicit instance retains packet-start indexing. The conditional criteria design is not
evidence that arbitrary parameter-type deduction or TC conditional
encoding works. Monitoring intervals are explicitly carried in the add commands by this
example's capability choice; no extra presence flag is required for a fixed declared layout.

The command tables below are current example inputs, and the walkthrough is a verification
procedure rather than a recorded result. Timing predictions are approximate because signals,
repetition counters and executor scheduling affect transitions.

## Integration checks

### D.1 Start the instance

```bash
mvn -pl simulator,examples/pus -am clean install -DskipTests   # first build only
mvn -pl examples/pus yamcs:run
```
Web UI: `http://localhost:8090`, instance `pus`. Commands live under `/PUS12/...`, TM containers
under the same `/PUS12/` subsystem (see the command/report tables).

### D.2 Seed PMON definitions (pre-registered, enabled, no TC required)

| pmon_id | param_id | check_type | thresholds | rep# | interval | events |
|---|---|---|---|---|---|---|
| 1 `PMON_SINE_TEMP_LIMIT` | 1 `PARAM_SINE_TEMP` | limit-checking | low=-10.0, high=50.0 | 3 | 1000ms | low=`EVENT_SINE_TEMP_LOW`(10), high=`EVENT_SINE_TEMP_HIGH`(11) |
| 2 `PMON_RANDWALK_LIMIT` | 2 `PARAM_BUS_CURRENT` | limit-checking | low=1.0, high=8.0 | 2 | 500ms | low=`EVENT_RANDWALK_LOW`(12), high=`EVENT_RANDWALK_HIGH`(13) |
| 3 `PMON_BUSVOLT_DELTA` | 3 `PARAM_BUS_VOLTAGE` | delta-checking | low=0.05 (no event), high=1.0 | 3 | 1000ms | high=`EVENT_BUSVOLT_DELTA_HIGH`(14) |
| 4 `PMON_MODE_EXPECTED` | 4 `PARAM_MODE_REGISTER` | expected-value-checking | mask=0xFF, expected=1 | 2 | 1000ms | `EVENT_MODE_UNEXPECTED`(15) |

`pmon_id_type`/`monitored_param_id_type` in `pus12.xml` only enumerate these 4 IDs each — the examples below use those labels. To test a successful add with a seeded ID, disable and delete its existing definition first.

### D.3 Command reference — valid inputs

All commands are under `/PUS12/`. `N` is a numeric count; `pmon_ids` contains enum labels such as `"PMON_SINE_TEMP_LIMIT"`. Counts must match array lengths.

| Command | Subtype | Valid example args |
|---|---|---|
| `ENABLE_PARAMETER_MONITORING` | TC[12,1] | `{"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]}` |
| `DISABLE_PARAMETER_MONITORING` | TC[12,2] | `{"N": 2, "pmon_ids": ["PMON_SINE_TEMP_LIMIT", "PMON_RANDWALK_LIMIT"]}` |
| `ADD_PMON_LIMIT_F32` | TC[12,5] | `{"pmon_id": "PMON_SINE_TEMP_LIMIT", "param_id": "PARAM_SINE_TEMP", "monitoring_interval_ms": 1000, "repetition_number": 3, "low_limit": -10.0, "low_event_id": 10, "high_limit": 50.0, "high_event_id": 11}` |
| `ADD_PMON_EXPECTED_U8` | TC[12,5] | `{"pmon_id": "PMON_MODE_EXPECTED", "param_id": "PARAM_MODE_REGISTER", "monitoring_interval_ms": 1000, "repetition_number": 2, "mask": 255, "expected_value": 1, "event_id": 15}` |
| `ADD_PMON_DELTA_F32` | TC[12,5] | `{"pmon_id": "PMON_BUSVOLT_DELTA", "param_id": "PARAM_BUS_VOLTAGE", "monitoring_interval_ms": 1000, "repetition_number": 3, "low_threshold": 0.05, "low_event_id": 0, "high_threshold": 1.0, "high_event_id": 14, "num_consecutive_deltas": 3}` |
| `DELETE_PMON_DEFINITIONS` | TC[12,6] | `{"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]}` — target must be **disabled** first |
| `MODIFY_PMON_LIMIT_F32` | TC[12,7] | Same shape as `ADD_PMON_LIMIT_F32` minus `monitoring_interval_ms`; `pmon_id`/`param_id`/check_type must match the existing definition exactly |
| `MODIFY_PMON_EXPECTED_U8` | TC[12,7] | Same shape as `ADD_PMON_EXPECTED_U8` minus `monitoring_interval_ms` |
| `MODIFY_PMON_DELTA_F32` | TC[12,7] | Same shape as `ADD_PMON_DELTA_F32` minus `monitoring_interval_ms` |
| `REPORT_PMON_DEFINITIONS` | TC[12,8] | `{"N": 0, "pmon_ids": []}` → all; or `{"N": 1, "pmon_ids": ["PMON_MODE_EXPECTED"]}` → one |
| `REPORT_PMON_CHECK_TRANSITIONS` | TC[12,11] | `{}` (no arguments) |
| `REPORT_PMON_STATUS` | TC[12,13] | `{}` (no arguments) |

Rejection conditions to exercise (all respond NACK completion, not NACK start — the command is
accepted then rejected during execution): re-adding an existing `pmon_id` (`ALREADY_EXISTS`),
deleting/modifying an unknown `pmon_id` (`PMON_ID_UNKNOWN`), deleting a still-`enabled` definition
(`PMON_STILL_ENABLED`), modifying with the wrong check-type variant (`CHECK_TYPE_MISMATCH`), or
modifying with a `param_id` that doesn't match the stored definition (`PARAM_ID_MISMATCH`).

### D.4 TMs to check

| Container | Subtype | Triggered by | Layout (after the 21-byte common PUS TM header) |
|---|---|---|---|
| `/PUS12/PMON_DEFINITION_REPORT` | TM[12,9] | TC[12,8] | `count:u8` + count × variable-length entries (see the criteria list below): `pmon_id:u16, param_id:u16, pmon_status:u8, repetition_number:u8, check_type:u8`, then criteria fields gated by `check_type` — no padding, fully decoded per-entry by XTCE (`PMON_ENTRY` container) |
| `/PUS12/PMON_TRANSITION_REPORT` | TM[12,12] | TC[12,11] | `count:u8` + count × 4B entries: `pmon_id:u16, prev_status:u8, new_status:u8` (flushes and clears `checkTransitionList`) |
| `/PUS12/PMON_STATUS_REPORT` | TM[12,14] | TC[12,13] | `count:u8` + count × 3B entries: `pmon_id:u16, pmon_status:u8` |
| `/PUS5/event_sine_temp_low`, `event_sine_temp_high`, `event_randwalk_low`, `event_randwalk_high`, `event_busvolt_delta_high`, `event_mode_unexpected` | TM[5,2] (subtype always hardcoded to 2 by `Pus12Service.maybeRaiseEvent`) | An enabled PMON transitioning into LOW/HIGH (never on recovery) | `event_id:u8` (10-15) + `pmon_id:u16` + `value:f32` (or `value:u8` for `event_mode_unexpected`) |

`pmon_status`: `0=disabled, 1=enabled`. `check_type`: `0=expected-value-checking, 1=limit-checking, 2=delta-checking`.
`prev_status`/`new_status` (generic across all 3 check types, see `PmonCheckingStatus`): `0=good, 1=unchecked, 2=invalid, 3=low_or_unexpected, 4=high`.

Criteria decode by `check_type` (all big-endian, no padding — entries are genuinely variable-length):
- limit-checking (12B): `low_limit:f32, low_event_id:u16, high_limit:f32, high_event_id:u16`
- delta-checking (13B): `low_threshold:f32, low_event_id:u16, high_threshold:f32, high_event_id:u16, num_consecutive_deltas:u8`
- expected-value-checking (4B): `mask:u8, expected_value:u8, event_id:u16`

### D.5 Automated integration test

The repository includes `examples/pus/tests/test-pus12.py`, using the Yamcs Python client:

```bash
python3 examples/pus/tests/test-pus12.py
```

Covers, in order: initial 4-seed state via TM[12,14]; enable/disable round-trip; TC[12,8] N=0 full
report via TM[12,9]; delete-while-enabled rejection; disable→delete→re-add round-trip (the only way
to exercise a successful TC[12,5] given the fixed 4-entry `pmon_id_type` enum); duplicate-add
rejection; modify check-type-mismatch and param-id-mismatch rejections; a valid modify verified by
re-reading TM[12,9] criteria bytes; and a structural check of TM[12,12]. It restores the original
seed state at the end, so it is safe to re-run against the same live instance.

It deliberately does **not** assert which checking-status transitions fire from the synthetic
signals — that depends on wall-clock timing (see D.6) — only on the deterministic command/response
state machine.

### D.6 Manual verification of the timing-based monitoring loop

The four signal generators run in `Pus12Service.advanceSignalGenerators()` on a 100ms master tick,
independent of the CLI/UI. With the seed defaults, on a freshly started instance:

- **PMON 1** (sine wave, ±35 around 20°C over a 24s period, low=-10/high=50, rep#=3, 1000ms interval):
  expect a `HIGH` transition within the first ~10-12s (sine peak ≈ 55 > 50), a `GOOD` recovery a few
  seconds later, and a `LOW` transition around the trough (sine trough ≈ -15 < -10) roughly every
  24s cycle thereafter.
- **PMON 2** (mean-reverting random walk around 4.5, low=1.0/high=8.0, rep#=2, 500ms interval):
  stochastic — expect occasional `LOW`/`HIGH` excursions within the first ~30-60s, not on a fixed
  schedule.
- **PMON 3** (flat 28.0V with a scripted spike to 31.0V every 15s held for 1.5s, low=0.05 [no event]/
  high=1.0, rep#=3, 1000ms interval): expect a `HIGH` transition roughly every 15s window (the
  averaged delta across 3 consecutive 1s samples crosses the spike).
- **PMON 4** (mode register cycling NOMINAL(1)×8 ticks / SAFE(2)×2 ticks, expected=1, rep#=2, 1000ms
  interval): expect a `LOW` ("unexpected value") transition roughly every 10 ticks of its own
  sampling cadence, recovering to `GOOD` shortly after.

To observe: subscribe to `/PUS12/PMON_TRANSITION_REPORT` (send `REPORT_PMON_CHECK_TRANSITIONS`
periodically, or poll it) and to the YAMCS Events view for `EVENT_SINE_TEMP_LOW`/`HIGH`,
`EVENT_RANDWALK_LOW`/`HIGH`, `EVENT_BUSVOLT_DELTA_HIGH`, `EVENT_MODE_UNEXPECTED` (ids 10-15) —
each should have a readable rendered message from `events.json`. Recovery-to-`GOOD` transitions are
visible in TM[12,12] but do **not** raise an event — the spec's `low_event_id`/`high_event_id`/
`event_id` fields (Fig 8-116/8-117) have no dedicated "recovered" counterpart, so `Pus12Service`
fires events only on entry into `LOW`/`HIGH`.
