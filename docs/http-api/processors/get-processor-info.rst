Get Processor Info
==================

Get info on a specific Yamcs processor::

    GET /api/processors/:instance/:name


.. rubric:: Response
.. code-block:: json

    {
      "instance" : "simulator",
      "name" : "realtime",
      "type" : "realtime",
      "creator" : "system",
      "hasCommanding" : true,
      "state" : "RUNNING"
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: protobuf

    message ProcessorInfo {
      optional string instance = 1;
      optional string name = 2;
      optional string type = 3;
      optional string spec = 4;
      optional string creator = 5;
      optional bool hasCommanding = 6;
      optional ServiceState state = 7;
      optional yamcs.ReplayRequest replayRequest = 8;
      optional yamcs.ReplayStatus.ReplayState replayState = 9;
    }
