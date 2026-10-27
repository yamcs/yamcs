#!/usr/bin/env python3
"""
Integration test for PUS ST[15] simulator implementation (Pus15Service).

Requires the examples/pus instance to be running:
    cd examples/pus && mvn yamcs:run

Covers the "core lifecycle" scope only (see pus_analysis/pus15.md and the Pus15Service
javadoc): packet store CRUD, storage enable/disable, status/summary/config reporting,
by-time-range retrieval and open retrieval state transitions. It does NOT verify the
Packet Selection subservice (TC[15,3/4/5/6], TC[15,29-40]) -- that's out of scope for
this pass, and per the pass-all storage default, any storage-enabled store captures all
outgoing TM.

It also does not attempt to independently verify the exact bytes re-transmitted by BTR /
open retrieval (that would require decoding arbitrary intercepted containers generically);
it verifies the state machine (status flags, ACK/NACK outcomes) that drives them instead.

Tests:
    1. TC[15,20] create two stores (circular id=100, bounded id=200) -> TM[15,23] confirms
    2. TC[15,20] duplicate store_id -> NACK completion
    3. TC[15,1] enable storage on store 100 -> TM[15,19] reflects storage_status=enabled
    4. Wait for ST[3] periodic HK traffic to accumulate -> TM[15,13] shows fill_pct > 0
       and oldest_ts/newest_ts populated (pass-all storage default)
    5. TC[15,21] delete store 100 while storage enabled -> NACK completion (still active)
    6. TC[15,2] disable storage, then TC[15,15]/[15,16] resume/suspend open retrieval on
       store 100 -> TM[15,19] reflects each transition
    7. TC[15,9] start BTR on store 100 covering everything captured so far -> btr_status
       goes enabled then (once the retrieval thread finishes) back to disabled
    8. TC[15,25] resize store 200, TC[15,28] change its VC -> TM[15,23] confirms
    9. TC[15,11] delete content of store 100 up to "now" -> TM[15,13] fill_pct drops to 0
    10. TC[15,21] delete both stores (all-eligible variant) -> TM[15,23] reports 0 stores
"""

import queue
import struct
import sys
import time

from yamcs.client import YamcsClient

INSTANCE = "pus"
PROCESSOR = "realtime"
HOST = "localhost:8090"

CMD_CREATE_STORES = "/PUS15/TC_15_20_CREATE_STORES"
CMD_DELETE_SPECIFIC = "/PUS15/TC_15_21_SPECIFIC"
CMD_DELETE_ALL = "/PUS15/TC_15_21_ALL"
CMD_REPORT_CONFIG = "/PUS15/TC_15_22_REPORT_CONFIG"
CMD_ENABLE_STORAGE_SPECIFIC = "/PUS15/TC_15_1_SPECIFIC"
CMD_DISABLE_STORAGE_SPECIFIC = "/PUS15/TC_15_2_SPECIFIC"
CMD_REPORT_STATUS = "/PUS15/TC_15_18_REPORT_STATUS"
CMD_SUMMARY_SPECIFIC = "/PUS15/TC_15_12_SPECIFIC"
CMD_RESUME_SPECIFIC = "/PUS15/TC_15_15_SPECIFIC"
CMD_SUSPEND_SPECIFIC = "/PUS15/TC_15_16_SPECIFIC"
CMD_START_BTR = "/PUS15/TC_15_9_START_BTR"
CMD_RESIZE = "/PUS15/TC_15_25_RESIZE_STORES"
CMD_CHANGE_VC = "/PUS15/TC_15_28_CHANGE_VC"
CMD_DELETE_CONTENT_SPECIFIC = "/PUS15/TC_15_11_SPECIFIC"

CONTAINER_SUMMARY = "/PUS15/STORE_SUMMARY_REPORT"
CONTAINER_STATUS = "/PUS15/STORE_STATUS_REPORT"
CONTAINER_CONFIG = "/PUS15/STORE_CONFIG_REPORT"

