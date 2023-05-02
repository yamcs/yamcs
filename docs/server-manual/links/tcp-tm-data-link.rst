TCP TM Data Link
================

Provides packets received via plain TCP sockets.

In case the TCP connection with the telemetry server cannot be opened or is broken, it retries to connect each 10 seconds.


Class Name
----------

:javadoc:`org.yamcs.tctm.TcpTmDataLink`


Configuration Options
---------------------

host (string)
    **Required.** The host of the TM provider

port (integer)
    **Required.** The TCP port to connect to

stream (string)
    **Required.** The stream where data is emitted

packetInputStreamClassName (string)
    Class name of a :javadoc:`~org.yamcs.tctm.PacketInputStream`. Default is :javadoc:`org.yamcs.tctm.CcsdsPacketInputStream` which reads CCSDS Packets.

packetInputStreamArgs (map)
    Optional args of arbitrary complexity to pass to the PacketInputStream. Each PacketInputStream may support different options.

packetPreprocessorClassName (string)
    Class name of a :javadoc:`~org.yamcs.tctm.PacketPreprocessor` implementation. Default is :javadoc:`org.yamcs.tctm.IssPacketPreprocessor` which applies :abbr:`ISS (International Space Station)` conventions.

packetPreprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the PacketPreprocessor. Each PacketPreprocessor may support different options.
