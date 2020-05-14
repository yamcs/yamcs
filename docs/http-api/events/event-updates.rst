Event Updates
=============

Subscribe to event updates on a :doc:`../websocket` connection using the topic ``events``. This will make your WebSocket connection receive instance-wide events.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeEventsRequest {
      optional string instance = 1;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message Event {
      enum EventSeverity {
        INFO = 0;
        WARNING = 1;
        ERROR = 2;
        //the levels below are compatible with XTCE
        // we left the 4 out since it could be used 
        // for warning if we ever decide to get rid of the old ones
        WATCH = 3;
        DISTRESS = 5;
        CRITICAL = 6;
        SEVERE = 7;
      }
      required string source = 1;
      required int64 generationTime = 2;
      optional int64 receptionTime = 3;
      optional int32 seqNumber = 4;
      optional string type = 5;
      required string message = 6;
      optional EventSeverity severity = 7[default=INFO];
    
      optional string generationTimeUTC = 8;
      optional string receptionTimeUTC = 9;
      
      optional string createdBy = 10; // Set by API when event was posted by a user
    
      extensions 100 to 10000;
    }
