#!/usr/bin/env python3
"""
Manual test for the PUS ST[19] event-action service with an embedded TC packet.

The action "tc" argument type of /PUS19/ADD_EVENT_ACTIONS is annotated with Yamcs:EmbeddedTc, so
the command post-processor fills in the sequence count and the packet length of the embedded TC and
(embeddedTcCrc: true) appends its CRC. The embedded packet is obtained with a dry run.

The simulator generates EVENT_1 every 5 seconds (and EVENT_2 in the other seconds).

Scenario:
  1. switch voltage 1 on
  2. obtain the binary of SWITCH_VOLTAGE_OFF with a dry run
  3. add an event-action definition: on EVENT_1 execute SWITCH_VOLTAGE_OFF
  4. enable the event-action function and the definition; wait for EVENT_1 -> voltage 1 goes off
  5. report the status (TM[19,7]), then disable and delete the definition

Check the simulator log: the definition is printed with its sequence count and "CRC valid", and
"Event 1 reported, executing the event-action" appears every 5 seconds while it is enabled.

Run the example first:  ./run-example.sh pus-frames
"""

import time

from yamcs.client import YamcsClient


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
        "/PUS19/ADD_EVENT_ACTIONS",
        args={
            "num_definitions": 1,
            "definitions": [{"event_id": "EVENT_1", "tc": tc_off.hex()}],
        },
    )
    ack = command.await_acknowledgment("Acknowledge_Sent")
    if ack.status != "OK":
        raise ValueError(f"Failed to send the command: {ack.status}")
    print("TC[19,1] ccsds-seqcount:", command.attributes.get("ccsds-seqcount"))
    print("ccsds-seqcount:definitions[0].tc:", command.attributes.get("ccsds-seqcount:definitions[0].tc"))
    binary = command.attributes.get("binary")
    print("TC[19,1] binary sent:", binary.hex() if binary else None)

    print("Enabling the event-action function and the definition for EVENT_1")
    cmd_conn.issue("/PUS19/ENABLE_FUNCTION")
    cmd_conn.issue("/PUS19/ENABLE_EVENT_ACTIONS", args={"num_definitions": 1, "event_ids": ["EVENT_1"]})
    time.sleep(7)

    cmd_conn.issue("/PUS19/REPORT_STATUS")
    time.sleep(1)
    print("Event-action status:", processor.get_parameter_value("/PUS19/status_report").eng_value)

    print("Disabling and deleting the definition")
    cmd_conn.issue("/PUS19/DISABLE_EVENT_ACTIONS", args={"num_definitions": 1, "event_ids": ["EVENT_1"]})
    cmd_conn.issue("/PUS19/DELETE_EVENT_ACTIONS", args={"num_definitions": 1, "event_ids": ["EVENT_1"]})
    time.sleep(1)
    print("Done - check the simulator log: the event-action was executed on EVENT_1 with a valid CRC.")
