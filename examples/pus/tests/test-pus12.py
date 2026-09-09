#!/usr/bin/env python3
"""
Integration test for PUS ST[12] simulator implementation (Pus12Service).

Requires the examples/pus instance to be running:
    cd examples/pus && mvn yamcs:run

Covers the deterministic, command-driven behaviour only (enable/disable/add/
delete/modify/report state machine and rejection conditions). It deliberately
does NOT assert on which checking-status transitions occur from the synthetic
signal generators, since those depend on wall-clock timing (see
pus_analysis/pus12.md "Testing Methodology" for a manual/timing-based walkthrough
of that part).

Tests:
    1. Initial state: TC[12,13] -> TM[12,14] shows the 4 seed PMONs, all enabled
    2. TC[12,2] disable PMON 1, TC[12,1] re-enable it -> status reflects each change
    3. TC[12,8] N=0 -> TM[12,9] returns all 4 definitions with correct headers
    4. TC[12,6] delete while enabled -> NACK completion (still-enabled rejection)
    5. Disable + delete PMON 1, then TC[12,5] re-add it -> ACK completion, back to 4 defs
    6. TC[12,5] duplicate pmon_id -> NACK completion (already-exists rejection)
    7. TC[12,7] modify with wrong check_type -> NACK completion (check_type mismatch)
    8. TC[12,7] modify with wrong param_id -> NACK completion (param_id mismatch)
    9. TC[12,7] valid modify -> ACK completion, TM[12,9] reflects new criteria
    10. TC[12,11] -> TM[12,12] returns a structurally valid (possibly empty) report

Byte layout of TM[12,9]/[12,12]/[12,14] (from Pus12Service, same PusTmPacket
header as TM[5,8] in test-pus5.py: 6B CCSDS + 7B PUS secondary header + 8B CUC
time, pfield=0x2F not implicit):
    [0..20]  21B common PUS TM header
    [21]     N (uint8, entry count)
    [22..]   N entries, layout depends on report (see parse_* helpers below)

TM[12,9] entries are variable-length (7B common header + a criteria tail whose
shape depends on check_type) — no padding, matching the pus12.xml PMON_ENTRY
container's IncludeCondition-gated fields (see pus_analysis/pus12.md Gap 1).
"""

import queue
import struct
import sys

from yamcs.client import YamcsClient

INSTANCE = "pus"
PROCESSOR = "realtime"
HOST = "localhost:8090"

CMD_ENABLE = "/PUS12/ENABLE_PARAMETER_MONITORING"
CMD_DISABLE = "/PUS12/DISABLE_PARAMETER_MONITORING"
CMD_ADD_LIMIT_F32 = "/PUS12/ADD_PMON_LIMIT_F32"
CMD_DELETE = "/PUS12/DELETE_PMON_DEFINITIONS"
CMD_MODIFY_LIMIT_F32 = "/PUS12/MODIFY_PMON_LIMIT_F32"
CMD_REPORT_DEFS = "/PUS12/REPORT_PMON_DEFINITIONS"
CMD_REPORT_TRANSITIONS = "/PUS12/REPORT_PMON_CHECK_TRANSITIONS"
CMD_REPORT_STATUS = "/PUS12/REPORT_PMON_STATUS"

CONTAINER_DEFS = "/PUS12/PMON_DEFINITION_REPORT"
CONTAINER_TRANSITIONS = "/PUS12/PMON_TRANSITION_REPORT"
CONTAINER_STATUS = "/PUS12/PMON_STATUS_REPORT"

APP_DATA_OFFSET = 21  # 6B CCSDS + 7B PUS secondary header + 8B CUC time

# pmon_id_type / monitored_param_id_type enum labels (pus12.xml)
PMON_SINE_TEMP_LIMIT = 1
PMON_RANDWALK_LIMIT = 2
PMON_BUSVOLT_DELTA = 3
PMON_MODE_EXPECTED = 4
PARAM_SINE_TEMP = 1
PARAM_BUS_CURRENT = 2

CHECK_TYPE_EXPECTED_VALUE = 0
CHECK_TYPE_LIMIT = 1
CHECK_TYPE_DELTA = 2
STATUS_DISABLED = 0
STATUS_ENABLED = 1

GREEN = "\033[32m"
RED = "\033[31m"
RESET = "\033[0m"


