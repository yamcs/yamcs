CSP Command Postprocessor
=========================

A postprocessor for handling :abbr:`CSP (CubeSat Space Protocol)` 1.x commands prior to sending out.

Specifically, it will calculate the checksum if the CRC bit is set in the generated command binary.


Class Name
----------

:javadoc:`org.yamcs.tctm.csp.CspCommandPostprocessor`


Configuration
-------------

This preprocessor can be used in the configuration of a data link in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml
   :emphasize-lines: 8

   dataLinks:
     - name: csp-bridge
       class: org.yamcs.tctm.UdpTcTmDataLink
       tmStream: tm_realtime
       tcStream: tc_realtime
       host: 127.0.0.1
       port: 10100
       commandPostprocessorClassName: org.yamcs.tctm.csp.CspCommandPostprocessor
       packetPreprocessorClassName: org.yamcs.tctm.csp.CspPacketPreprocessor


Configuration Options
---------------------

maximumTcPacketLength (integer)
    Length in bytes. If the command (including optional CRC) is larger than this value, the command is discarded (the "Sent" acknowledgment will fail).

    Set to ``-1`` to ignore this length check.

    Default: ``-1``
