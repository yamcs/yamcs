#!/usr/bin/env python
"""
ST[20] on-board parameter management (ECSS-E-ST-70-41C, 6.20 and 8.20).

Generates pus20.xml using pymdb.

Implemented TC[20,1], TM[20,2], TC[20,3].
Messages related to parameter definitions are not supported (TC[20,4], TC[20,5], TC[20,6], TM[20,7]).

The value field of TM[20,2] and TC[20,3] has a size deduced from the onboard parameter identified by the
parameter ID. The deduction is done from the simulator MDB (landing.xml): each onboard parameter is a simulator
parameter in /SIMULATOR/ONBOARD/ with an alias in the namespace PARAMETER_ID_NAMESPACE, the alias being the label/name
of the parameter ID enumeration below. Keep the two in sync.

Limitation of TC[20,3]: XTCE cannot type a command argument based on the value of a preceding argument. The
command therefore carries the application data as opaque bytes: N followed by N x (parameter ID, value), where
the value is encoded in big endian according to the type of the onboard parameter (see PARAMETERS below).

This MDB also contains some convenience commands to set multiple onboard parameters at once, see GROUPS.
"""
import yamcs.pymdb as Y

PARAMETER_ID_NAMESPACE = "PUS20_Parameter_ID"

# (parameter id, name of the on-board parameter in the simulator MDB (/SIMULATOR/ONBOARD/<name>))
# The names are also the labels of the parameter ID enumeration.
PARAMETERS = [
    (1, "HkReportInterval"),  # uint16, ms
    (2, "BatteryHeaterSetpoint"),  # float32, degC
    (3, "AdcsMode"),  # enumerated uint8
    (4, "TmDownlinkRate"),  # uint32, bit/s
    (5, "AdcsControllerGain"),  # float64
    (6, "MagnetometerBiasX"),  # int16, nT
    (7, "PayloadPowerEnabled"),  # boolean, uint8
]

# How the value of each on-board parameter is encoded on board (big endian) and how it is presented as a command
# argument. This has to match the types in landing.xml (and Pus20Service):
# kind: uint, int, float, enum, bool; bits: the size of the value; minimum/maximum: limits checked when
# the command is built (not enforced on board, the simulator accepts any value that fits)
PARAMETER_TYPES = {
    "HkReportInterval": dict(
        kind="uint", bits=16, unit="ms", minimum=100, maximum=60000,
        description="Housekeeping report interval",
    ),
    "BatteryHeaterSetpoint": dict(
        kind="float", bits=32, unit="degC", minimum=-20.0, maximum=40.0,
        description="Battery heater thermostat target temperature",
    ),
    "AdcsMode": dict(
        kind="enum", bits=8,
        choices=[(0, "SAFE"), (1, "DETUMBLE"), (2, "SUN_POINTING"), (3, "NADIR_POINTING")],
        description="Attitude control mode",
    ),
    "TmDownlinkRate": dict(
        kind="uint", bits=32, unit="bit/s", minimum=1200, maximum=2000000,
        description="Telemetry downlink data rate",
    ),
    "AdcsControllerGain": dict(
        kind="float", bits=64, minimum=0.0,
        description="Proportional gain of the attitude control loop",
    ),
    "MagnetometerBiasX": dict(
        kind="int", bits=16, unit="nT", minimum=-10000, maximum=10000,
        description="Magnetometer X-axis bias",
    ),
    "PayloadPowerEnabled": dict(
        kind="bool", bits=8, zero="DISABLED", one="ENABLED",
        description="Payload power switch",
    ),
}

