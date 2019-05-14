Command History Updates
=======================

The `cmdhistory` resource type within the WebSocket API allows subscribing to commanding history updates. Information will be received when new commands are sent to Yamcs, and when Yamcs Server is notified of acknowledgements over its further lifecycle.


.. rubric:: Subscribe

Within the WebSocket request envelope use these values:

* request-type `cmdhistory`
* request `subscribe`

This will make your web socket connection receive updates of the type `CMD_HISTORY`.

Here's example output in JSON (with Protobuf, there's an applicable getter in the `WebSocketSubscriptionData`) for one issued command.

.. code-block:: json

    [1,2,3]
    [1,4,0,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"TransmissionConstraints","value":{"type":"STRING","stringValue":"NA"}}]}}]
    [1,4,1,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Final_Sequence_Count","value":{"type":"STRING","stringValue":"11"}}]}}]
    [1,4,2,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_FSC_Status","value":{"type":"STRING","stringValue":"ACK: OK"}}]}}]
    [1,4,3,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_FSC_Time","value":{"type":"TIMESTAMP","timestampValue":1490956723844}}]}}]
    [1,4,4,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_FRC_Status","value":{"type":"STRING","stringValue":"ACK: OK"}}]}}]
    [1,4,5,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_FRC_Time","value":{"type":"TIMESTAMP","timestampValue":1490956724244}}]}}]
    [1,4,6,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_DASS_Status","value":{"type":"STRING","stringValue":"ACK: OK"}}]}}]
    [1,4,7,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_DASS_Time","value":{"type":"TIMESTAMP","timestampValue":1490956724644}}]}}]
    [1,4,8,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_MCS_Status","value":{"type":"STRING","stringValue":"ACK: OK"}}]}}]
    [1,4,9,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_MCS_Time","value":{"type":"TIMESTAMP","timestampValue":1490956725044}}]}}]
    [1,4,10,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_A_Status","value":{"type":"STRING","stringValue":"ACK A: OK"}}]}}]
    [1,4,11,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_A_Time","value":{"type":"TIMESTAMP","timestampValue":1490956725444}}]}}]
    [1,4,12,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_B_Status","value":{"type":"STRING","stringValue":"ACK B: OK"}}]}}]
    [1,4,13,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_B_Time","value":{"type":"TIMESTAMP","timestampValue":1490956726444}}]}}]
    [1,4,14,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_C_Status","value":{"type":"STRING","stringValue":"ACK C: OK"}}]}}]
    [1,4,15,{"dt":"CMD_HISTORY", "data":{"commandId":{"generationTime":1490956723442,"origin":"user@my-machine","sequenceNumber":1,"commandName":"/YSS/SIMULATOR/SWITCH_VOLTAGE_ON"},"attr":[{"name":"Acknowledge_C_Time","value":{"type":"TIMESTAMP","timestampValue":1490956727444}}]}}]


.. rubric:: Unsubscribe

Within the WebSocket request envelope use these values:

* request-type `cmdhistory`
* request `unsubscribe`

This will stop your WebSocket connection from getting further command history updates.
