Generic Packet Preprocessor
===========================

A configurable preprocessor for verifying and identifying arbitrary packets.

You can configure the location and characteristics of the *timestamp* and the *sequence* field.


Class Name
----------

:javadoc:`org.yamcs.tctm.GenericPacketPreprocessor`


Configuration
-------------

.. code-block:: yaml

   dataLinks:
     - name: tm-in
       # ...
       packetPreprocessorClassName: org.yamcs.tctm.GenericPacketPreprocessor
       packetPreprocessorArgs:
         timestampOffset: 2
         seqCountOffset: 10
         errorDetection:
           type: CRC-16-CCIIT
         timeEncoding:
           type: FIXED
           size: 8
           epoch: UNIX


Configuration Options
---------------------

timestampOffset (integer)
    **Required.** Offset in the packet where to read the timestamp from.
    
    The characteristics on how to interpret the timestamp is configured with the ``timeEncoding`` property.

    Set to ``-1`` if you do not want to read the timestamp from the packet. The timestamp will then use the local wallclock time  instead.

timeEncoding (map)
    Configure how time is read from the packet. See :doc:`time-encoding`.

    If unset, Yamcs defaults to the following configuration, which assumes an 8-byte timestamp in Unix milliseconds.

    .. code-block:: yaml

       timeEncoding:
         type: FIXED
         epoch: UNIX
         size: 8
         multiplier: 1

seqCountOffset (integer)
    **Required.** Offset in the packet where to read the sequence count from.

    The length of the sequence count is currently hardcoded to 4 bytes.
    
    Set to ``-1`` if you do not want to read the sequence count from the packet. The sequence count will then always be set to 0.

errorDetection (map)
    If specified, a checksum at the end of each packet is checked to verify integrity.
    Detailed below.

byteOrder (string)
    One of ``BIG_ENDIAN`` or ``LITTLE_ENDIAN``. This option may be used when reading the timestamp, sequence count and checksum.

    Default: ``BIG_ENDIAN``.


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
