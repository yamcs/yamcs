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

timeEncoding (map)
    Configures how the release time is encoded in the generated ``TC[11,4]`` command (see the
    ``pus11`` option below). Sub-keys: ``implicitPfield`` (boolean, default ``true``), ``pfield``
    (integer) and ``pfieldCont`` (integer, default ``-1``). If not specified, an implicit CUC
    P-field of ``0x2e`` is used.

    ``epoch`` (one of ``TAI``, ``J2000``, ``UNIX``, ``GPS``, ``CUSTOM`` or ``NONE``, default ``NONE``)
    is the epoch the release time is encoded against when no ``tcoService`` is configured. ``NONE``
    encodes the Yamcs internal time. ``CUSTOM`` requires ``epochUTC`` (string) and optionally
    ``timeIncludesLeapSeconds`` (boolean, default ``true``).

tcoService (string)
    Name of a :abbr:`TCO (Time Correlation)` service. When set, the release time written in the
    ``TC[11,4]`` command is the on-board time obtained from that service instead of the raw UTC
    value.

pus11Crc (boolean)
    If true, a checksum is appended to the generated ``TC[11,4]`` commands. Requires
    ``errorDetection``. Default: ``true`` if ``errorDetection`` is configured, ``false`` otherwise.

pus11Apid (integer)
    APID (0-2047) of the generated ``TC[11,4]`` commands, i.e. the on-board application process
    hosting the scheduling subservice. Required when scheduling commands (``pus11ScheduleAt``);
    scheduling a command without it configured fails the command.

pus11 (map)
    Enables the PUS 11 time-based scheduling support. Detailed below.

    Older releases configured the source id, acknowledgement flags, sub-schedule id and group id
    with the top-level ``pus11SourceId``, ``pus11AckFlags``, ``pus11SubscheduleId`` and
    ``pus11GroupId`` options. These are rejected; move them into the ``pus11`` block.


PUS 11 time-based scheduling
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

A command issued with the ``pus11ScheduleAt`` command option (a timestamp) is not sent as-is;
instead it is embedded into a ``TC[11,4] insert activities into the time-based schedule`` request
that releases it on board at the requested time. The ``pus11ScheduleAt`` option is only registered
when the ``pus11`` map is present.

The ``pus11`` map configures the optional sub-schedule and scheduling group identifiers that are
written into that request. Each sub-section is independent: when it is present the corresponding
field is written into the ``TC[11,4]`` command and the matching command option is registered so
operators can override the value per command; when it is absent neither the field nor the option
exist. A value that does not fit in the configured field width fails the command.

subScheduleId (map)
    ``bytes`` (integer, 1..8, default ``1``): width of the sub-schedule id field.
    ``default`` (integer, default ``0``): value used when the ``pus11SubScheduleId`` command
    option is not set.

groupId (map)
    ``bytes`` (integer, 1..8, default ``1``): width of the scheduling group id field.
    ``default`` (integer, default ``0``): value used when the ``pus11GroupId`` command option is
    not set.

sourceId (integer)
    Source id (0-65535) written in the secondary header of the generated ``TC[11,4]`` command.
    Default ``0``.

ackFlags (integer)
    Acknowledgement flags (0-15) written in the secondary header of the generated ``TC[11,4]``
    command. Default ``0xD``.

Besides ``pus11Apid``, ``pus11CcsdsSeqCount`` and ``pus11Binary``, the following command history
attributes are published for a scheduled command:

``ccsds-apid`` / ``ccsds-seqcount``
    Request ID the PUS 1 verifiers match against. For a scheduled command these carry the
    ``TC[11,4]`` wrapper's APID and sequence count, since only the wrapper's acknowledgements arrive
    within the verifiers' check window.

``pus11-inner-apid`` / ``pus11-inner-seqcount``
    The embedded command's own APID and sequence count.

Example:

.. code-block:: yaml

    commandPostprocessorClassName: org.yamcs.pus.PusCommandPostprocessor
    commandPostprocessorArgs:
        errorDetection:
            type: CRC-16-CCIIT
        pus11Apid: 1
        pus11:
            subScheduleId: { bytes: 1, default: 1 }
            groupId: { bytes: 1, default: 1 }


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
