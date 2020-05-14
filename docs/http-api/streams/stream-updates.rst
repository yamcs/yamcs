Stream Updates
==============

Subscribe to stream data on a :doc:`../websocket` connection using the topic ``stream``.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeStreamRequest {
      optional string instance = 1;
      optional string stream = 2;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message StreamData {
      optional string stream = 1;
      repeated ColumnData column = 2;
    }
