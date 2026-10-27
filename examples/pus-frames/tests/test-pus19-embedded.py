#!/usr/bin/env python3
"""
Manual test for the PUS ST[19] event-action service with an embedded TC packet.

The "request" member of /PUS19/TC_19_1_ADD is typed /PUS/EmbeddedTcType (annotated with
Yamcs:EmbeddedTc), so the command post-processor fills in the sequence count and the packet length
of the embedded TC and (embeddedTcCrc: true) appends its CRC. The embedded packet is obtained with a
dry run. As specified by ECSS-E-ST-70-41C 8.19.2.1 the request has no length prefix.

A definition is identified by (app_process_id, event_def_id). The simulator has a single
application process (APID 1) and generates EVENT_1 every 5 seconds (EVENT_2 in the other seconds).

Scenario:
  1. switch voltage 1 on
  2. obtain the binary of SWITCH_VOLTAGE_OFF with a dry run
  3. add an event-action definition: on (1, EVENT_1) execute SWITCH_VOLTAGE_OFF
  4. enable the event-action function and the definition; wait for EVENT_1 -> voltage 1 goes off
  5. report the status (TM[19,7]) and the definitions (TM[19,11])
  6. disable and delete the definition

Check the simulator log: the definition is printed with its sequence count and "CRC valid", and
the stored request is released each time EVENT_1 fires while it is enabled.

Run the example first:  ./run-example.sh pus-frames
"""

import time

from yamcs.client import YamcsClient

APID = 1
EVENT_1 = 1
EVENT_IDS = [{"app_process_id": APID, "event_def_id": EVENT_1}]


def dry_run_binary(processor, command_name, args):
    """the binary of the command as encoded from the MDB (no sequence count, length or CRC)"""
    return processor.issue_command(command_name, args=args, dry_run=True).binary


if __name__ == "__main__":
    client = YamcsClient("localhost:8090")
    processor = client.get_processor("pus-frames", "realtime")
    cmd_conn = processor.create_command_connection()

    print("Switching voltage 1 on")
    cmd_conn.issue("/SIMULATOR/SWITCH_VOLTAGE_ON", args={"voltage_num": 1})

    tc_off = dry_run_binary(processor, "/SIMULATOR/SWITCH_VOLTAGE_OFF", {"voltage_num": 1})
    print("dry run SWITCH_VOLTAGE_OFF:", tc_off.hex())

    command = cmd_conn.issue(
        "/PUS19/TC_19_1_ADD",
        args={
            "N": 1,
            "entries": [{"app_process_id": APID, "event_def_id": EVENT_1, "request": tc_off.hex()}],
        },
    )
    ack = command.await_acknowledgment("Acknowledge_Sent")
    if ack.status != "OK":
        raise ValueError(f"Failed to send the command: {ack.status}")
    print("TC[19,1] ccsds-seqcount:", command.attributes.get("ccsds-seqcount"))
    print("ccsds-seqcount:entries[0].request:", command.attributes.get("ccsds-seqcount:entries[0].request"))
    binary = command.attributes.get("binary")
    print("TC[19,1] binary sent:", binary.hex() if binary else None)

    print("Enabling the event-action function and the definition for (1, EVENT_1)")
    cmd_conn.issue("/PUS19/TC_19_8_ENABLE_FUNCTION")
    cmd_conn.issue("/PUS19/TC_19_4_SPECIFIC", args={"N": 1, "event_ids": EVENT_IDS})
    time.sleep(7)

    cmd_conn.issue("/PUS19/TC_19_6_REPORT_STATUS")
    cmd_conn.issue("/PUS19/TC_19_10_REPORT_DEFS", args={"N": 0, "event_ids": []})
    time.sleep(1)
    print("Event-action status:", processor.get_parameter_value("/PUS19/ea_status_entries").eng_value)
    print("Reported request:", processor.get_parameter_value("/PUS19/ea11_request_data").eng_value.hex())

    print("Disabling and deleting the definition")
    cmd_conn.issue("/PUS19/TC_19_5_SPECIFIC", args={"N": 1, "event_ids": EVENT_IDS})
    cmd_conn.issue("/PUS19/TC_19_2_DELETE", args={"N": 1, "event_ids": EVENT_IDS})
    time.sleep(1)
    print("Done - check the simulator log: the request was released on EVENT_1 with a valid CRC.")
