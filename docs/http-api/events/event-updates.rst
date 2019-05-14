Event Updates
=============

The `events` resource type within the WebSocket API allows subscribing to event updates.


.. rubric:: Subscribe

Within the WebSocket request envelope use these values:

* request-type `events`
* request `subscribe`

This will make your web socket connection receive updates of the type `ProtoDataType.EVENT`.

Here's example output in JSON (with Protobuf, there's an applicable getter in the `WebSocketSubscriptionData`).

.. code-block:: json

    [1,2,3]
    [1,4,0,{"dt":"EVENT","data":{"source":"CustomAlgorithm","generationTime":1440823760490,"receptionTime":1440823760490,"seqNumber":325,"type":"bla","message":"uhuh0.4890178832134868","severity":0}}]
    [1,4,1,{"dt":"EVENT","data":{"source":"CustomAlgorithm","generationTime":1440823765491,"receptionTime":1440823765491,"seqNumber":326,"type":"bla","message":"uhuh0.29612159559494056","severity":0}}]
    [1,4,2,{"dt":"EVENT","data":{"source":"CustomAlgorithm","generationTime":1440823770490,"receptionTime":1440823770490,"seqNumber":327,"type":"bla","message":"uhuh0.7098682009567915","severity":0}}]

.. rubric:: Unsubscribe

Within the WebSocket request envelope use these values:

* request-type `events`
* request `unsubscribe`

This will stop your WebSocket connection from getting further event updates.
