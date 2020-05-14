Packet Updates
==============

Subscribe to packets on a :doc:`../websocket` connection using the topic ``packets``.

.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribePacketsRequest {
      optional string instance = 1;
      optional string stream = 2;
    }

.. note::
    This subscription is performed at stream level. It is not currently possible to subscribe to the telemetry of a processor (but most of the time the telemetry in a processor will be coming from a stream).


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message TmPacketData {
      required bytes packet = 2;
      optional int32 sequenceNumber = 4;
      optional NamedObjectId id = 5;
      optional google.protobuf.Timestamp receptionTime = 8;
      optional google.protobuf.Timestamp generationTime = 9;
    }

.. note::

    Because the messages are received from a stream, the NamedObjectId (which is the identifier of the packet) will not be filled in.
