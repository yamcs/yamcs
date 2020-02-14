Stream Indexes
==============

Stream index records of  multiple indexes for the given instance::

    POST /api/archive/{instance}:streamIndex


.. rubric:: Parameters

start (string)
    The time at which to start retrieving index records.

stop (string)
    The time at which to stop retrieving index records.

filters (array of strings)
    The type of indexes to retrieve. Choose out of ``tm``, ``pp``, ``events``, ``commands`` or ``completeness``. By default all indexes are sent.

packetnames (array of strings)
    Specify exact names for the TM packets for which you want to retrieve index records. Setting this parameter, automatically implies that ``tm`` is added to the filter.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message StreamIndexRequest {
      optional google.protobuf.Timestamp start = 2;
      optional google.protobuf.Timestamp stop = 3;
      repeated string filters = 4;
      repeated string packetnames = 5;
    }


.. rubric:: Response Schema (protobuf)

The response is a stream of individual Protobuf messages delimited with a ``VarInt``. Every message is of type:

.. code-block:: proto

    message IndexResult {
      repeated ArchiveRecord records = 2;
      //type can be histogram or completeness
      optional string type = 3;
      //if type=histogram, the tableName is the table for which the histogram is sent
      optional string tableName = 4;
    }
