Stream TM Packet Provider
=========================

Receives packets from ``tm`` streams and sends them to the processor for extraction of parameters.

This respects the root container defined as part of the ``streamConfig`` in :file:`etc/yamcs.yaml`.

Class Name
----------

:javadoc:`org.yamcs.StreamTmPacketProvider`


Configuration
-------------

This service is defined in :file:`etc/processor.yaml`. Example:

.. code-block:: yaml

    realtime:
      services:
        - class: org.yamcs.StreamTmPacketProvider
          args:
            streams: ["tm_realtime", "tm_dump"]


Configuration Options
---------------------

streams (list of strings)
    **Required.** The streams to read.
