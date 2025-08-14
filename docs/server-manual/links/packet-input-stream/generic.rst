Generic Packet Input Stream
===========================

Splits a stream of variably-sized packets, where the packets have a length-field of some kind. The position of the length-field, as well as the logic for determining the size of the whole packet can be influenced by a number of configuration options.


Class Name
----------

:javadoc:`org.yamcs.tctm.GenericPacketInputStream`


Configuration
-------------

This can be used in the configuration of a stream-based packet data link in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml
   :emphasize-lines: 6-12

   dataLinks:
     - name: file-in
       class: org.yamcs.tctm.FilePollingTmDataLink
       stream: tm_dump
       incomingDir: incoming/
       packetInputStreamClassName: org.yamcs.tctm.GenericPacketInputStream
       packetInputStreamArgs:
         lengthFieldOffset: 4
         lengthFieldLength: 2
         lengthAdjustment: 7
         initialBytesToStrip: 0
         maxPacketLength: 1500
     # ...

.. note::

   This example ``GenericPacketInputStream`` configuration is equivalent to a :doc:`ccsds`.


Configuration Options
---------------------

lengthFieldOffset (integer)
   **Required.** Offset within the packet (in bytes), where the length field is located.

lengthFieldLength (integer)
   **Required.** Size in bytes of the length field. Must be a value between 1 and 4.

lengthAdjustment (integer)
   **Required.** Having read the length-field value, the ``lengthAdjustment`` option allows to adjust that value in order to obtain the length of the whole packet. This can be useful if for example the header is not included in the encoded length. If no adjustment needs to be made (i.e. the encoded length is equal to the whole packet length), a value of ``0`` is appropriate.

initialBytesToStrip (integer)
   **Required.** After reading the whole packet, use this option to strip some of the leading bytes. For example, assume you are dealing with length-prefixed packets, but you don't want that prefix to be considered part of the packet processed by Yamcs.

maxPacketLength (integer)
   Maximum allowed size of each packet in bytes. Default: ``1500``

byteOrder (string)
   One of ``BIG_ENDIAN`` or ``LITTLE_ENDIAN``. This option is used when reading the value of the length field.
   
   Default: ``BIG_ENDIAN``
