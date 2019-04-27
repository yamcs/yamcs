Download Bulk Indexes
=====================

Download multiple indexes at the same time for the given instance::

    GET /api/archive/:instance/indexes


.. rubric:: Parameters

start (string)
    The time at which to start retrieving index records.

stop (string)
    The time at which to stop retrieving index records.

filter (array of strings)
    The type of indexes to retrieve. Choose out of ``tm``, ``pp``, ``events``, ``commands`` or ``completeness``. By default all indexes will be sent.

packetname (array of strings)
    Specify exact names for the TM packets for which you want to retrieve index records. Setting this parameter, automatically implies that ``tm`` is added to the filter.


.. rubric:: Example

You get back a sequence of consecutive self-standing JSON objects. Note that this JSON output can be useful for testing, but you probably want to use the Protobuf media type for decreased network traffic.

.. code-block:: json

    {
      "instance" : "simulator",
      "records" : [ {
        "id" : {
          "name" : "/YSS/SIMULATOR/Power"
        },
        "first" : 1448272059406,
        "last" : 1448272084398,
        "num" : 5
      }, {
        "id" : {
          "name" : "/YSS/SIMULATOR/Power"
        },
        "first" : 1448823600003,
        "last" : 1448824423242,
        "num" : 133
      } ],
      "type" : "histogram",
      "tableName" : "tm"
    }{
      "instance" : "simulator",
      "records" : [ {
        "id" : {
          "name" : "/YSS/SIMULATOR/SWITCH_VOLTAGE_OFF"
        },
        "first" : 1448731973739,
        "last" : 1448731973739,
        "num" : 1
      }, {
        "id" : {
          "name" : "/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"
        },
        "first" : 1448791891823,
        "last" : 1448791891823,
        "num" : 1
      } ],
      "type" : "histogram",
      "tableName" : "cmdhist"
    }


.. rubric:: Request Schema (protobuf)

This can be used instead of the request URI when the list of packets is long and the URI would be larger than 4096 characters.

.. code-block:: proto

    message BulkGetIndexRequest {
      optional string start = 1;
      optional string stop = 2;
      repeated string filter = 3;
      repeated string packetname = 4;
    }


.. rubric:: Response Schema (protobuf)

The response is a stream of individual Protobuf messages delimited with a ``VarInt``. Every message is of type:

.. code-block:: proto

    message IndexResult {
      required string instance = 1;
      repeated ArchiveRecord records = 2;
      //type can be histogram or completeness
      optional string type = 3;
      //if type=histogram, the tableName is the table for which the histogram is sent
      optional string tableName = 4;
    }
