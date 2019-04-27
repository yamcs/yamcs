List Command Queues
===================

List all command queues for the given processor::

    GET /api/processors/:instance/:processor/cqueues


.. rubric:: Response
.. code-block:: json

    {
      "queue" : [ {
        "instance" : "simulator",
        "processorName" : "realtime",
        "name" : "default",
        "state" : "ENABLED",
        "nbSentCommands" : 0,
        "nbRejectedCommands" : 0
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListCommandQueuesResponse {
      repeated commanding.CommandQueueInfo queue = 1;
    }
