List Streams
============

List all streams for the given instance::

    GET /api/archive/:instance/streams

.. note::

  This is low-level API for those cases where access to the internal streams of Yamcs is wanted. It is recommended to use other API operations for any of the default built-in streams.


.. rubric:: Example
.. code-block:: json

    {
      "stream" : [ {
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
      } ]
    }

Note that this will only list the fixed columns of the stream. Tuples may always have extra columns.


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListStreamsResponse {
      repeated archive.StreamInfo stream = 1;
    }
