CCSDS Packet Input Stream
=========================

Splits a stream of consecutive CCSDS Space Packets. For each next packet, the implementation reads the first 6 bytes (= primary header). The last two bytes of this header (plus one) determine the length of the remainder of that packet.


Class Name
----------

:javadoc:`org.yamcs.tctm.CcsdsPacketInputStream`


Configuration
-------------

This can be used in the configuration of a stream-based packet data link in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml
   :emphasize-lines: 6-8

   dataLinks:
     - name: file-in
       class: org.yamcs.tctm.FilePollingTmDataLink
       stream: tm_dump
       incomingDir: incoming/
       packetInputStreamClassName: org.yamcs.tctm.CcsdsPacketInputStream
       packetInputStreamArgs:
         maxPacketLength: 1500
       # ...


Configuration Options
---------------------

maxPacketLength (integer)
   Maximum allowed size of each packet in bytes. Default: ``1500``
