Download Event Index
====================

Download the index of stored events for the given instance::

    GET /api/archive/:instance/indexes/events


.. rubric:: Parameters

start (string)
    The time at which to start retrieving index records.

stop (string)
    The time at which to stop retrieving index records.


.. rubric:: Example

You get back a sequence of consecutive self-standing JSON objects. Note that this JSON output can be useful for testing, but you probably want to use the Protobuf media type for decreased network traffic.

.. code-block:: json

    {
      "id" : {
        "name" : "CustomAlgorithm"
      },
      "first" : 1448272052179,
      "last" : 1448272052241,
      "num" : 2
    }{
      "id" : {
        "name" : "CustomAlgorithm"
      },
      "first" : 1448272057109,
      "last" : 1448272062209,
      "num" : 3
    }{
      "id" : {
        "name" : "CustomAlgorithm"
      },
      "first" : 1448272067107,
      "last" : 1448272072206,
      "num" : 3
    }


.. rubric:: Response Schema (protobuf)

The response is a stream of individual Protobuf messages delimited with a ``VarInt``. Every message is of type:

.. code-block:: proto

    message ArchiveRecord {
      required NamedObjectId id = 1;
      required int64 first = 2;
      required int64 last = 3;
      required int32 num = 4;
      optional string info = 5;
    }
