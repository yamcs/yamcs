UDP TC Data Link
================

Sends telecommands via UDP socket. One datagram is equivalent to one command.


Class Name
----------

:javadoc:`org.yamcs.tctm.UdpTcDataLink`


Configuration Options
---------------------

stream (string)
    **Required.** The stream where outgoing data (telecommands) is emitted

host (string)
    **Required.** The host of the remote system

port (integer)
    **Required.** The UDP port to send to

tcQueueSize (integer)
    Limit the size of the queue. Default: unlimited

tcMaxRate (integer)
    Ensure that on overage no more than ``tcMaxRate`` commands are issued during any given second. Default: unspecified

commandPostprocessorClassName (string)
    Class name of a :doc:`command-postprocessor/index` implementation. Default is :doc:`org.yamcs.tctm.GenericCommandPostprocessor <command-postprocessor/generic>`.

commandPostprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the command postprocessor. Each postprocessor may support different options.
