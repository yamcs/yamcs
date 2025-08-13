Fixed-length Packet Input Stream
================================

Splits a stream of packets, by assuming that each packet has a fixed length in bytes.

For example, when receiving the following byte fragments:

.. code-block:: text

   +---+----+------+----+
   | A | BC | DEFG | HI |
   +---+----+------+----+

A fixed-length packet input stream at 3 bytes, will identify the following packets:

.. code-block:: text

   +-----+-----+-----+
   | ABC | DEF | GHI |
   +-----+-----+-----+


Class Name
----------

:javadoc:`org.yamcs.tctm.FixedPacketInputStream`


Configuration
-------------

This can be used in the configuration of a stream-based packet data link in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml
   :emphasize-lines: 5-7

   name: file-in
   class: org.yamcs.tctm.FilePollingTmDataLink
   stream: tm_dump
   incomingDir: incoming/
   packetInputStreamClassName: org.yamcs.tctm.FixedPacketInputStream
   packetInputStreamArgs:
     packetSize: 50
   # ...


Configuration Options
---------------------

packetSize (integer)
   **Required.** Size of each packet in bytes