def parse_status_report(raw: bytes):
    n = raw[APP_DATA_OFFSET]
    entries = []
    off = APP_DATA_OFFSET + 1
    for _ in range(n):
        pmon_id, status = struct.unpack_from(">HB", raw, off)
        entries.append((pmon_id, status))
        off += 3
    return n, entries


def parse_definition_report(raw: bytes):
    n = raw[APP_DATA_OFFSET]
    entries = []
    off = APP_DATA_OFFSET + 1
    for _ in range(n):
        pmon_id, param_id, status, rep_num, check_type = struct.unpack_from(">HHBBB", raw, off)
        off += 7
        entry = {
            "pmon_id": pmon_id,
            "param_id": param_id,
            "status": status,
            "rep_num": rep_num,
            "check_type": check_type,
        }
        if check_type == CHECK_TYPE_EXPECTED_VALUE:
            mask, expected_value, event_id = struct.unpack_from(">BBH", raw, off)
            entry.update(mask=mask, expected_value=expected_value, event_id=event_id)
            off += 4
        elif check_type == CHECK_TYPE_LIMIT:
            low_limit, low_event_id, high_limit, high_event_id = struct.unpack_from(">fHfH", raw, off)
            entry.update(
                low_limit=low_limit,
                low_event_id=low_event_id,
                high_limit=high_limit,
                high_event_id=high_event_id,
            )
            off += 12
        elif check_type == CHECK_TYPE_DELTA:
            low_threshold, low_event_id, high_threshold, high_event_id, num_consecutive_deltas = (
                struct.unpack_from(">fHfHB", raw, off)
            )
            entry.update(
                low_threshold=low_threshold,
                low_event_id=low_event_id,
                high_threshold=high_threshold,
                high_event_id=high_event_id,
                num_consecutive_deltas=num_consecutive_deltas,
            )
            off += 13
        entries.append(entry)
    return n, entries


def parse_transition_report(raw: bytes):
    n = raw[APP_DATA_OFFSET]
    entries = []
    off = APP_DATA_OFFSET + 1
    for _ in range(n):
        pmon_id, prev_status, new_status = struct.unpack_from(">HBB", raw, off)
        entries.append((pmon_id, prev_status, new_status))
        off += 4
    return n, entries


