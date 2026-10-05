#!/usr/bin/env python3
"""
Manual test for the PUS 11 time-based scheduling with scheduling groups.

Scenario:
  1. create scheduling group 5 (enabled)
  2. schedule a command into sub-schedule 1 / group 5 a few seconds in the future
  3. enable sub-schedule 1 (a sub-schedule auto-created by an insert starts disabled)
  4. disable group 5 -> the activity is withheld, the simulator logs the drop
  5. schedule again, leave group 5 enabled -> the command is released
  6. read back the group status (TM[11,27]) and the detail report (shows the group id)

Run the example first:  ./run-example.sh pus-frames
"""

import time
from datetime import datetime, timedelta, timezone

from yamcs.client import YamcsClient

GROUP_ID = 5
SUB_SCHEDULE_ID = 1


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
    ack = command.await_acknowledgment("Acknowledge_Sent")
    if ack.status != "OK":
        raise ValueError(f"Failed to send the command: {ack.status}")
    return command.attributes["ccsds-seqcount"]


if __name__ == "__main__":
    client = YamcsClient("localhost:8090")
    processor = client.get_processor("pus-frames", "realtime")
    cmd_conn = processor.create_command_connection()

    print("Creating scheduling group", GROUP_ID)
    cmd_conn.issue(
        "/PUS11/CREATE_GROUPS",
        args={"num_groups": 1, "groups": [{"group_id": GROUP_ID, "status": "enabled"}]},
    )
    time.sleep(1)

    # --- withheld run: group disabled before the release time ---
    seq = schedule_command(cmd_conn, "/SIMULATOR/SWITCH_VOLTAGE_OFF", {"voltage_num": 1}, 6)
    print("scheduled with ccsds-seqcount", seq)

    cmd_conn.issue(
        "/PUS11/ENABLE_SCHEDULE",
        args={"num_schedules": 1, "schedules": [SUB_SCHEDULE_ID]},
    )
    print("Disabling group", GROUP_ID, "- the scheduled command should NOT be released")
    cmd_conn.issue(
        "/PUS11/DISABLE_GROUPS",
        args={"num_groups": 1, "group_ids": [GROUP_ID]},
    )
    time.sleep(8)

    # --- released run: group enabled ---
    cmd_conn.issue(
        "/PUS11/ENABLE_GROUPS",
        args={"num_groups": 1, "group_ids": [GROUP_ID]},
    )
    seq = schedule_command(cmd_conn, "/SIMULATOR/SWITCH_VOLTAGE_OFF", {"voltage_num": 1}, 6)
    print("scheduled with ccsds-seqcount", seq, "- this one SHOULD be released")

    # ask for the group status report TM[11,27] and the detail report TM[11,10]
    cmd_conn.issue("/PUS11/GET_GROUP_STATUS")
    cmd_conn.issue(
        "/PUS11/GET_DETAIL_REPORT_BY_ID",
        args={"num_requests": 1, "requests": [{"source_id": 0, "apid": "1", "seqcount": seq}]},
    )
    time.sleep(8)
    print("Done - check the simulator log and the PUS11 parameters / events in Yamcs.")
