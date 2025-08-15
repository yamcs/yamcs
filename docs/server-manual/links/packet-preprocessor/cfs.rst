cFS Packet Preprocessor
=======================

A preprocessor for verifying and identifying `NASA (National Aeronautics and Space Administration)` cFS packets.

cFS packet headers are assumed to consist of a primary CCSDS Space Packet header (6 bytes), seconds (4 bytes), and subseconds (2 bytes).


Class Name
----------

:javadoc:`org.yamcs.tctm.cfs.CfsPacketPreprocessor`


Configuration Options
---------------------

byteOrder (string)
    One of ``BIG_ENDIAN`` or ``LITTLE_ENDIAN``. This option is used when reading the seconds and subseconds from the packet header. It is not used for reading the CCSDS primary header, which is always in ``BIG_ENDIAN``.
    
    Default: ``BIG_ENDIAN``.

useLocalGenerationTime (boolean)
    If true, packets are timestamped with local mission time rather than the time extracted from the packets. Default: ``false``.

timeEncoding (map)
    Configure how time is read from the packet. See :doc:`time-encoding`. The ``type`` option is ignored.
    
    If unset, Yamcs defaults to the following configuration:

    .. code-block:: yaml
    
       timeEncoding:
         epoch: GPS

    Which is equivalent to:

    .. code-block:: yaml

       timeEncoding:
         epoch: CUSTOM
         epochUTC: "1980-01-06T00:00:00Z"
         timeIncludesLeapSeconds: true