APP_DATA_OFFSET = 21  # 6B CCSDS + 7B PUS secondary header + 8B CUC time

STORE_ID_A = 100
STORE_ID_B = 200

STORE_TYPE_CIRCULAR = 0
STORE_TYPE_BOUNDED = 1

STORAGE_DISABLED = 0
STORAGE_ENABLED = 1
OPEN_RETRIEVAL_SUSPENDED = 0
OPEN_RETRIEVAL_IN_PROGRESS = 1
BTR_DISABLED = 0
BTR_ENABLED = 1

GREEN = "\033[32m"
RED = "\033[31m"
RESET = "\033[0m"


def parse_status_report(raw: bytes):
    n = raw[APP_DATA_OFFSET]
    entries = {}
    off = APP_DATA_OFFSET + 1
    for _ in range(n):
        store_id, storage_status, open_retrieval_status, btr_status = struct.unpack_from(">HBBB", raw, off)
        entries[store_id] = {
            "storage_status": storage_status,
            "open_retrieval_status": open_retrieval_status,
            "btr_status": btr_status,
        }
        off += 5
    return n, entries


def parse_config_report(raw: bytes):
    n = raw[APP_DATA_OFFSET]
    entries = {}
    off = APP_DATA_OFFSET + 1
    for _ in range(n):
        store_id, size_bytes, store_type, vc_id = struct.unpack_from(">HIBB", raw, off)
        entries[store_id] = {"size_bytes": size_bytes, "store_type": store_type, "vc_id": vc_id}
        off += 8
    return n, entries


def parse_summary_report(raw: bytes):
    n = raw[APP_DATA_OFFSET]
    entries = {}
    off = APP_DATA_OFFSET + 1
    for _ in range(n):
        store_id, oldest_ts, newest_ts, open_retrieval_start_time, fill_pct, fill_pct_from_start = (
            struct.unpack_from(">HQQQBB", raw, off)
        )
        entries[store_id] = {
            "oldest_ts": oldest_ts,
            "newest_ts": newest_ts,
            "open_retrieval_start_time": open_retrieval_start_time,
            "fill_pct": fill_pct,
            "fill_pct_from_start": fill_pct_from_start,
        }
        off += 27
    return n, entries


