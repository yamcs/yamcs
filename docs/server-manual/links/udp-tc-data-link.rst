UDP TC Data Link
================

Sends telecommands via UDP socket. One datagram is equivalent to one command.


Class Name
----------

:javadoc:`org.yamcs.tctm.UdpTcDataLink`


Configuration Options
---------------------

stream (string)
    **Required.** The stream where data is emitted

host (string)
    **Required.** The host of the TC provider

port (integer)
    **Required.** The UDP port to send to

port (integer)
    **Required.** The UDP port to listen on

tcQueueSize (integer)
    Limit the size of the queue. Default: unlimited

tcMaxRate (integer)
    Ensure that on overage no more than ``tcMaxRate`` commands are issued during any given second. Default: unspecified

commandPostprocessorClassName (string)
    Class name of a :javadoc:`~org.yamcs.tctm.CommandPostprocessor` implementation. Default is :javadoc:`org.yamcs.tctm.IssCommandPostprocessor` which applies :abbr:`ISS (International Space Station)` conventions.

commandPostprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the CommandPostprocessor. Each CommandPostprocessor may support different options.
