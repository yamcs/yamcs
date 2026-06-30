# PUS ST[05] — Event Reporting Service
> ECSS-E-ST-70-41C §6.5 (pp. 121–126) and §8.5 (pp. 477–480)

---

## Purpose

ST[05] reports events of operational significance: on-board failures/anomalies, activity progress, BITE results, payload events.

**Event Definition**: identified by an *event definition ID* unique within an APID. Each has a fixed severity level and an optional auxiliary data structure whose layout is *deduced* from the event ID (not self-describing in the packet).

**Event Report Generation Status**: per-event-definition `enabled`/`disabled` flag; initial state declared at specification time.

---

## Severity / Subtype Mapping

| Subtype | Name | YAMCS severity |
|---------|------|----------------|
| 1 | Informative event report | INFO |
| 2 | Low severity anomaly report | WATCH |
| 3 | Medium severity anomaly report | DISTRESS |
| 4 | High severity anomaly report | CRITICAL |

---

## Message Types

| Subtype | Dir | Name |
|---------|-----|------|
| 1 | TM | Informative event report |
| 2 | TM | Low severity anomaly report |
| 3 | TM | Medium severity anomaly report |
| 4 | TM | High severity anomaly report |
| 5 | TC | Enable report generation |
| 6 | TC | Disable report generation |
| 7 | TC | Report list of disabled event definitions |
| 8 | TM | Disabled event definitions list report |

---

## Packet Structures (§8.5)

**TM[5,1–4]**: `event_id (enum) | auxiliary data (deduced)`  
**TC[5,5/6]**: `N (uint8) | event_id × N (enum)`  
**TC[5,7]**: no application data  
**TM[5,8]**: `N (uint8) | event_id × N (enum)`

---

## MCS Implementation Summary (Ground Station Only)

> MCS = YAMCS ground station. On-board logic (simulator/`Pus5Service`) is out of scope here.

| Message | MCS Role | XTCE Sufficient? | Java Required? | Notes |
|---------|----------|-----------------|----------------|-------|
| TM[5,1] | Receive | Partial | **Yes** — `PusEventDecoder` | XTCE decodes packet + params; Java needed to emit YAMCS native events to events stream |
| TM[5,2] | Receive | Partial | **Yes** — `PusEventDecoder` | Same as TM[5,1] |
| TM[5,3] | Receive | Partial | **Yes** — `PusEventDecoder` | Same as TM[5,1] |
| TM[5,4] | Receive | Partial | **Yes** — `PusEventDecoder` | Same as TM[5,1] |
| TC[5,5] | Send | **Yes** | No | `ENABLE_REPORT_GENERATION` MetaCommand in MDB; YAMCS sends natively |
| TC[5,6] | Send | **Yes** | No | `DISABLE_REPORT_GENERATION` MetaCommand in MDB; YAMCS sends natively |
| TC[5,7] | Send | **Yes** | No | `REPORT_DISABLED_LIST` MetaCommand in MDB; YAMCS sends natively |
| TM[5,8] | Receive | **Yes** | No | `pus5-disabled-list` container fully decodes count + ID array |

**Key takeaway**: For MCS, only the TM[5,1–4] receive path requires Java (`PusEventDecoder`). All TC sends and TM[5,8] decode are pure XTCE.

---

## Component Roles

| Component | File | Role |
|-----------|------|------|
| Ground decoder | `yamcs-core/.../pus/PusEventDecoder.java` | Decodes TM[5,1–4] → YAMCS native events via XtceTmExtractor + YAML event templates. **Only Java the ground-only MCS needs.** |
| MDB | `examples/pus/src/main/yamcs/mdb/pus5.xml` | XTCE definitions for all 8 message types (TC[5,5/6/7] sends + TM[5,8]/TM[5,1–4] decode) |
| Simulator | `simulator/.../pus/Pus5Service.java` | **Spacecraft side** (test target): generates TM[5,1–4]; handles TC[5,5/6/7] and replies TM[5,8] |

> No on-board handler runs on the ground. The former `yamcs-core/.../pus/Pus5Service` (a `PusTcHandler` that intercepted TC[5,5/6/7] locally) has been **removed** — for a ground-only MCS those TCs must reach the spacecraft. `PusCommandReleaser` no longer registers a service-5 handler, so the TCs fall through to `StreamTcCommandReleaser` and are radiated over the link.

