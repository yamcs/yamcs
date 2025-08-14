UDP TM Data Link
================

Listens on a UDP port for datagrams containing CCSDS packets. One datagram is equivalent to one packet.


Class Name
----------

:javadoc:`org.yamcs.tctm.UdpTmDataLink`


Configuration Options
---------------------

stream (string)
    **Required.** The stream where incoming data (telemetry) is emitted

port (integer)
    **Required.** The UDP port to listen on

maxLength (integer)
    Maximum allowed length in bytes of received packets. If a larger datagram is received, the data will be truncated. 
    
    Default: ``1500``

packetPreprocessorClassName (string)
    Class name of a :doc:`packet-preprocessor/index` implementation. Default is :javadoc:`org.yamcs.tctm.IssPacketPreprocessor` which applies :abbr:`ISS (International Space Station)` conventions.

packetPreprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the packet preprocessor. Each preprocessor may support different options.
