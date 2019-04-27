Command Queues Updates
======================

The `cqueues` resource type within the WebSocket API allows subscribing to commanding queue updates. Information will be received when new commands are added into the queues, commands are removed from the queues as well as when the queues state changes (between BLOCKED, ENABLED, DISABLED).


.. rubric:: Subscribe

Within the WebSocket request envelope use these values:

* request-type `cqueues`
* request `subscribe`

This will make your web socket connection receive updates of the type `Commanding.CommandQueueInfo` and `Commanding.CommandQueueEntry`. The initial CommandQueueInfo message (right after subscription) will contain the command queue information including all the commands in the queue. The subsequent messages will only be sent each time the command queue state modifies (e.g. from ENABLED to BLOCKED) or when a command is added or sent but will not include the commands in the queue. The information about the commands added/sent/removed is sent in `Commanding.CommandQueueEntry` messages.


Here's example output in JSON (with Protobuf, there's an applicable getter in the `WebSocketSubscriptionData`) for a message received just after subscription.

.. code-block:: json

    [1,2,3]
    [1,4,0,{"dt":"COMMAND_QUEUE_INFO","data":{"instance":"simulator","processorName":"realtime","name":"default","state":"BLOCKED","nbSentCommands":5,"nbRejectedCommands":1,"entry":[{"instance":"simulator","processorName":"realtime","queueName":"default","cmdId":{"generationTime":1470381583809,"origin":"127.0.0.1","sequenceNumber":14,"commandName":"/test123/"},"source":"test123()","binary":"GMnAAAA5ABBDDEBqEwCwRsiBwEcAAABpAAAAAAAPc3ZfaXNDcmlzc0Nyb3NzAAAAAAAAAAAAAAAAAAAAAAAAAA==","username":"nm","generationTime":1470381583809,"uuid":"2459b774-52e2-4011-b753-31151e689821"}]}}]

Below, an example message received when a command is added to the queue:

.. code-block:: json

    [1,4,3,{"dt":"COMMAND_QUEUE_EVENT","data":{"type":"COMMAND_ADDED","data":{"instance":"simulator","processorName":"realtime","queueName":"default","cmdId":{"generationTime":1470381585809,"origin":"127.0.0.1","sequenceNumber":14,"commandName":"test124"},"source":"test124()","binary":"GMnAAAA5AAAAAABqewCwRsiBwEcAAABpAAAAAAAPc3ZfaXNDcmlzc0Nyb3NzAAAAAAAAAAAAAAAAAAAAAAAAAA==","username":"nm","generationTime":1470381583809,"uuid":"2459b774-89e2-4011-b753-31151e689821"}}}]


.. rubric:: Unsubscribe

Within the WebSocket request envelope use these values:

* request-type `cqueues`
* request `unsubscribe`

This will stop your WebSocket connection from getting further commanding queue updates.
