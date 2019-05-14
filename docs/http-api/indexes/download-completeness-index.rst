Download Completeness Index
===========================

Download the completeness index records for the given instance::

    GET /api/archive/:instance/indexes/completeness


.. rubric:: Parameters

start (string)
    The time at which to start retrieving index records.

stop (string)
    The time at which to stop retrieving index records.


.. rubric:: Example

You get back a sequence of consecutive self-standing JSON objects. Note that this JSON output can be useful for testing, but you probably want to use the Protobuf media type for decreased network traffic.

.. code-block:: json

    {
      "id": {
        "name": "apid_1"
      },
      "first": "1522399433316",
      "last": "1522399433316",
      "num": 1,
      "info": "seqFirst: 0 seqLast: 0"
    }{
      "id": {
        "name": "apid_1"
      },
      "first": "1522399433515",
      "last": "1522399433515",
      "num": 1,
      "info": "seqFirst: 0 seqLast: 0"
    }{
      "id": {
        "name": "apid_1"
      },
      "first": "1522399433718",
      "last": "1522399433718",
      "num": 1,
      "info": "seqFirst: 0 seqLast: 0"
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
