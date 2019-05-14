Link Updates
============

The `links` resource type within the WebSocket API allows subscribing to link updates.

.. rubric:: Subscribe

Within the WebSocket request envelope use these values:

* request-type `links`
* request `subscribe`

This will make your web socket connection receive updates of the type `ProtoDataType.LINK_EVENT`.

Here's example output in JSON (with Protobuf, there's an applicable getter in the `WebSocketSubscriptionData`).

.. code-block:: json

    [1,2,3]
    [1,4,0,{"dt":"LINK_EVENT","data":{"type":"REGISTERED","linkInfo":{"instance":"simulator","name":"tm_realtime","type":"TcpTmDataLink","spec":"local","stream":"tm_realtime","disabled":false,"status":"UNAVAIL","dataCount":4344,"detailedStatus":"Not connected to simulator:10015"}}}]
    [1,4,1,{"dt":"LINK_EVENT","data":{"type":"REGISTERED","linkInfo":{"instance":"simulator","name":"tm_dump","type":"TcpTmDataLink","spec":"localDump","stream":"tm_dump","disabled":false,"status":"UNAVAIL","dataCount":0,"detailedStatus":"Not connected to simulator:10115"}}}]
    [1,4,2,{"dt":"LINK_EVENT","data":{"type":"REGISTERED","linkInfo":{"instance":"simulator","name":"tc1","type":"TcpTcDataLink","spec":"local","stream":"tc_realtime","disabled":false,"status":"UNAVAIL","dataCount":0,"detailedStatus":"Not connected to simulator:10025"}}}]

The `type` of every LINK_EVENT can be either `REGISTERED`, `UNREGISTERED` or `UPDATED`. Directly after subscribing you will first get a `REGISTERED` link at that time.

* `REGISTERED`: a new link was registered. You also receive this event directly after you subscribe, for every link that is registered at that time.
* `UNREGISTERED`: a link was unregistered.
* `UPDATED`: a link was updated in one of its attributes, for example the dataCount has increased, or the status has changed.


.. rubric:: Unsubscribe

Within the WebSocket request envelope use these values:

* request-type `links`
* request `unsubscribe`

This will stop your WebSocket connection from getting further link updates.
