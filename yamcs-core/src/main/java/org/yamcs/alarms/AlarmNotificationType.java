package org.yamcs.alarms;
 

public enum AlarmNotificationType {
    TRIGGERED, //when the alarm has been initially triggered
    ACKNOWLEDGED, //when the operator acknowledges the alarm
    RTN, //when the process (parameter) has returned to normal (in limits)
    CLEARED, //when the alarm has been cleared (it is not anymore triggered)
    RESET, //for latched alarms, when the operator resets it
    SHELVED, //when the operator shelves the alarm (disable temporarily)
    UNSHELVED, //when the operator shelves the alarm (disable temporarily)
    VALUE_UPDATED, //when a new value has been received for a parameter and the value does not change the alarm state 
    SEVERITY_INCREASED; //when the severity has increased
}