# Groups of parameters that are set together with one TC[20,3] (see group_command below)
GROUPS = [
    (
        "SET_ADCS_CONFIGURATION",
        "Sets the attitude control mode, the controller gain and the magnetometer bias",
        ["AdcsMode", "AdcsControllerGain", "MagnetometerBiasX"],
    ),
    (
        "SET_TELEMETRY_CONFIGURATION",
        "Sets the housekeeping report interval and the telemetry downlink rate",
        ["HkReportInterval", "TmDownlinkRate"],
    ),
    (
        "SET_THERMAL_AND_POWER_CONFIGURATION",
        "Sets the battery heater setpoint and the payload power switch",
        ["BatteryHeaterSetpoint", "PayloadPowerEnabled"],
    ),
    (
        "SET_ALL_PARAMETERS",
        "Sets all the on-board parameters",
        [name for _, name in PARAMETERS],
    ),
]

system = Y.System("PUS20")

parameter_id_choices = [(i, name) for i, name in PARAMETERS]


def uint(bits):
    return Y.IntegerEncoding(bits=bits)


def parameter_id_enc():
    return Y.IntegerEncoding(bits=8)


# TELEMETRY
pus20_tm = Y.Container(
    system,
    "pus20-tm",
    base="/PUS/pus-tm",
    condition=Y.EqExpression("/PUS/type", 20),
)

# TM[20,2]: N, N x (parameter ID, value)
report_n = Y.IntegerParameter(
    system,
    "parameter_value_report_n",
    signed=False,
    bits=8,
    encoding=uint(8),
    short_description="Number of parameter values in the report",
)
parameter_id = Y.EnumeratedParameter(
    system,
    "parameter_id",
    choices=parameter_id_choices,
    encoding=parameter_id_enc(),
    short_description="On-board parameter ID",
)

# A (parameter ID, value) pair. The value is decoded using the type of the parameter that has, in the namespace
# PARAMETER_ID_NAMESPACE, the alias equal to the preceding parameter ID.
parameter_value_notification = Y.Container(
    system,
    "parameter_value_notification",
    entries=[
        Y.ParameterEntry(parameter_id),
        Y.IndirectParameterEntry(parameter_id, alias_namespace=PARAMETER_ID_NAMESPACE),
    ],
)

Y.Container(
    system,
    "PARAMETER_VALUE_REPORT",
    base=pus20_tm,
    condition=Y.EqExpression("/PUS/subtype", 2),
    entries=[
        Y.ParameterEntry(report_n),
        Y.ContainerEntry(
            parameter_value_notification, repeat=Y.ParameterValue(report_n)
        ),
    ],
    short_description="TM[20,2] parameter value report",
)

# TM[1,4] failed start of execution: for the failure code UNKNOWN_PARAMETER (12, see the start-failure-code
# enumeration in pus.xml), the failure data is the ID of the unknown parameter (the failed instruction).
failed_parameter_id = Y.IntegerParameter(
    system,
    "start_failure_parameter_id",
    signed=False,
    bits=8,
    encoding=uint(8),
    short_description="ID of the parameter of the instruction that failed to start (TM[1,4], UNKNOWN_PARAMETER)",
)
Y.Container(
    system,
    "START_FAILURE_UNKNOWN_PARAMETER",
    base="/PUS/pus-tc-ack-start-failure",
    condition=Y.EqExpression("/PUS/tc-ack-start-failure-code", 12, calibrated=False),
    entries=[Y.ParameterEntry(failed_parameter_id)],
    short_description="TM[1,4] failed start of execution of a ST[20] instruction with an unknown parameter",
)

# TELECOMMANDS
pus20_tc = Y.Command(
    system,
    "pus20-tc",
    base="/PUS/pus-tc",
    abstract=True,
    assignments={"apid": 1, "type": 20},
)

# TC[20,1]: N, N x parameter ID
report_n_arg = Y.IntegerArgument(
    "num_parameters",
    signed=False,
    bits=8,
    encoding=uint(8),
    short_description="Number of parameter IDs",
)
Y.Command(
    system,
    "REPORT_PARAMETER_VALUES",
    base=pus20_tc,
    assignments={"subtype": 1},
    short_description="TC[20,1] report parameter values",
    arguments=[
        report_n_arg,
        Y.ArrayArgument(
            "parameter_ids",
            data_type=Y.EnumeratedArgument(
                "parameter_id",
                choices=parameter_id_choices,
                encoding=parameter_id_enc(),
            ),
            length=Y.ArgumentValue(report_n_arg),
        ),
    ],
)

