CSP Packet Preprocessor
=======================

A preprocessor for verifying and identifying :abbr:`CSP (CubeSat Space Protocol)` 1.x packets.

If the checksum bit is set in the CSP header, this preprocessor will verify that the packet matches the incoming CRC32C checksum, else mark it as invalid.

Note that the CSP header does not include any time or sequence fields. The default preprocessor implementation will set the generation time as equal to the local reception time, and will use an incremental sequence count rotating over 16 bits. You can change this behavior by subclassing :javadoc:`org.yamcs.tctm.csp.CspPacketPreprocessor` and overriding the ``getGenerationTime`` and ``getSequenceCount`` methods.


Class Name
----------

:javadoc:`org.yamcs.tctm.csp.CspPacketPreprocessor`


Configuration
-------------

This preprocessor can be used in the configuration of a data link in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml
   :emphasize-lines: 9-11

   dataLinks:
     - name: csp-bridge
       class: org.yamcs.tctm.UdpTcTmDataLink
       tmStream: tm_realtime
       tcStream: tc_realtime
       host: 127.0.0.1
       port: 10100
       commandPostprocessorClassName: org.yamcs.tctm.csp.CspCommandPostprocessor
       packetPreprocessorClassName: org.yamcs.tctm.csp.CspPacketPreprocessor
       packetPreprocessorArgs:
         cspId: 11


Configuration Options
---------------------

cspId (integer, or list of integers)
    Drop packets unless the destination field matches any of the configured values. When unspecified, no packets are dropped.
