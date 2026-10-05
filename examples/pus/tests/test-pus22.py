#!/usr/bin/env python3
"""
Manual test for the PUS 22 position-based scheduling with scheduling groups.

Scenario (mirrors test-pus11.py in the pus-frames example, but keyed on an orbit position
instead of a time):
  1. create scheduling group 5 (enabled)
  2. schedule a command into sub-schedule 1 / group 5 a few seconds' worth of orbit angle ahead
  3. enable sub-schedule 1 (a sub-schedule auto-created by an insert starts disabled)
  4. disable group 5 -> the activity is withheld, the simulator logs the drop
  5. schedule again, leave group 5 enabled -> the command is released
  6. read back the group status (TM[22,27]) and the detail report (shows the group id)

Run the example first:  ./run-example.sh pus
"""

import time

from yamcs.client import YamcsClient

GROUP_ID = 5
SUB_SCHEDULE_ID = 1

# the simulator's simulated orbit: 360 degrees per this many seconds (see Pus22Service /
# PusSimulator.DEFAULT_ORBIT_PERIOD_NANOS)
ORBIT_PERIOD_SECONDS = 90 * 60
DEG_PER_SECOND = 360.0 / ORBIT_PERIOD_SECONDS
TICKS_PER_ORBIT = 65536


def current_position_deg(processor):
    orbit_number = processor.get_parameter_value("/SIMULATOR/OrbitNumber").eng_value
    angle_ticks = processor.get_parameter_value("/SIMULATOR/OrbitAngle").eng_value
    return orbit_number, angle_ticks * 360.0 / TICKS_PER_ORBIT


def schedule_command(cmd_conn, processor, command_name, args, angle_ahead_deg):
    orbit_number, angle_deg = current_position_deg(processor)
    target_angle = angle_deg + angle_ahead_deg
    if target_angle >= 360:
        target_angle -= 360
        orbit_number += 1
    print(f"Scheduling {command_name} at orbit {orbit_number}, angle {target_angle:.3f} deg "
          f"(group {GROUP_ID}, sub-schedule {SUB_SCHEDULE_ID})")
    command = cmd_conn.issue(
        command_name,
        args=args,
        extra={
            "pus22OrbitNumber": orbit_number,
            "pus22OrbitAngle": target_angle,
            "pus22GroupId": GROUP_ID,
            "pus22SubScheduleId": SUB_SCHEDULE_ID,
        },
    )
    ack = command.await_acknowledgment("Acknowledge_Sent")
    if ack.status != "OK":
        raise ValueError(f"Failed to send the command: {ack.status}")
    return command.attributes["ccsds-seqcount"]


if __name__ == "__main__":
    client = YamcsClient("localhost:8090")
    processor = client.get_processor("pus", "realtime")
    cmd_conn = processor.create_command_connection()

    print("Creating scheduling group", GROUP_ID)
    cmd_conn.issue(
        "/PUS22/CREATE_GROUPS",
        args={"num_groups": 1, "groups": [{"group_id": GROUP_ID, "status": "enabled"}]},
    )
    time.sleep(1)

    # --- withheld run: group disabled before the release position ---
    seq = schedule_command(cmd_conn, processor, "/SIMULATOR/SWITCH_VOLTAGE_OFF", {"voltage_num": 1},
                            6 * DEG_PER_SECOND)
    print("scheduled with ccsds-seqcount", seq)

    cmd_conn.issue(
        "/PUS22/ENABLE_SCHEDULE",
        args={"num_schedules": 1, "schedules": [SUB_SCHEDULE_ID]},
    )
    print("Disabling group", GROUP_ID, "- the scheduled command should NOT be released")
    cmd_conn.issue(
        "/PUS22/DISABLE_GROUPS",
        args={"num_groups": 1, "group_ids": [GROUP_ID]},
    )
    time.sleep(8)

    # --- released run: group enabled ---
    cmd_conn.issue(
        "/PUS22/ENABLE_GROUPS",
        args={"num_groups": 1, "group_ids": [GROUP_ID]},
    )
    seq = schedule_command(cmd_conn, processor, "/SIMULATOR/SWITCH_VOLTAGE_OFF", {"voltage_num": 1},
                            6 * DEG_PER_SECOND)
    print("scheduled with ccsds-seqcount", seq, "- this one SHOULD be released")

    # ask for the group status report TM[22,27] and the detail report TM[22,10]
    cmd_conn.issue("/PUS22/GET_GROUP_STATUS")
    cmd_conn.issue(
        "/PUS22/GET_DETAIL_REPORT_BY_ID",
        args={"num_requests": 1, "requests": [{"source_id": 0, "apid": "1", "seqcount": seq}]},
    )
    time.sleep(8)
    print("Done - check the simulator log and the PUS22 parameters / events in Yamcs.")
