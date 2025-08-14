TCP TC Data Link
================

Sends telecommands via TCP.


Class Name
----------

:javadoc:`org.yamcs.tctm.TcpTcDataLink`


Configuration
-------------

Data links are configured in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

   dataLinks:
    - name: tc-out
      class: org.yamcs.tctm.TcpTcDataLink
      stream: tc_realtime
      host: 127.0.0.1
      port: 10010
      commandPostprocessorClassName: org.yamcs.tctm.GenericCommandPostprocessor


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
    Class name of a :doc:`command-postprocessor/index` implementation. Default is :doc:`org.yamcs.tctm.GenericCommandPostprocessor <command-postprocessor/generic>`.

commandPostprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the command postprocessor. Each postprocessor may support different options.