### XTCE Container Hierarchy

```
pus5-tm  (type == 5)
  ├── pus5-event-report  (subtype < 5) → event_id
  │     ├── event1  (restriction: event_id == EVENT_1) → event1_para1, event1_para2
  │     └── event2  (restriction: event_id == EVENT_2) → event2_msg
  └── pus5-disabled-list  (subtype == 8) → disabled_count, disabled_event_ids
```

### PusEventDecoder Flow

```
TM stream → PusEventDecoder (guards: type==5, subtype in [1,4])
  → XtceTmExtractor → extracts event_id + aux params
  → EventFormatter.format(apid, eventId, params)  [looks up YAML template]
  → emits Event proto on events_realtime stream
```

`PusEventDecoder` requires an `eventTemplateFile` (YAML) with one template per event ID. Missing templates produce warnings; raw TM is still archived.

---

## On-Board Service (spacecraft side, not the MCS)

The enable/disable bookkeeping and TM[5,8] generation are **on-board responsibilities** and live only in the spacecraft (modelled by `simulator/.../pus/Pus5Service.java` for testing). The MCS does not run this logic — it sends TC[5,5/6/7] over the link and receives the spacecraft's TM[5,8] / TM[5,1–4] in response.

For a ground-only MCS the previous `yamcs-core` on-board emulation (`org.yamcs.pus.Pus5Service`, a `PusTcHandler` that intercepted these TCs locally, kept a `Map<Integer,Boolean>` in `MementoDb`, and fabricated TM[5,8] on `tm_realtime`) has been removed. Keeping it would have silently swallowed the TCs on the ground and faked spacecraft telemetry.

---

## Gaps and Open Items

| Message | Issue | Severity |
|---------|-------|----------|
| TM[5,1–4] | Event registry is static (MDB load-time). New event type requires `<Enumeration>` + restart. | Medium |
| TM[5,1–4] | Aux data is "deduced" — each event type needs its own XTCE sub-container matching the on-board byte layout. No generic mechanism. | Medium |
| TC[5,7] / TM[5,8] | `ContainerVerifier` on `pus5-disabled-list` not yet defined in MDB — TC[5,7] complete verification still relies on the ST[01] completion report. | Medium |
| TC[5,5/6] | Event IDs encoded as uint8. If mission uses uint16, the MDB types must change. | Low |
| All | §6.5.6 observables (accumulated occurrences, disabled count, last event ID/time per severity) are on-board concerns — out of MCS scope. | Low |

**Architectural**: `PusEventDecoder` is the only path from TM[5,1–4] packets to YAMCS native events and must remain an instance-level service. The enable/disable state is held on-board; the MCS observes it only via TC[5,7] → TM[5,8].

---

## Mission-Specific Items

**`prepended_string` encoding**: YAMCS-specific 2-byte-length-prefixed string used in event aux data. Replace with actual FSW encoding (fixed-length, null-terminated, or 1-byte prefix).

**Event ID bit width**: MDB assumes uint8. For 16-bit IDs add `<IntegerDataEncoding sizeInBits="16"/>` to the MDB types (the on-board FSW must match).

**TC[5,7] Complete Verifier (open)**: add a `ContainerVerifier` on `pus5-disabled-list` to the `REPORT_DISABLED_LIST` `CommandVerifierSet` in `pus5.xml`.

---

## Bugs Fixed

| # | Fix |
|---|-----|
| 1 | `PusEventDecoder` subtype guard added: `if (subtype < 1 || subtype > 4) return` — prevents TM[5,8] leaking into event pipeline |
| 2 | `pus5.xml` restructured to `pus5-tm` → `pus5-event-report` (subtypes 1–4) + `pus5-disabled-list` (subtype 8) |
| 3 | `IncludeCondition` on `disabled_event_ids` — prevents empty-array crash when N=0 |
| 4 | `Pus1Verifier` returns `NO_RESULT` for packets where service type ≠ 1 — prevents false failure on TC[5,7] |
| 5 | Ground-only MCS: removed the on-board emulation handler (`yamcs-core` `Pus5Service`) and unregistered service 5 from `PusCommandReleaser`, so TC[5,5/6/7] are radiated to the spacecraft instead of being handled locally |
