PUS Packet Preprocessor
=======================

A preprocessor for verifying and identifying ECSS PUS packets, according to ECSS-E-ST-70-41C.


Class Name
----------

:javadoc:`org.yamcs.pus.PusPacketPreprocessor`


Configuration
-------------

.. code-block:: yaml

   dataLinks:
     - name: tm-in
       # ...
       packetPreprocessorClassName: org.yamcs.tctm.pus.PusPacketPreprocessor
       packetPreprocessorArgs:
         errorDetection:
            type: CRC-16-CCIIT
         useLocalGenerationTime: false
         timeEncoding:
            type: CUC
            epoch: CUSTOM
            epochUTC: "2010-09-01T00:00:00Z"
            timeIncludesLeapSeconds: true
         tcoService: tco0


Configuration Options
---------------------

errorDetection (map)
    If specified, a checksum at the end of each packet is checked to verify integrity.
    Detailed below.

useLocalGenerationTime (boolean)
    If true, packets are timestamped with local mission time rather than the time extracted from the packets. Default: ``false``.

timeEncoding (map)
    Configure how time is read from the packet. See :doc:`time-encoding`.

    If unset, Yamcs defaults to the following configuration:

    .. code-block:: yaml
    
       timeEncoding:
         type: CUC
         epoch: GPS

    Which is equivalent to:

    .. code-block:: yaml

       timeEncoding:
         type: CUC
         epoch: CUSTOM
         epochUTC: "1980-01-06T00:00:00Z"
         timeIncludesLeapSeconds: true

pktTimeOffset (integer)
    Location in bytes where to find the time field.
    
    Default: ``13``.

timePktTimeOffset (integer)
    Location in bytes where to find the time field in PUS Time Packets. These packets do not have a secondary PUS header.
    
    Default: ``7``.

tcoService (string)
    Name of a :doc:`../../services/instance/time-correlation`.

performTimeCorrelation (boolean)
    If true, send received packets to the configured ``tcoService`` for updating time synchronization coefficients.

    Default: ``false``

goodFrameStream (string)
    When ``performTimeCorrelation`` is enabled, subscribe to this Yamcs stream producing good frames. This should match the name of the stream that the frame-based data link is publishing to.

    Time packets from this stream are processed to find Earth Reception Time.

    Default: ``good_frame_stream``


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