# TC[20,3]: N, N x (parameter ID, value). See the limitation described in the module documentation.
Y.Command(
    system,
    "SET_PARAMETER_VALUES",
    base=pus20_tc,
    assignments={"subtype": 3},
    short_description="TC[20,3] set parameter values",
    long_description=(
        "The instructions argument contains the application data of TC[20,3] as hex: N (1 byte) followed by "
        "N x (parameter ID (1 byte), value). The value is big endian and has the size of the on-board parameter: "
        + ", ".join(f"{i}={name}" for i, name in PARAMETERS)
        + ". The sizes of the parameters are defined in the simulator MDB."
    ),
    arguments=[
        Y.BinaryArgument(
            "instructions",
            encoding=Y.BinaryEncoding(bits=-1),
        )
    ],
)



# TC[20,3] with several instructions and typed values: fixed N and parameter IDs, typed value arguments
PARAMETER_IDS = {name: i for i, name in PARAMETERS}


def value_argument(name):
    """The command argument for the value of the on-board parameter, encoded as on board (big endian)"""
    spec = PARAMETER_TYPES[name]
    kind, bits = spec["kind"], spec["bits"]
    common = dict(short_description=spec["description"])
    if kind == "uint":
        return Y.IntegerArgument(
            name, signed=False, bits=bits, minimum=spec.get("minimum"), maximum=spec.get("maximum"),
            units=spec.get("unit"), encoding=Y.IntegerEncoding(bits=bits), **common)
    if kind == "int":
        return Y.IntegerArgument(
            name, signed=True, bits=bits, minimum=spec.get("minimum"), maximum=spec.get("maximum"),
            units=spec.get("unit"),
            encoding=Y.IntegerEncoding(bits=bits, scheme=Y.IntegerEncodingScheme.TWOS_COMPLEMENT), **common)
    if kind == "float":
        return Y.FloatArgument(
            name, bits=bits, minimum=spec.get("minimum"), maximum=spec.get("maximum"),
            units=spec.get("unit"), encoding=Y.FloatEncoding(bits=bits), **common)
    if kind == "enum":
        return Y.EnumeratedArgument(name, choices=spec["choices"], encoding=Y.IntegerEncoding(bits=bits), **common)
    if kind == "bool":
        return Y.BooleanArgument(
            name, zero_string_value=spec["zero"], one_string_value=spec["one"],
            encoding=Y.IntegerEncoding(bits=bits), **common)
    raise ValueError(f"Unknown kind {kind} for {name}")


def group_command(command_name, description, names):
    """
    A TC[20,3] with one instruction per parameter, in the order given. The number of instructions and the
    parameter IDs are fixed, the values are the arguments of the command.
    """
    arguments = [value_argument(name) for name in names]
    entries = [Y.FixedValueEntry(bytes([len(names)]), name="number_of_instructions")]
    for name, argument in zip(names, arguments):
        entries.append(Y.FixedValueEntry(bytes([PARAMETER_IDS[name]]), name=f"{name}_id"))
        entries.append(Y.ArgumentEntry(argument))
    return Y.Command(
        system,
        command_name,
        base=pus20_tc,
        assignments={"subtype": 3},
        short_description=f"TC[20,3] set parameter values: {description}",
        long_description=(
            f"{description}. Sends one TC[20,3] with {len(names)} instruction(s), one for each of: "
            + ", ".join(names)
            + ". All the values are required."
        ),
        arguments=arguments,
        entries=entries,
    )


for _command_name, _description, _names in GROUPS:
    group_command(_command_name, _description, _names)

with open("pus20.xml", "wt") as f:
    system.dump(f)