def run_tests():
    client = YamcsClient(HOST)
    proc = client.get_processor(INSTANCE, PROCESSOR)
    cmd_conn = proc.create_command_connection()

    defs_q: queue.Queue[bytes] = queue.Queue()
    transitions_q: queue.Queue[bytes] = queue.Queue()
    status_q: queue.Queue[bytes] = queue.Queue()
    defs_sub = proc.create_container_subscription(CONTAINER_DEFS, on_data=lambda c: defs_q.put(c.binary))
    transitions_sub = proc.create_container_subscription(
        CONTAINER_TRANSITIONS, on_data=lambda c: transitions_q.put(c.binary)
    )
    status_sub = proc.create_container_subscription(CONTAINER_STATUS, on_data=lambda c: status_q.put(c.binary))

    passed = failed = 0

    def check(name: str, ok: bool, detail: str = ""):
        nonlocal passed, failed
        if ok:
            print(f"  [{GREEN}PASS{RESET}] {name}")
            passed += 1
        else:
            print(f"  [{RED}FAIL{RESET}] {name}" + (f": {detail}" if detail else ""))
            failed += 1

    def issue(cmd: str, args: dict, timeout: float = 5.0):
        c = cmd_conn.issue(cmd, args=args)
        c.await_acknowledgment("Acknowledge_Sent", timeout=timeout)
        return c.await_acknowledgment("CommandComplete", timeout=timeout)

    def query_status(timeout: float = 5.0):
        while not status_q.empty():
            status_q.get_nowait()
        compl = issue(CMD_REPORT_STATUS, {})
        raw = status_q.get(timeout=timeout)
        return compl, parse_status_report(raw)

    def query_defs(pmon_ids: list, timeout: float = 5.0):
        while not defs_q.empty():
            defs_q.get_nowait()
        compl = issue(CMD_REPORT_DEFS, {"N": len(pmon_ids), "pmon_ids": pmon_ids})
        raw = defs_q.get(timeout=timeout)
        return compl, parse_definition_report(raw)

    def query_transitions(timeout: float = 5.0):
        while not transitions_q.empty():
            transitions_q.get_nowait()
        compl = issue(CMD_REPORT_TRANSITIONS, {})
        raw = transitions_q.get(timeout=timeout)
        return compl, parse_transition_report(raw)

    # ── Test 1: Initial state ────────────────────────────────────────────
    print("\nTest 1: Initial state — 4 seed PMONs, all enabled")
    compl, (n, entries) = query_status()
    check("TC[12,13] CommandComplete OK", compl.status == "OK", compl.status)
    check("status_count == 4", n == 4, f"got {n}")
    ids = sorted(pid for pid, _ in entries)
    check("pmon_ids == [1,2,3,4]", ids == [1, 2, 3, 4], f"got {ids}")
    check(
        "all seeds enabled",
        all(status == STATUS_ENABLED for _, status in entries),
        f"got {entries}",
    )

    # ── Test 2: Disable / re-enable PMON 1 ───────────────────────────────
    print("\nTest 2: Disable then re-enable PMON_SINE_TEMP_LIMIT")
    compl = issue(CMD_DISABLE, {"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]})
    check("TC[12,2] CommandComplete OK", compl.status == "OK", compl.status)
    _, (_, entries) = query_status()
    status_by_id = dict(entries)
    check("PMON 1 disabled", status_by_id[PMON_SINE_TEMP_LIMIT] == STATUS_DISABLED, status_by_id)
    compl = issue(CMD_ENABLE, {"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]})
    check("TC[12,1] CommandComplete OK", compl.status == "OK", compl.status)
    _, (_, entries) = query_status()
    status_by_id = dict(entries)
    check("PMON 1 enabled again", status_by_id[PMON_SINE_TEMP_LIMIT] == STATUS_ENABLED, status_by_id)

    # ── Test 3: Report all definitions (N=0) ─────────────────────────────
    print("\nTest 3: TC[12,8] N=0 -> TM[12,9] reports all 4 definitions")
    compl, (n, entries) = query_defs([])
    check("TC[12,8] CommandComplete OK", compl.status == "OK", compl.status)
    check("pmon_def_count == 4", n == 4, f"got {n}")
    by_id = {e["pmon_id"]: e for e in entries}
    check("PMON 1 check_type == LIMIT", by_id[1]["check_type"] == CHECK_TYPE_LIMIT, by_id.get(1))
    check("PMON 3 check_type == DELTA", by_id[3]["check_type"] == CHECK_TYPE_DELTA, by_id.get(3))
    check(
        "PMON 4 check_type == EXPECTED_VALUE",
        by_id[4]["check_type"] == CHECK_TYPE_EXPECTED_VALUE,
        by_id.get(4),
    )

    # ── Test 4: Delete while still enabled -> rejected ───────────────────
    print("\nTest 4: TC[12,6] delete PMON 1 while enabled -> NACK completion")
    compl = issue(CMD_DELETE, {"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]})
    check("TC[12,6] rejected (still enabled)", compl.status != "OK", compl.status)
    _, (n, _) = query_status()
    check("still 4 definitions (nothing deleted)", n == 4, f"got {n}")

    # ── Test 5: Disable, delete, re-add PMON 1 ───────────────────────────
    print("\nTest 5: Disable + delete PMON 1, then TC[12,5] re-add it")
    issue(CMD_DISABLE, {"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]})
    compl = issue(CMD_DELETE, {"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]})
    check("TC[12,6] CommandComplete OK", compl.status == "OK", compl.status)
    _, (n, _) = query_status()
    check("3 definitions remain", n == 3, f"got {n}")

    compl = issue(
        CMD_ADD_LIMIT_F32,
        {
            "pmon_id": "PMON_SINE_TEMP_LIMIT",
            "param_id": "PARAM_SINE_TEMP",
            "monitoring_interval_ms": 1000,
            "repetition_number": 3,
            "low_limit": -10.0,
            "low_event_id": 10,
            "high_limit": 50.0,
            "high_event_id": 11,
        },
    )
    check("TC[12,5] CommandComplete OK", compl.status == "OK", compl.status)
    _, (n, entries) = query_status()
    status_by_id = dict(entries)
    check("4 definitions again", n == 4, f"got {n}")
    check(
        "re-added PMON 1 starts disabled",
        status_by_id[PMON_SINE_TEMP_LIMIT] == STATUS_DISABLED,
        status_by_id,
    )
    compl = issue(CMD_ENABLE, {"N": 1, "pmon_ids": ["PMON_SINE_TEMP_LIMIT"]})
    check("re-enable PMON 1 (restore demo state)", compl.status == "OK", compl.status)

    # ── Test 6: Duplicate add -> rejected ────────────────────────────────
    print("\nTest 6: TC[12,5] duplicate pmon_id -> NACK completion")
    compl = issue(
        CMD_ADD_LIMIT_F32,
        {
            "pmon_id": "PMON_RANDWALK_LIMIT",  # already exists
            "param_id": "PARAM_BUS_CURRENT",
            "monitoring_interval_ms": 1000,
            "repetition_number": 2,
            "low_limit": 1.0,
            "low_event_id": 12,
            "high_limit": 8.0,
            "high_event_id": 13,
        },
    )
    check("TC[12,5] rejected (already exists)", compl.status != "OK", compl.status)

    # ── Test 7: Modify with wrong check_type -> rejected ─────────────────
    print("\nTest 7: TC[12,7] MODIFY_PMON_LIMIT_F32 on an EXPECTED_VALUE def -> NACK")
    compl = issue(
        CMD_MODIFY_LIMIT_F32,
        {
            "pmon_id": "PMON_MODE_EXPECTED",  # actually EXPECTED_VALUE, not LIMIT
            "param_id": "PARAM_MODE_REGISTER",
            "repetition_number": 2,
            "low_limit": 0.0,
            "low_event_id": 15,
            "high_limit": 1.0,
            "high_event_id": 15,
        },
    )
    check("TC[12,7] rejected (check_type mismatch)", compl.status != "OK", compl.status)

    # ── Test 8: Modify with wrong param_id -> rejected ───────────────────
    print("\nTest 8: TC[12,7] MODIFY_PMON_LIMIT_F32 with wrong param_id -> NACK")
    compl = issue(
        CMD_MODIFY_LIMIT_F32,
        {
            "pmon_id": "PMON_SINE_TEMP_LIMIT",  # correct check_type (LIMIT)...
            "param_id": "PARAM_BUS_CURRENT",  # ...but wrong param_id (should be PARAM_SINE_TEMP)
            "repetition_number": 3,
            "low_limit": -10.0,
            "low_event_id": 10,
            "high_limit": 50.0,
            "high_event_id": 11,
        },
    )
    check("TC[12,7] rejected (param_id mismatch)", compl.status != "OK", compl.status)

    # ── Test 9: Valid modify ──────────────────────────────────────────────
    print("\nTest 9: TC[12,7] valid modify — widen PMON 1's limits")
    compl = issue(
        CMD_MODIFY_LIMIT_F32,
        {
            "pmon_id": "PMON_SINE_TEMP_LIMIT",
            "param_id": "PARAM_SINE_TEMP",
            "repetition_number": 3,
            "low_limit": -20.0,
            "low_event_id": 10,
            "high_limit": 60.0,
            "high_event_id": 11,
        },
    )
    check("TC[12,7] CommandComplete OK", compl.status == "OK", compl.status)
    _, (_, entries) = query_defs(["PMON_SINE_TEMP_LIMIT"])
    check("low_limit updated to -20.0", abs(entries[0]["low_limit"] - (-20.0)) < 1e-3, entries[0]["low_limit"])
    check("high_limit updated to 60.0", abs(entries[0]["high_limit"] - 60.0) < 1e-3, entries[0]["high_limit"])

    # restore original seed limits so the script is safe to re-run
    compl = issue(
        CMD_MODIFY_LIMIT_F32,
        {
            "pmon_id": "PMON_SINE_TEMP_LIMIT",
            "param_id": "PARAM_SINE_TEMP",
            "repetition_number": 3,
            "low_limit": -10.0,
            "low_event_id": 10,
            "high_limit": 50.0,
            "high_event_id": 11,
        },
    )
    check("restore original limits (cleanup)", compl.status == "OK", compl.status)

    # ── Test 10: Check-transition report is structurally valid ──────────
    print("\nTest 10: TC[12,11] -> TM[12,12] structurally valid")
    compl, (n, entries) = query_transitions()
    check("TC[12,11] CommandComplete OK", compl.status == "OK", compl.status)
    check("transition_count matches parsed entry count", n == len(entries), f"{n} vs {len(entries)}")

    # ── Cleanup ──────────────────────────────────────────────────────────
    defs_sub.cancel()
    transitions_sub.cancel()
    status_sub.cancel()

    print(f"\n{'─' * 44}")
    print(f"Results: {passed} passed, {failed} failed")
    return failed == 0


if __name__ == "__main__":
    sys.exit(0 if run_tests() else 1)
