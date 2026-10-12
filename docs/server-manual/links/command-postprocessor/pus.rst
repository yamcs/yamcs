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

tcoService (string)
    Name of a :abbr:`TCO (Time Correlation)` service. When set, the release time written in the
    ``TC[11,4]`` command is the on-board time obtained from that service instead of the raw UTC
    value.

pus11Crc (boolean)
    If ``true`` (the default) the generated ``TC[11,4]`` command carries a trailing CRC.

pus11Apid (integer)
    APID to use for the generated ``TC[11,4]`` command. If ``-1`` (the default) the APID of the
    wrapped command is reused.

pus11 (map)
    Enables the PUS 11 time-based scheduling support. Detailed below.

embeddedTcCrc (boolean)
    If ``true``, a checksum (computed with the ``errorDetection`` algorithm) is appended to each TC
    packet embedded into a command, see :ref:`pus-embedded-tc` below. Default: ``true`` if
    ``errorDetection`` is configured, ``false`` otherwise. Setting it to ``true`` without
    ``errorDetection`` is a configuration error.


PUS 11 time-based scheduling
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

A command issued with the ``pus11ScheduleAt`` command option (a timestamp) is not sent as-is;
instead it is embedded into a ``TC[11,4] insert activities into the time-based schedule`` request
that releases it on board at the requested time.

The ``pus11`` map configures the optional sub-schedule and scheduling group identifiers that are
written into that request. Each sub-section is independent: when it is present the corresponding
field is written into the ``TC[11,4]`` command and the matching command option is registered so
operators can override the value per command; when it is absent neither the field nor the option
exist.

subScheduleId (map)
    ``bytes`` (integer, 1..8, default ``1``): width of the sub-schedule id field.
    ``default`` (integer, default ``0``): value used when the ``pus11SubScheduleId`` command
    option is not set.

groupId (map)
    ``bytes`` (integer, 1..8, default ``1``): width of the scheduling group id field.
    ``default`` (integer, default ``0``): value used when the ``pus11GroupId`` command option is
    not set.

sourceId (integer)
    Source id written in the secondary header of the generated ``TC[11,4]`` command. Default ``0``.

Example:

.. code-block:: yaml

    commandPostprocessorClassName: org.yamcs.pus.PusCommandPostprocessor
    commandPostprocessorArgs:
        errorDetection:
            type: CRC-16-CCIIT
        pus11:
            subScheduleId: { bytes: 1, default: 1 }
            groupId: { bytes: 1, default: 1 }


.. _pus-embedded-tc:

Embedded TC packets
^^^^^^^^^^^^^^^^^^^

Some commands carry complete TC packets as arguments, for example a manually built
``TC[11,4] insert activities into the time-based schedule`` or ``TC[22,4]``, where each activity
is a TC packet. To have these packets processed like top-level commands, annotate the binary
argument type with the ``Yamcs:EmbeddedTc`` ancillary data:

.. code-block:: xml

    <BinaryArgumentType name="EmbeddedTcType">
        <AncillaryDataSet>
            <AncillaryData name="Yamcs:EmbeddedTc"/>
        </AncillaryDataSet>
        <BinaryDataEncoding>
            <SizeInBits><FixedValue>-1</FixedValue></SizeInBits>
        </BinaryDataEncoding>
    </BinaryArgumentType>

The postprocessor finds the annotated values using the argument locations recorded when the
command is encoded. The type can be used for a top-level argument but also as member of an
aggregate inside an array, such as a list of activities. For each embedded packet, in the order in
which they appear in the command, the postprocessor:

* fills in the CCSDS packet length;
* fills in the CCSDS sequence count, using the counter of the embedded packet's APID. The value is
  published in the command history as ``ccsds-seqcount:<argument path>``, for example
  ``ccsds-seqcount:activities[1].tc``;
* if ``embeddedTcCrc`` is set, appends the checksum. The command grows by 2 bytes per embedded
  packet; the entries following an embedded packet are shifted accordingly.

The embedded packets can thus be entered with zero sequence count and length, and without
checksum. The outer command gets its length, sequence count and checksum afterwards, as usual.

The SCOS-2000 MIB loader (yamcs-scos2k) annotates the command parameters with
(PTC, PFC) = (12, 1) this way.

Limitations:

* the embedded packet has to be byte aligned.
* when the checksum is appended, the binary encoding must allow the size to change: either a size
  of ``-1`` (all the bytes of the value) or a leading size tag (which is updated). With a fixed size
  or a dynamic size (given by another argument) the command fails.
* entries located at an absolute position (``referenceLocation="containerStart"``) after an
  embedded packet are not shifted.


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
