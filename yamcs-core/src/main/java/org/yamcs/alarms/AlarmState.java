package org.yamcs.alarms;

/**
 * Inspired from ANSI/ISA–18.2 Management Of Alarm Systems For The Process Industries
 * 
 * process is the entity that triggers the alarm, in our case parameters or events.
 * 
 * @author nm
 *
 */
public enum AlarmState {
    NORMAL,      //process : OK,   Alm: OK,    Ack: ack
    UNACK_ALARM, //process: alarm, Alm: alarm, Ack: unack
    ACK_ALARM,   //process: alarm, Alm: alarm, Ack: ack
    RTN_UNACK,   //process: OK,    Alm: OK,    Ack: unack
    LATCH_UNACK, //process: OK,    Alm: alarm, Ack: unack
    LATCH_ACK,   //process: OK,    Alm: alarm, Ack: ack
    SHELVED      //ignored temporarely at operator's request
}
