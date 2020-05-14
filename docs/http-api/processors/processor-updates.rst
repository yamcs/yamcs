Processor Updates
=================

Status
------

Subscribe to processor updates on a :doc:`../websocket` connection using the topic ``processors``.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message ListProcessorsResponse {
      repeated yamcsManagement.ProcessorInfo processors = 1;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ProcessorInfo {
      optional string instance = 1; //yamcs instance
      optional string name = 2;
      optional string type = 3;
      optional string spec = 4;
      optional string creator = 5;
      optional bool hasAlarms = 6;
      optional bool hasCommanding = 7;
      optional ServiceState state = 8;
      optional ReplayRequest replayRequest = 9; //in case of replay
      optional ReplayStatus.ReplayState replayState = 10; //in case of replay
      repeated ServiceInfo services = 16;
      optional bool persistent = 17;
      optional string time = 18;
      optional bool replay = 19;
      optional bool checkCommandClearance = 20;
    }


Statistics
----------

Subscribe to processor statistics on a :doc:`../websocket` connection using the topic ``tmstats``.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeTMStatisticsRequest {
      optional string instance = 1;
      optional string processor = 2;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message Statistics {
      reserved 2,4,5;
      optional string instance = 1;
      optional string processor = 7;
      repeated TmStatistics tmstats = 3;
      optional google.protobuf.Timestamp lastUpdated = 6; //java local time of the last update
    }
