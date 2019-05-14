Download Packet Index
=====================

Download the index of stored packets for the given instance::

    GET /api/archive/:instance/indexes/packets


.. rubric:: Parameters

start (string)
    The time at which to start retrieving index records.

stop (string)
    The time at which to stop retrieving index records.

name (array of strings)
    | Exact qualified names of the packets to include in the index. By default all packets will be included.
    | **Partial wildcard matching is not currently supported.**


.. rubric:: Example

You get back a sequence of consecutive self-standing JSON objects. Note that this JSON output can be useful for testing, but you probably want to use the Protobuf media type for decreased network traffic.

.. code-block:: json

    {
      "id" : {
        "name" : "/YSS/SIMULATOR/FlightData"
      },
      "first" : 1448272053375,
      "last" : 1448272089628,
      "num" : 181
    }{
      "id" : {
        "name" : "/YSS/SIMULATOR/Power"
      },
      "first" : 1448272059406,
      "last" : 1448272084398,
      "num" : 5
    }{
      "id" : {
        "name" : "/YSS/SIMULATOR/DHS"
      },
      "first" : 1448272059406,
      "last" : 1448272084398,
      "num" : 5
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
