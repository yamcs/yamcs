Alarm Updates
=============

Status
------

Subscribe to general status updates on a :doc:`../websocket` connection using the topic ``global-alarm-status``.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeGlobalStatusRequest {
      optional string instance = 1;
      optional string processor = 2;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message GlobalAlarmStatus {  
      optional int32 unacknowledgedCount = 1;
      optional bool unacknowledgedActive = 2;
      optional int32 acknowledgedCount = 3;
      optional bool acknowledgedActive = 4;
      optional int32 shelvedCount = 5;
      optional bool shelvedActive = 6;
    }


Alarm Detail
------------

Subscribe to detailed alarms for a specific processor using the topic ``alarms``

Directly after you subscribe, you will receive the active set of alarms -- if any.


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message SubscribeAlarmsRequest {
      optional string instance = 1;
      optional string processor = 2;
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    // Summary of an alarm applicable for Parameter or Event alarms.
    message AlarmData {
      optional AlarmType type = 1;
      optional google.protobuf.Timestamp triggerTime = 2 ;
    
      // For parameter alarms, this is the id of the parameters
      // For event alarms
      //   - the id.namespace is /yamcs/event/<EVENT_SOURCE>, unless 
      //     EVENT_SOURCE starts with a "/" in which case the namespace
      //     is just the <EVENT_SOURCE>
      //   - the id.name is the <EVENT_TYPE>
      optional NamedObjectId id = 3;
    
      // Distinguisher between multiple alarms for the same id
      optional uint32 seqNum = 4;
    
      optional AlarmSeverity severity = 5;
    
      // Number of times the object was in alarm state
      optional uint32 violations = 6;
    
      // Number of samples received for the object
      optional uint32 count = 7;
      
      optional AcknowledgeInfo acknowledgeInfo = 8;
      optional AlarmNotificationType notificationType = 9;
    
      optional ParameterAlarmData parameterDetail = 10;
      optional EventAlarmData eventDetail = 11;
    
      // Whether the alarm will stay triggered even when the process is OK
      optional bool latching = 12;
     
    
      // if the process that generated the alarm is ok (i.e. parameter is within limits)
      optional bool processOK = 13;
      // triggered is same with processOK except when the alarm is latching
      optional bool triggered = 14;
      // if the operator has acknowledged the alarm
      optional bool acknowledged = 15;
    
      // Details in case the alarm was shelved
      optional ShelveInfo shelveInfo = 16;
    
      optional ClearInfo clearInfo = 17;
    }
