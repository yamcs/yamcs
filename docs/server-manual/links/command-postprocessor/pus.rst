PUS Command Postprocessor
=========================

A postprocessor for handling ECSS PUS commands, according to ECSS-E-ST-70-41C.

This postprocessor will set the length and sequence count in the CCSDS primary header. If configured, it can also calculate a checksum.


Class Name
----------

:javadoc:`org.yamcs.pus.PusCommandPostprocessor`


Configuration Options
---------------------

seqCounterName (string)
    Name of a shared CCSDS sequence counter. By default each link maintains its own per-APID
    sequence counter independently of all other links. When two or more links specify the same
    ``seqCounterName`` they share a single per-APID counter, so the target receives a continuous
    sequence across those links.

    If not specified, the counter is local to this link.

errorDetection (map)
    If specified, a checksum is appended at the end of each command.
    Detailed below.

pus11Crc (boolean)
    If true, a checksum is appended to the TC(11,4) commands generated for the ``pus11ScheduleAt`` command option.
    Requires ``errorDetection``. Default: ``true`` if ``errorDetection`` is configured, ``false`` otherwise.

pus11Apid (integer)
    APID of the generated TC(11,4) commands, i.e. the on-board application process hosting the
    scheduling subservice. Required when scheduling commands (``pus11ScheduleAt``); scheduling a
    command without it configured fails the command.

pus11SourceId (integer)
    Source ID (0-65535) written in the secondary header of the generated TC(11,4) commands. Default: ``0``.

pus11AckFlags (integer)
    Acknowledgement flags (0-15) written in the secondary header of the generated TC(11,4) commands. Default: ``0xD``.

pus11SubscheduleId (integer)
    Sub-schedule ID (0-255) of the generated TC(11,4) commands. It can be overridden per command with the
    ``pus11SubscheduleId`` command option. If neither is given, ``0`` is used; the command does not fail.

pus11GroupId (integer)
    Group ID (0-255) of the generated TC(11,4) commands. It can be overridden per command with the
    ``pus11GroupId`` command option. If neither is given, ``0`` is used; the command does not fail.

    The sub-schedule ID and group ID fields are always written; they cannot be omitted. The on-board
    scheduling subservice must therefore support both sub-schedules and groups.


Error Detection sub-configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

type (string)
    **Required.** Can take one of the values:

    * ``16-SUM``: calculates a 16 bits checksum over the entire packet which has to contain an even number of bytes. This checksum is used in Columbus/:abbr:`ISS (International Space Station)` data.
    * ``CRC-16-CCIIT``: standard CRC algorithm used in PUS and also in CCSDS standards for frame encoding. 
    * ``ISO-16``: specified in PUS as alternative to CRC-16-CCIIT.
    * ``NONE``: no error detection will be used, this is the default if the ``errorDetection`` map is not present.

initialValue (integer)
    Used when the type is ``CRC-16-CCIIT`` to specify the initial value used for the algorithm. Default: ``0xFFFF``.