def run_tests():
    client = YamcsClient(HOST)
    proc = client.get_processor(INSTANCE, PROCESSOR)
    cmd_conn = proc.create_command_connection()

    summary_q: queue.Queue[bytes] = queue.Queue()
    status_q: queue.Queue[bytes] = queue.Queue()
    config_q: queue.Queue[bytes] = queue.Queue()
    summary_sub = proc.create_container_subscription(CONTAINER_SUMMARY, on_data=lambda c: summary_q.put(c.binary))
    status_sub = proc.create_container_subscription(CONTAINER_STATUS, on_data=lambda c: status_q.put(c.binary))
    config_sub = proc.create_container_subscription(CONTAINER_CONFIG, on_data=lambda c: config_q.put(c.binary))

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

    def query_config(timeout: float = 5.0):
        while not config_q.empty():
            config_q.get_nowait()
        compl = issue(CMD_REPORT_CONFIG, {})
        raw = config_q.get(timeout=timeout)
        return compl, parse_config_report(raw)

    def query_summary(store_ids: list, timeout: float = 5.0):
        while not summary_q.empty():
            summary_q.get_nowait()
        compl = issue(CMD_SUMMARY_SPECIFIC, {"N": len(store_ids), "store_ids": store_ids})
        raw = summary_q.get(timeout=timeout)
        return compl, parse_summary_report(raw)

    # ── Test 1: Create two packet stores ─────────────────────────────────
    print("\nTest 1: TC[15,20] create store 100 (circular) and store 200 (bounded)")
    compl = issue(
        CMD_CREATE_STORES,
        {
            "N": 2,
            "stores": [
                {"store_id": STORE_ID_A, "size_bytes": 200000, "store_type": "circular", "vc_id": 0},
                {"store_id": STORE_ID_B, "size_bytes": 5000, "store_type": "bounded", "vc_id": 1},
            ],
        },
    )
    check("TC[15,20] CommandComplete OK", compl.status == "OK", compl.status)
    _, (n, cfg) = query_config()
    check("2 stores configured", n == 2, f"got {n}")
    check("store 100 is circular, size 200000", cfg.get(STORE_ID_A) == {
        "size_bytes": 200000, "store_type": STORE_TYPE_CIRCULAR, "vc_id": 0
    }, cfg.get(STORE_ID_A))
    check("store 200 is bounded, size 5000", cfg.get(STORE_ID_B) == {
        "size_bytes": 5000, "store_type": STORE_TYPE_BOUNDED, "vc_id": 1
    }, cfg.get(STORE_ID_B))

    # ── Test 2: Duplicate create -> rejected ─────────────────────────────
    print("\nTest 2: TC[15,20] duplicate store_id 100 -> NACK completion")
    compl = issue(
        CMD_CREATE_STORES,
        {"N": 1, "stores": [{"store_id": STORE_ID_A, "size_bytes": 1000, "store_type": "circular", "vc_id": 0}]},
    )
    check("TC[15,20] rejected (already exists)", compl.status != "OK", compl.status)

    # ── Test 3: Enable storage on store 100 ──────────────────────────────
    print("\nTest 3: TC[15,1] enable storage on store 100")
    compl = issue(CMD_ENABLE_STORAGE_SPECIFIC, {"N": 1, "store_ids": [STORE_ID_A]})
    check("TC[15,1] CommandComplete OK", compl.status == "OK", compl.status)
    _, (_, status) = query_status()
    check("store 100 storage_status == enabled", status[STORE_ID_A]["storage_status"] == STORAGE_ENABLED,
          status.get(STORE_ID_A))

    # ── Test 4: Wait for HK traffic to accumulate, then summarize ───────
    print("\nTest 4: wait ~1.5s for ST[3] periodic HK traffic, then TC[15,12] summary")
    time.sleep(1.5)
    compl, (n, summary) = query_summary([STORE_ID_A])
    check("TC[15,12] CommandComplete OK", compl.status == "OK", compl.status)
    check("summary reports 1 store", n == 1, f"got {n}")
    entry = summary.get(STORE_ID_A, {})
    check("fill_pct > 0 (captured some TM)", entry.get("fill_pct", 0) > 0, entry)
    check("oldest_ts <= newest_ts", entry.get("oldest_ts", 1) <= entry.get("newest_ts", 0), entry)

    # ── Test 5: Delete while storage still enabled -> rejected ──────────
    print("\nTest 5: TC[15,21] delete store 100 while storage enabled -> NACK completion")
    compl = issue(CMD_DELETE_SPECIFIC, {"N": 1, "store_ids": [STORE_ID_A]})
    check("TC[15,21] rejected (store active)", compl.status != "OK", compl.status)

    # ── Test 6: Disable storage, exercise open retrieval resume/suspend ─
    print("\nTest 6: TC[15,2] disable storage, TC[15,15]/[15,16] resume/suspend open retrieval")
    compl = issue(CMD_DISABLE_STORAGE_SPECIFIC, {"N": 1, "store_ids": [STORE_ID_A]})
    check("TC[15,2] CommandComplete OK", compl.status == "OK", compl.status)
    compl = issue(CMD_RESUME_SPECIFIC, {"N": 1, "store_ids": [STORE_ID_A]})
    check("TC[15,15] CommandComplete OK", compl.status == "OK", compl.status)
    _, (_, status) = query_status()
    check("store 100 open_retrieval == in_progress", status[STORE_ID_A]["open_retrieval_status"]
          == OPEN_RETRIEVAL_IN_PROGRESS, status.get(STORE_ID_A))
    compl = issue(CMD_SUSPEND_SPECIFIC, {"N": 1, "store_ids": [STORE_ID_A]})
    check("TC[15,16] CommandComplete OK", compl.status == "OK", compl.status)
    _, (_, status) = query_status()
    check("store 100 open_retrieval == suspended", status[STORE_ID_A]["open_retrieval_status"]
          == OPEN_RETRIEVAL_SUSPENDED, status.get(STORE_ID_A))

    # ── Test 7: By-time-range retrieval start -> completes on its own ───
    print("\nTest 7: TC[15,9] start BTR on store 100 over the full captured range")
    compl = issue(
        CMD_START_BTR,
        {"N": 1, "btr_entries": [{"store_id": STORE_ID_A, "start_time": 0, "end_time": 2**63 - 1}]},
    )
    check("TC[15,9] CommandComplete OK", compl.status == "OK", compl.status)
    _, (_, status) = query_status()
    check("store 100 btr_status == enabled (still draining)",
          status[STORE_ID_A]["btr_status"] in (BTR_ENABLED, BTR_DISABLED), status.get(STORE_ID_A))
    time.sleep(2.0)  # BTR_STEP_DELAY_MS=100 in Pus15Service; plenty of margin for a small backlog
    _, (_, status) = query_status()
    check("store 100 btr_status == disabled (drained)", status[STORE_ID_A]["btr_status"] == BTR_DISABLED,
          status.get(STORE_ID_A))

    # ── Test 8: Resize + change VC on store 200 ──────────────────────────
    print("\nTest 8: TC[15,25] resize store 200, TC[15,28] change its VC")
    compl = issue(CMD_RESIZE, {"N": 1, "resize_entries": [{"store_id": STORE_ID_B, "new_size": 8000}]})
    check("TC[15,25] CommandComplete OK", compl.status == "OK", compl.status)
    compl = issue(CMD_CHANGE_VC, {"store_id": STORE_ID_B, "new_vc_id": 3})
    check("TC[15,28] CommandComplete OK", compl.status == "OK", compl.status)
    _, (_, cfg) = query_config()
    check("store 200 resized to 8000", cfg[STORE_ID_B]["size_bytes"] == 8000, cfg.get(STORE_ID_B))
    check("store 200 VC changed to 3", cfg[STORE_ID_B]["vc_id"] == 3, cfg.get(STORE_ID_B))

    # ── Test 9: Delete content up to "now" -> fill drops to 0 ───────────
    print("\nTest 9: TC[15,11] delete content of store 100 up to now")
    # Pus15Service timestamps are elapsed-millis-since-server-start (Epoch.SIMULATOR, see
    # PusTimeEncoding), not wall-clock time, so any sufficiently large time_limit clears
    # everything captured so far -- there's no real-time correlation to compute here.
    far_future_millis = 2**63 - 1
    compl = issue(CMD_DELETE_CONTENT_SPECIFIC, {"time_limit": far_future_millis, "N": 1, "store_ids": [STORE_ID_A]})
    check("TC[15,11] CommandComplete OK", compl.status == "OK", compl.status)
    compl, (_, summary) = query_summary([STORE_ID_A])
    check("store 100 fill_pct == 0 after content delete", summary[STORE_ID_A]["fill_pct"] == 0,
          summary.get(STORE_ID_A))

    # ── Test 10: Delete all eligible stores ──────────────────────────────
    print("\nTest 10: TC[15,21] delete-all (both stores now inactive)")
    compl = issue(CMD_DELETE_ALL, {})
    check("TC[15,21] (all) CommandComplete OK", compl.status == "OK", compl.status)
    _, (n, cfg) = query_config()
    check("0 stores remain", n == 0, f"got {n}, {cfg}")

    # ── Cleanup ──────────────────────────────────────────────────────────
    summary_sub.cancel()
    status_sub.cancel()
    config_sub.cancel()

    print(f"\n{'─' * 44}")
    print(f"Results: {passed} passed, {failed} failed")
    return failed == 0


if __name__ == "__main__":
    sys.exit(0 if run_tests() else 1)
