Stream Updates
==============

The `stream` resource type within the WebSocket API groups low-level subscribe operations on Yamcs Streams.

The documented operations work on one of the built-in streams (like `tm`, `tm_realtime`, `tm_dump`, `pp_realtime`, `cmdhist_realtime`, etc). Or, if your Yamcs deployment defines any other streams, they would work as well.


.. rubric:: Subscribe to a Stream

Within the WebSocket request envelope use these values:

* request-type `stream`
* request `subscribe`
* data
    * With Protobuf: an object of type `Yamcs.StreamSubscribeRequest` where at least the `stream` name is filled in.
    * With JSON: an object literal where at least the `stream` key is set.

Here's a full request example in JSON-notation

.. code-block:: json

    [1,1,3,{"stream": "subscribe", "data": {"stream": "tm_realtime"}}]

As a result of the above call you will get updates whenever anybody publishes data to the specified stream. With Protobuf, the data can be fetched with the `getStreamData()`-method on in the `WebSocketSubscriptionData` object. With JSON, you might see something like this example output:

.. code-block:: json

    [1,2,3]
    [1,4,0,{"dt":"STREAM_DATA","data":{"stream":"tm_realtime","columnValue":[{"columnName":"gentime","value":{"type":6,"timestampValue":1438608491320}},{"columnName":"seqNum","value":{"type":3,"sint32Value":134283264}},{"columnName":"rectime","value":{"type":6,"timestampValue":1438608508323}},{"columnName":"packet","value":{"type":4,"binaryValue":"CAEAAAAPQuou2FJFAAAABOcAAAAAAA=="}}]}}]
    [1,4,1,{"dt":"STREAM_DATA","data":{"stream":"tm_realtime","columnValue":[{"columnName":"gentime","value":{"type":6,"timestampValue":1438608491320}},{"columnName":"seqNum","value":{"type":3,"sint32Value":134283264}},{"columnName":"rectime","value":{"type":6,"timestampValue":1438608508323}},{"columnName":"packet","value":{"type":4,"binaryValue":"CAEAAAAPQuou2FJFAAAABOcAAAAAAA=="}}]}}]

In the case we were receiving some simulated data from the `tm_realtime` stream, this is a built-in stream with columns `gentime`, `rectime`, `seqNum` and `packet`. This last column is of binary format (it's the raw TM packet), which is why it is Base64-encoded in the JSON output.

Other streams would have different columns.
