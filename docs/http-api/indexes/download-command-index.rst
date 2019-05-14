Download Command Index
======================

Download the index of stored commands for the given instance::

    GET /api/archive/:instance/indexes/commands


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
        "name" : "/YSS/SIMULATOR/SWITCH_VOLTAGE_OFF"
      },
      "first" : 1448731973739,
      "last" : 1448731973739,
      "num" : 1
    }{
      "id" : {
        "name" : "/YSS/SIMULATOR/SWITCH_VOLTAGE_OFF"
      },
      "first" : 1448782973440,
      "last" : 1448782973440,
      "num" : 1
    }{
      "id" : {
        "name" : "/YSS/SIMULATOR/SWITCH_VOLTAGE_OFF"
      },
      "first" : 1448783668726,
      "last" : 1448783674298,
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
