Get Command Queue
=================

Get data on a command queue::

    GET /api/processors/{instance}/{processor}/queues/{name}


.. rubric:: Response
.. code-block:: json

    {
      "instance" : "simulator",
      "processorName" : "realtime",
      "name" : "default",
      "state" : "BLOCKED",
      "nbSentCommands" : 0,
      "nbRejectedCommands" : 0,
      "entries" : [ {
        "instance" : "simulator",
        "processorName" : "realtime",
        "queueName" : "default",
        "cmdId" : {
          "generationTime" : 1448782973440,
          "origin" : "000349-WS.local",
          "sequenceNumber" : 5,
          "commandName" : "/YSS/SIMULATOR/SWITCH_VOLTAGE_OFF"
        },
        "source" : "SWITCH_VOLTAGE_OFF(voltage_num: 2)",
        "binary" : "GGTAAAAAAAAAAABqAAAAAgI=",
        "username" : "anonymous",
        "generationTime" : 1448782973440,
        "uuid" : "3e867111-048a-4343-b195-47ba07d07093"
      } ]
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
