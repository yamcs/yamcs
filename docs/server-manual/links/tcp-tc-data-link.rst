TCP TC Data Link
================

Sends telecommands via TCP.


Class Name
----------

:javadoc:`org.yamcs.tctm.TcpTcDataLink`


Configuration Options
---------------------

stream (string)
    **Required.** The stream where command instructions are received

host (string)
    **Required.** The host of the TC provider

port (integer)
    **Required.** The TCP port to connect to

tcQueueSize (integer)
    Limit the size of the queue. Default: unlimited

tcMaxRate (integer)
    Ensure that on overage no more than ``tcMaxRate`` commands are issued during any given second. Default: unspecified

commandPostprocessorClassName (string)
    Class name of a :javadoc:`~org.yamcs.tctm.CommandPostprocessor` implementation. Default is :javadoc:`org.yamcs.tctm.IssCommandPostprocessor` which applies :abbr:`ISS (International Space Station)` conventions.

commandPostprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the CommandPostprocessor. Each CommandPostprocessor may support different options.
