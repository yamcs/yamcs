#!/usr/bin/env python3
"""
Integration test for PUS ST[05] native YAMCS implementation (Pus5Service).

Requires the examples/pus instance to be running:
    cd examples/pus && mvn yamcs:run

Tests:
    1. Initial state: TC[5,7] → TM[5,8] with empty disabled list
    2. TC[5,6] disable EVENT_1 → list contains EVENT_1
    3. TC[5,5] re-enable EVENT_1 → list is empty again
    4. TC[5,6] disable both events in one TC → list contains both
    5. TC[5,5] enable both events in one TC → list is empty again
    6. Idempotency: disable already-disabled event → still OK, count unchanged

TM[5,8] binary layout (from PusCommandReleaser.buildPusTmPacket with pfield=0x2F):
    [0..5]   6B CCSDS primary header
    [6..12]  7B PUS TM secondary header (version, type, subtype, counter(2), dest(2))
    [13..20] 8B CUC time (1B pfield=0x2F + 4B coarse + 3B fine, pfield not implicit)
    [21]     N  — number of disabled events
    [22..]   N × uint8 event IDs
"""

import queue
import sys

from yamcs.client import YamcsClient

INSTANCE = "pus"
PROCESSOR = "realtime"
HOST = "localhost:8090"

CMD_ENABLE  = "/PUS5/ENABLE_REPORT_GENERATION"
CMD_DISABLE = "/PUS5/DISABLE_REPORT_GENERATION"
CMD_REPORT  = "/PUS5/REPORT_DISABLED_LIST"
CONTAINER   = "/PUS5/pus5-disabled-list"

# Byte offset where app data starts in native TM[5,8] packets
# 6B CCSDS + 7B PUS secondary header + 8B CUC time (pfield=0x2F, not implicit)
APP_DATA_OFFSET = 21

GREEN = "\033[32m"
RED   = "\033[31m"
RESET = "\033[0m"


def run_tests():
    client = YamcsClient(HOST)
    proc = client.get_processor(INSTANCE, PROCESSOR)
    cmd_conn = proc.create_command_connection()

    # Receive TM[5,8] packets via container subscription
    tm58_queue: queue.Queue[bytes] = queue.Queue()
    container_sub = proc.create_container_subscription(
        CONTAINER,
        on_data=lambda c: tm58_queue.put(c.binary),
    )

    passed = failed = 0

    def check(name: str, ok: bool, detail: str = ""):
        nonlocal passed, failed
        if ok:
            print(f"  [{GREEN}PASS{RESET}] {name}")
            passed += 1
        else:
            print(f"  [{RED}FAIL{RESET}] {name}" + (f": {detail}" if detail else ""))
            failed += 1

    def query_disabled(timeout: float = 5.0):
        """Issue TC[5,7] and return (count, [id, ...]) parsed from TM[5,8]."""
        while not tm58_queue.empty():
            tm58_queue.get_nowait()
        cmd = cmd_conn.issue(CMD_REPORT)
        cmd.await_acknowledgment("Acknowledge_Sent", timeout=timeout)
        cmd.await_acknowledgment("CommandComplete", timeout=timeout)
        raw = tm58_queue.get(timeout=timeout)
        n = raw[APP_DATA_OFFSET]
        ids = list(raw[APP_DATA_OFFSET + 1 : APP_DATA_OFFSET + 1 + n])
        return n, ids

    def tc_disable(events: list, timeout: float = 5.0):
        cmd = cmd_conn.issue(CMD_DISABLE, args={"N": len(events), "events": events})
        cmd.await_acknowledgment("Acknowledge_Sent", timeout=timeout)
        return cmd.await_acknowledgment("CommandComplete", timeout=timeout)

    def tc_enable(events: list, timeout: float = 5.0):
        cmd = cmd_conn.issue(CMD_ENABLE, args={"N": len(events), "events": events})
        cmd.await_acknowledgment("Acknowledge_Sent", timeout=timeout)
        return cmd.await_acknowledgment("CommandComplete", timeout=timeout)

    # Reset state: ensure all events are enabled before running tests
    tc_enable(["EVENT_1", "EVENT_2"])

    # ── Test 1: Initial state ────────────────────────────────────────────
    print("\nTest 1: Initial state — all events enabled")
    count, ids = query_disabled()
    check("disabled_count == 0", count == 0, f"got {count}")
    check("disabled list is empty", ids == [], f"got {ids}")

    # ── Test 2: Disable EVENT_1 ──────────────────────────────────────────
    print("\nTest 2: Disable EVENT_1")
    compl = tc_disable(["EVENT_1"])
    check("TC[5,6] CommandComplete OK", compl.status == "OK", compl.status)
    count, ids = query_disabled()
    check("disabled_count == 1", count == 1, f"got {count}")
    check("EVENT_1 (id=1) in disabled list", 1 in ids, f"got {ids}")
    check("EVENT_2 not in disabled list", 2 not in ids, f"got {ids}")

    # ── Test 3: Re-enable EVENT_1 ────────────────────────────────────────
    print("\nTest 3: Re-enable EVENT_1")
    compl = tc_enable(["EVENT_1"])
    check("TC[5,5] CommandComplete OK", compl.status == "OK", compl.status)
    count, ids = query_disabled()
    check("disabled_count == 0", count == 0, f"got {count}")
    check("list empty after re-enable", ids == [], f"got {ids}")

    # ── Test 4: Disable both events in one TC ────────────────────────────
    print("\nTest 4: Disable EVENT_1 and EVENT_2 in one TC[5,6]")
    compl = tc_disable(["EVENT_1", "EVENT_2"])
    check("TC[5,6] CommandComplete OK", compl.status == "OK", compl.status)
    count, ids = query_disabled()
    check("disabled_count == 2", count == 2, f"got {count}")
    check("EVENT_1 (id=1) in list", 1 in ids, f"got {ids}")
    check("EVENT_2 (id=2) in list", 2 in ids, f"got {ids}")

    # ── Test 5: Enable both events in one TC ─────────────────────────────
    print("\nTest 5: Enable EVENT_1 and EVENT_2 in one TC[5,5]")
    compl = tc_enable(["EVENT_1", "EVENT_2"])
    check("TC[5,5] CommandComplete OK", compl.status == "OK", compl.status)
    count, ids = query_disabled()
    check("disabled_count == 0", count == 0, f"got {count}")
    check("list empty after enabling both", ids == [], f"got {ids}")

    # ── Test 6: Idempotency ──────────────────────────────────────────────
    print("\nTest 6: Idempotency — disable EVENT_1 twice")
    tc_disable(["EVENT_1"])
    compl = tc_disable(["EVENT_1"])
    check("second TC[5,6] for same event still OK", compl.status == "OK", compl.status)
    count, ids = query_disabled()
    check("disabled_count still 1 (not doubled)", count == 1, f"got {count}")
    check("EVENT_1 (id=1) in list", 1 in ids, f"got {ids}")

    # ── Cleanup ──────────────────────────────────────────────────────────
    tc_enable(["EVENT_1", "EVENT_2"])
    container_sub.cancel()

    print(f"\n{'─' * 44}")
    print(f"Results: {passed} passed, {failed} failed")
    return failed == 0


if __name__ == "__main__":
    sys.exit(0 if run_tests() else 1)
