TCP TM Data Link
================

Provides packets received via plain TCP sockets.

In case the TCP connection with the telemetry server cannot be opened or is broken, it retries to connect each 10 seconds.


Class Name
----------

:javadoc:`org.yamcs.tctm.TcpTmDataLink`


Configuration
-------------

Data links are configured in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

   dataLinks:
    - name: tctm
      class: org.yamcs.tctm.TcpTmDataLink
      stream: tm_realtime
      host: 127.0.0.1
      port: 10011
      packetInputStreamClassName: org.yamcs.tctm.CcsdsPacketInputStream
      packetPreprocessorClassName: org.yamcs.tctm.GenericPacketPreprocessor
      packetPreprocessorArgs:
          timestampOffset: 2
          seqCountOffset: 10
          errorDetection:
            type: "CRC-16-CCIIT"


Configuration Options
---------------------

host (string)
    **Required.** The host of the TM provider

port (integer)
    **Required.** The TCP port to connect to

stream (string)
    **Required.** The stream where incoming data (telemetry) is emitted

packetInputStreamClassName (string)
    Class name of a :doc:`packet-input-stream/index`. Default is :doc:`org.yamcs.tctm.CcsdsPacketInputStream <packet-input-stream/ccsds>` which reads CCSDS Packets.

packetInputStreamArgs (map)
    Optional args of arbitrary complexity to pass to the PacketInputStream. Each PacketInputStream may support different options.

packetPreprocessorClassName (string)
    Class name of a :doc:`packet-preprocessor/index` implementation.
    
    Default is :javadoc:`org.yamcs.tctm.IssPacketPreprocessor` which applies :abbr:`ISS (International Space Station)` conventions.
    
    .. note::
        Always explicitly configure this property. As of Yamcs 5.12.1, you will see deprecation warnings when not doing so. In a later version we expect to remove the legacy default behaviour.

packetPreprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the packet preprocessor. Each preprocessor may support different options.
