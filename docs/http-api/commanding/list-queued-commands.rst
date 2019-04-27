List Queued Commands
====================

List all queued command entries for the given command queue::

    GET /api/processors/:instance/:processor/cqueues/:name/entries


.. rubric:: Response
.. code-block:: json

    {
      "entry" : [ {
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

    message ListCommandQueueEntries {
      repeated commanding.CommandQueueEntry entry = 1;
    }
