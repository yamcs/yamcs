from yamcs.client import YamcsClient

from datetime import datetime, timedelta, timezone

def schedule_command(cmd_conn, command_name, args, scheduled_time):
    t_str = scheduled_time.strftime("%Y-%m-%dT%H:%M:%S.001Z")
   
    # Issue command with scheduling
    print(f"Scheduling {command_name} at {t_str}")
    command = cmd_conn.issue(
        command_name,
        args=args,
        extra={"pus11ScheduleAt": t_str}
    )
    
    ack = command.await_acknowledgment("Acknowledge_Sent")
    if ack.status != "OK":
        raise ValueError("Failed to send the command: {}".format(ack.status))
    
    return command.attributes["ccsds-seqcount"]

if __name__ == "__main__":
    client = YamcsClient("localhost:8090")
    processor = client.get_processor("pus", "realtime")
    cmd_conn = processor.create_command_connection()
    
    t = datetime.now(timezone.utc) + timedelta(hours=1)
    seq1 = schedule_command(
        cmd_conn, "/SIMULATOR/SWITCH_VOLTAGE_OFF", {"voltage_num": 1}, t
    )
    print(f"VOLTAGE_OFF sequence count: {seq1}")
    
    cmd_conn.issue(command='/PUS11/GET_DETAIL_REPORT_BY_ID', args = {"num_requests": 1, "requests": [{"source_id": 0, "apid": "1", "seqcount": seq1}]})
    
