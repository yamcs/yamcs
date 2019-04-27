Download PP Index
=================

Download the index of stored processed parameter groups for the given instance::

    GET /api/archive/:instance/indexes/pp


.. rubric:: Parameters

start (string)
    The time at which to start retrieving index records.

stop (string)
    The time at which to stop retrieving index records.


.. rubric:: Example
.. code-block:: json

    {
      "id" : {
        "name" : "simulation"
      },
      "first" : 1448616349067,
      "last" : 1448616467049,
      "num" : 2
    }{
      "id" : {
        "name" : "simulation"
      },
      "first" : 1448617368018,
      "last" : 1448617368018,
      "num" : 1
    }{
      "id" : {
        "name" : "simulation"
      },
      "first" : 1448617667330,
      "last" : 1448617667330,
      "num" : 1
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
