Get Stream Info
===============

Get info on a Yamcs stream::

    GET /api/archive/:instance/streams/:name

.. note::

    This is low-level API for those cases where access to an internal stream of Yamcs is wanted. It is recommended to use other API operations for any of the default built-in streams.


.. rubric:: Response
.. code-block:: json

    {
      "name" : "tm_realtime",
      "column" : [ {
        "name" : "gentime",
        "type" : "TIMESTAMP"
      }, {
        "name" : "seqNum",
        "type" : "INT"
      }, {
        "name" : "rectime",
        "type" : "TIMESTAMP"
      }, {
        "name" : "packet",
        "type" : "BINARY"
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message StreamInfo {
      optional string name = 1;
      repeated ColumnInfo column = 2;
    }
