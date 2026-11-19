#!/usr/bin/env python3
"""
Manual test for the PUS 20 on-board parameter management.

Scenario:
  1. the on-board parameters /SIMULATOR/ONBOARD/* have no value
  2. TC[20,1] report the values of all the on-board parameters -> TM[20,2], decoded into the parameters
  3. set values like the edit button of the web interface does (the set parameter API call, handled by
     PusParameterManager, which sends a TC[20,3]); the parameters keep their old value
  4. TC[20,1] again: the parameters have the new values
  5. bulk update with the opaque instructions argument of TC[20,3]
  6. group commands: typed arguments, ONE TC[20,3] with several instructions

Run the example first:  ./run-example.sh pus
"""

import struct
import time

from yamcs.client import YamcsClient

client = YamcsClient("localhost:8090")
processor = client.get_processor("pus", "realtime")

PARAMS = [
    "HkReportInterval",
    "BatteryHeaterSetpoint",
    "AdcsMode",
    "TmDownlinkRate",
    "AdcsControllerGain",
    "MagnetometerBiasX",
    "PayloadPowerEnabled",
]
NAMESPACE = "/SIMULATOR/ONBOARD/"


def get():
    processor.issue_command(
        "/PUS20/REPORT_PARAMETER_VALUES",
        args={"num_parameters": len(PARAMS), "parameter_ids": PARAMS},
    )
    time.sleep(1.5)


def show(title):
    print(title)
    for name in PARAMS:
        pv = processor.get_parameter_value(NAMESPACE + name, from_cache=True)
        print(f"  {name:24} {pv.eng_value if pv else None}")


show("Before any TC[20,1] (no values expected, unless TM[20,2] was received before):")
get()
show("After TC[20,1]:")

# the edit button of the web interface
processor.set_parameter_value(NAMESPACE + "HkReportInterval", 250)
processor.set_parameter_value(NAMESPACE + "BatteryHeaterSetpoint", -3.5)
processor.set_parameter_value(NAMESPACE + "AdcsMode", "NADIR_POINTING")
processor.set_parameter_value(NAMESPACE + "PayloadPowerEnabled", False)
time.sleep(1.5)
show("After setting 4 parameters (still the old values):")
get()
show("After TC[20,1] again (new values):")

# bulk update: N, N x (parameter ID, big endian value)
# TmDownlinkRate (4, uint32) = 9600, MagnetometerBiasX (6, int16) = -300
data = bytes([2]) + bytes([4]) + struct.pack(">I", 9600) + bytes([6]) + struct.pack(">h", -300)
processor.issue_command("/PUS20/SET_PARAMETER_VALUES", args={"instructions": data})
time.sleep(1)
get()
show("After the bulk update (TmDownlinkRate = 9600, MagnetometerBiasX = -300):")

# group commands: one TC[20,3] with several instructions and typed, validated values
processor.issue_command(
    "/PUS20/SET_ADCS_CONFIGURATION",
    args={"AdcsMode": "DETUMBLE", "AdcsControllerGain": 2.5, "MagnetometerBiasX": 42},
)
processor.issue_command(
    "/PUS20/SET_THERMAL_AND_POWER_CONFIGURATION",
    args={"BatteryHeaterSetpoint": 12.5, "PayloadPowerEnabled": "ENABLED"},
)
time.sleep(1)
get()
show("After the group commands (DETUMBLE, gain 2.5, bias 42, setpoint 12.5, payload ENABLED):")