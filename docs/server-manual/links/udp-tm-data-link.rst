UDP TM Data Link
================

Listens on a UDP port for datagrams containing CCSDS packets. One datagram is equivalent to one packet.


Class Name
----------

:javadoc:`org.yamcs.tctm.UdpTmDataLink`


Configuration Options
---------------------

stream (string)
    **Required.** The stream where data is emitted

port (integer)
    **Required.** The UDP port to listen on

maxLength (integer)
    The maximum length of the packets received. If a larger datagram is received, the data will be truncated. Default: 1500 bytes

packetPreprocessorClassName (string)
    Class name of a :javadoc:`~org.yamcs.tctm.PacketPreprocessor` implementation. Default is :javadoc:`org.yamcs.tctm.IssPacketPreprocessor` which applies :abbr:`ISS (International Space Station)` conventions.

packetPreprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the PacketPreprocessor. Each PacketPreprocessor may support different options.
