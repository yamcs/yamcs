UDP Parameter Data Link
=======================

Listens on a UDP port for datagrams containing Protobuf encoded messages. One datagram is equivalent to a message of
type :javadoc:`~org.yamcs.protobuf.Pvalue.ParameterData`


Class Name
==========

:javadoc:`org.yamcs.tctm.UdpParameterDataLink`


Configuration Options
=====================

stream (string)
    **Required.** The stream where data is emitted

port (integer)
    **Required.** The UDP port to listen on
