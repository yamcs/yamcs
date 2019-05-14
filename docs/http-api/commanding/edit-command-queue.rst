Edit Command Queue
==================

Edit a command queue::

    PATCH /api/processors/:instance/:processor/cqueues/:name


.. rubric:: Parameters

state (string)
    The state of the queue. Either ``enabled``, ``disabled`` or ``blocked``.

The same parameters can also be specified in the request body. In case both query string parameters and body parameters are specified, they are merged with priority being given to query string parameters.


.. rubric:: Example

Block a queue:

.. code-block:: json

    {
      "state" : "blocked"
    }

The response contains the updated queue information:

.. code-block:: json

    {
      "instance" : "simulator",
      "processorName" : "realtime",
      "name" : "default",
      "state" : "BLOCKED",
      "nbSentCommands" : 0,
      "nbRejectedCommands" : 0
    }


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message EditCommandQueueRequest {
      optional string state = 1;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message CommandQueueInfo {
      required string instance = 1;
      required string processorName = 2;
      required string name = 3;
      optional QueueState state = 4;
      required int32 nbSentCommands = 5;
      required int32 nbRejectedCommands = 6;
      optional int32 stateExpirationTimeS = 7;
      repeated CommandQueueEntry entry = 8;
    }
