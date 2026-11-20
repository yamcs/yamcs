#!/usr/bin/env python3
"""
Manual test for the PUS 11 time-based scheduling with scheduling groups.

Scenario:
  1. enable the scheduler and create scheduling group 5 (enabled)
  2. schedule a command into sub-schedule 1 / group 5 a few seconds in the future
  3. enable sub-schedule 1 (a sub-schedule auto-created by an insert starts disabled)
  4. disable group 5 -> the activity is withheld, the simulator logs the drop
  5. schedule again, leave group 5 enabled -> the command is released
  6. read back the group status (TM[11,27]) and the detail report (shows the group id)

Run the example first and wait for time correlation:  ./run-example.sh pus-frames
"""

import queue
import time
from datetime import datetime, timedelta, timezone

from yamcs.client import YamcsClient

GROUP_ID = 5
SUB_SCHEDULE_ID = 1
APP_DATA_OFFSET = 25


def issue_checked(cmd_conn, name, args=None):
    command = cmd_conn.issue(name, args=args)
    command.await_complete(timeout=15)
    if not command.is_success():
        raise ValueError(f"{name} failed: {command.error}")
    return command


def schedule_command(cmd_conn, command_name, args, delay_seconds):
    t = datetime.now(timezone.utc) + timedelta(seconds=delay_seconds)
    t_str = t.strftime("%Y-%m-%dT%H:%M:%S.001Z")
    print(f"Scheduling {command_name} at {t_str} (group {GROUP_ID}, sub-schedule {SUB_SCHEDULE_ID})")
    command = cmd_conn.issue(
        command_name,
        args=args,
        extra={
            "pus11ScheduleAt": t_str,
            "pus11GroupId": GROUP_ID,
            "pus11SubScheduleId": SUB_SCHEDULE_ID,
        },
    )
    ack = command.await_acknowledgment("Verifier_Scheduled", timeout=15)
    if ack.status != "OK":
        raise ValueError(f"Failed to schedule the command: {ack.status}")
    return command


def received_completion(reports, seqcount, timeout):
    deadline = time.monotonic() + timeout
    while (remaining := deadline - time.monotonic()) > 0:
        try:
            packet = reports.get(timeout=remaining)
        except queue.Empty:
            break
        header = packet[APP_DATA_OFFSET:APP_DATA_OFFSET + 4]
        if (packet[8] == 7 and int.from_bytes(header[:2], "big") & 0x7FF == 1
                and int.from_bytes(header[2:], "big") & 0x3FFF == seqcount):
            return True
    return False


if __name__ == "__main__":
    client = YamcsClient("localhost:8090")
    processor = client.get_processor("pus-frames", "realtime")
    cmd_conn = processor.create_command_connection()
    reports = queue.Queue()
    # The Scheduled verifier completes the ground command when the wrapper is accepted.
    # Watch ST[01] separately to observe completion of the embedded command.
    subscription = processor.create_container_subscription(
        "/PUS/pus-tc-ack", on_data=lambda report: reports.put(report.binary)
    )

    print("Enabling the scheduler and creating scheduling group", GROUP_ID)
    issue_checked(cmd_conn, "/PUS11/ENABLE_SCHEDULER")
    issue_checked(
        cmd_conn,
        "/PUS11/CREATE_GROUPS",
        args={"num_groups": 1, "groups": [{"group_id": GROUP_ID, "status": "enabled"}]},
    )

    # --- withheld run: group disabled before the release time ---
    withheld = schedule_command(cmd_conn, "/SIMULATOR/SWITCH_VOLTAGE_OFF", {"voltage_num": 1}, 6)
    print("scheduled with ccsds-seqcount", withheld.attributes["ccsds-seqcount"])

    issue_checked(
        cmd_conn,
        "/PUS11/ENABLE_SCHEDULE",
        args={"num_schedules": 1, "schedules": [SUB_SCHEDULE_ID]},
    )
    print("Disabling group", GROUP_ID, "- the scheduled command should NOT be released")
    issue_checked(
        cmd_conn,
        "/PUS11/DISABLE_GROUPS",
        args={"num_groups": 1, "group_ids": [GROUP_ID]},
    )
    if received_completion(reports, withheld.attributes["ccsds-seqcount"], timeout=8):
        raise ValueError("A command in the disabled group executed")

    # --- released run: group enabled ---
    issue_checked(
        cmd_conn,
        "/PUS11/ENABLE_GROUPS",
        args={"num_groups": 1, "group_ids": [GROUP_ID]},
    )
    released = schedule_command(cmd_conn, "/SIMULATOR/SWITCH_VOLTAGE_OFF", {"voltage_num": 1}, 6)
    seq = released.attributes["ccsds-seqcount"]
    print("scheduled with ccsds-seqcount", seq, "- this one SHOULD be released")

    # ask for the group status report TM[11,27] and the detail report TM[11,10]
    issue_checked(cmd_conn, "/PUS11/GET_GROUP_STATUS")
    issue_checked(
        cmd_conn,
        "/PUS11/GET_DETAIL_REPORT_BY_ID",
        args={"num_requests": 1, "requests": [{"source_id": 0, "apid": 1, "seqcount": seq}]},
    )
    if not received_completion(reports, seq, timeout=15):
        raise ValueError("The released command did not report completion")
    subscription.cancel()
    print("Done - check the simulator log and the PUS11 parameters / events in Yamcs.")
