Generic Command Postprocessor
=============================

A configurable postprocessor  for handling arbitrary commands prior to sending out.

Currently this is limited to appending a configurable checksum field only.


Class Name
----------

:javadoc:`org.yamcs.tctm.GenericCommandPostprocessor`


Configuration Options
---------------------

errorDetection (map)
    If specified, a checksum is appended at the end of each command.
    Detailed below.


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
