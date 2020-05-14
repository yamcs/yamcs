Command Updates
===============

Get updates on issued commands on a :doc:`../websocket` connection using the topic ``commands``.

Information will be received when new commands are sent to Yamcs, and when Yamcs Server is notified of acknowledgements over its further lifecycle.

.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeCommandsRequest {
      optional string instance = 1;
      optional string processor = 2;
      optional bool ignorePastCommands = 3;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message CommandHistoryEntry {
      required CommandId commandId = 1;
      repeated CommandHistoryAttribute attr = 3;
      optional string generationTimeUTC = 4;
      repeated CommandAssignment assignment = 5;
    }
