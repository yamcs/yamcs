UDP TC/TM Data Link
===================

Sends telecommands via UDP socket, receiving telemetry on the same socket pair. The data link acts as the UDP client. No telemetry is received unless a command was sent first (any command).


Class Name
----------

:javadoc:`org.yamcs.tctm.UdpTcTmDataLink`


Configuration
-------------

Data links are configured in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

   dataLinks:
    - name: csp-bridge
      class: org.yamcs.tctm.UdpTcTmDataLink
      tmStream: tm_realtime
      tcStream: tc_realtime
      host: 127.0.0.1
      port: 10010
      commandPostprocessorClassName: org.yamcs.tctm.csp.CspCommandPostprocessor
      packetPreprocessorClassName: org.yamcs.tctm.csp.CspPacketPreprocessor


Configuration Options
---------------------

tcStream (string)
    **Required.** The stream where outgoing data (telecommands) is emitted

tmStream (string)
    **Required.** The stream where incoming data (telemetry) is emitted

host (string)
    **Required.** The host of the remote system

port (integer)
    **Required.** The UDP port to send to

commandPostprocessorClassName (string)
    Class name of a :doc:`command-postprocessor/index` implementation. Default is :doc:`org.yamcs.tctm.GenericCommandPostprocessor <command-postprocessor/generic>`.

commandPostprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the command postprocessor. Each postprocessor may support different options.

packetPreprocessorClassName (string)
    Class name of a :doc:`packet-preprocessor/index` implementation.
    
    Default is :javadoc:`org.yamcs.tctm.IssPacketPreprocessor` which applies :abbr:`ISS (International Space Station)` conventions.
    
    .. note::
        Always explicitly configure this property. As of Yamcs 5.12.1, you will see deprecation warnings when not doing so. In a later version we expect to remove the legacy default behaviour.

packetPreprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the packet preprocessor. Each preprocessor may support different options.
