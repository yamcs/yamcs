Command Queue Updates
=====================

Statistics
----------

Subscribe to general statistics on a :doc:`../websocket` connection using the topic ``queue-stats``.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeQueueStatisticsRequest {
      optional string instance = 1;
      optional string processor = 2;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message CommandQueueInfo {
      optional string instance = 1;
      optional string processorName = 2;
      optional string name = 3;
      optional QueueState state = 4;
      optional int32 nbSentCommands = 5;
      optional int32 nbRejectedCommands = 6;
      optional int32 stateExpirationTimeS = 7;
      repeated CommandQueueEntry entry = 8;
      optional int32 order = 9;
      repeated string users = 10;
      repeated string groups = 11;
      optional mdb.SignificanceInfo.SignificanceLevelType minLevel = 12;
    }


Queue Detail
------------

Subscribe to queue events for a specific processor using the topic ``queue-events``

.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeQueueEventsRequest {
      optional string instance = 1;
      optional string processor = 2;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message CommandQueueEvent {
      enum Type {
        COMMAND_ADDED = 1;
        COMMAND_REJECTED = 2;
        COMMAND_SENT = 3;
        COMMAND_UPDATED = 4;
      }
      optional Type type = 1;
      optional CommandQueueEntry data = 2;
    }
