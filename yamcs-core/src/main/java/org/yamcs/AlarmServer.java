package org.yamcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.tctm.PpProviderAdapter;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * Maintains a list of active alarms. 
 * 
 *  
 * <p>
 * <b>Must be declared after {@link YarchChannel}</b>
 */
public class AlarmServer extends AbstractService {

    private EventProducer eventProducer;
    private Map<Parameter, ActiveAlarm> activeAlarms=new HashMap<Parameter, ActiveAlarm>();
    // Last value of each param (for detecting changes in value)
    final String yamcsInstance;
    private final Logger log = LoggerFactory.getLogger(AlarmServer.class);
    private AlarmListener listener;
    private String streamName;
    
    static public final TupleDefinition ALARM_TUPLE_DEFINITION=new TupleDefinition();
    //user time, parameter name sequence number and event 
    static {
        ALARM_TUPLE_DEFINITION.addColumn("triggerTime", DataType.TIMESTAMP);
        ALARM_TUPLE_DEFINITION.addColumn("parameter", DataType.STRING);
        ALARM_TUPLE_DEFINITION.addColumn("seqNum", DataType.INT);
        ALARM_TUPLE_DEFINITION.addColumn("event", DataType.STRING);
    }
    
    /**
     * alarm server without a listener (used for unit tests )
     * @param yamcsInstance
     */
    AlarmServer(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
        eventProducer=EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource("AlarmServer");
    }

    /**
     * Creates an alarm server that pushes all alarms to a stream
     * @param yamcsInstance
     * @param streamName
     */
    public AlarmServer(String yamcsInstance, String streamName) {
        this.yamcsInstance = yamcsInstance;
        eventProducer=EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource("AlarmServer");
        this.streamName = streamName;
    }

    
    void setListener(AlarmListener listener) {
        this.listener = listener;
    }


    @Override
    public void doStart() {
        if(streamName!=null) {
            YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
            Stream s = ydb.getStream(streamName);
            if(s==null) {
                notifyFailed(new ConfigurationException("Cannot find a stream named '"+streamName+"'"));
                return;
            }
            listener = new AlarmToStream(s);
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        notifyStopped();
    }

    public void update(ParameterValue pv, int minViolations) {
        update(pv, minViolations, false);
    }
    public void update(ParameterValue pv, int minViolations, boolean autoAck) {
        log.info("request to update " + pv.getParameter().getQualifiedName());
        Parameter param = pv.getParameter();
        
        ActiveAlarm activeAlarm=activeAlarms.get(pv.getParameter());
        
        if((activeAlarm==null) && (pv.getMonitoringResult()==MonitoringResult.IN_LIMITS)) {
            return;
        }
        
        
        if(pv.getMonitoringResult()==MonitoringResult.IN_LIMITS) {
            if(activeAlarm.violations<minViolations) {
                log.debug("Clearing glitch for {}", param.getQualifiedName());
                activeAlarms.remove(param);
                return;
            }
        
            activeAlarm.currentValue = pv;
            if((activeAlarm.acknoledged) ||(activeAlarm.autoAcknoledge)) {
                listener.notifyCleared(activeAlarm);
                activeAlarms.remove(pv.getParameter());
            } else {
                listener.notifyUpdate(activeAlarm);
            }
        } else { // out of limits
            if(activeAlarm==null) {
                activeAlarm=new ActiveAlarm(pv, autoAck);
                activeAlarms.put(pv.getParameter(), activeAlarm);
            } else {
                activeAlarm.currentValue = pv;
                activeAlarm.violations++;
            }
            if(activeAlarm.violations < minViolations) {
                return;
            }
        
            if(activeAlarm.violations == minViolations) {
                listener.notifyTriggered(activeAlarm);
            } else {
                if(moreSevere(pv.getMonitoringResult(), activeAlarm.mostSevereValue.getMonitoringResult())){
                    activeAlarm.mostSevereValue = pv;
                    listener.notifySeverityIncrease(activeAlarm);
                } else {
                    listener.notifyUpdate(activeAlarm);
                }
            }
        
            activeAlarms.put(pv.getParameter(), activeAlarm);
        }
    }

    public void acknowledge(Parameter p, int id, String username) {
        ActiveAlarm aa = activeAlarms.get(p);
        if(aa.id!=id) {
            log.warn("Got acknowledge for parameter "+p+" but the id does not match");
            return;
        }
        aa.acknoledged = true;
        aa.usernameThatAcknowledged = username;
        
        if(aa.currentValue.getMonitoringResult()==MonitoringResult.IN_LIMITS) {
            activeAlarms.remove(p);
            listener.notifyCleared(aa);
        }
    }

    private boolean moreSevere(MonitoringResult mr1, MonitoringResult mr2) {
        return mr1.getNumber()>mr2.getNumber();
    }

    public static interface AlarmListener {
        public void notifySeverityIncrease(ActiveAlarm activeAlarm);
        public void notifyTriggered(ActiveAlarm activeAlarm) ;
        public void notifyUpdate(ActiveAlarm activeAlarm);
        /**
         * 
         * @param activeAlarm
         * @param username - username that cleared the alarm or "autoCleared" if it's auto cleared
         */
        public void notifyCleared(ActiveAlarm activeAlarm);
    }



    public static class ActiveAlarm {
        static AtomicInteger counter = new AtomicInteger();
        public int id;
        
        public boolean acknoledged;
        
        public boolean autoAcknoledge;
        
        //the value that triggered the alarm
        ParameterValue triggerValue;
        
        //most severe value
        ParameterValue mostSevereValue;
        
        //current value of the parameter
        ParameterValue currentValue;
        
        //message provided at triggering time  
        String message;
        
        int violations=1;
        
        String usernameThatAcknowledged;
        
        
        ActiveAlarm(ParameterValue pv) {
            this.triggerValue = this.currentValue = this.mostSevereValue = pv;
            id = counter.getAndIncrement();
        }
        
        ActiveAlarm(ParameterValue pv, boolean autoAck) {
            this(pv);
            this.autoAcknoledge = autoAck;
        }
    }
    
    
    static class AlarmToStream implements AlarmListener {
        enum AlarmEvent {
            TRIGGERED, UPDATED, SEVERITY_INCREASED, CLEARED;
        }
        final DataType PP_DATA_TYPE=DataType.protobuf(org.yamcs.protobuf.Pvalue.ParameterValue.class.getName());
        
        Stream stream;
        public AlarmToStream(Stream s) {
            this.stream = s;
        }
        
        private ArrayList<Object> getTupleKey(ActiveAlarm activeAlarm, AlarmEvent e) {
            ArrayList<Object> al=new ArrayList<Object>(7);
        
            //triggerTime
            al.add(activeAlarm.triggerValue.getGenerationTime());
            //parameter
            al.add(activeAlarm.triggerValue.getParameter().getQualifiedName());
            //seqNum
            al.add(activeAlarm.id);
            //event
            al.add(e.name());
            
            return al;
        }
        @Override
        public void notifyTriggered(ActiveAlarm activeAlarm) {
            TupleDefinition tdef = ALARM_TUPLE_DEFINITION.copy();
            ArrayList<Object> al = getTupleKey(activeAlarm, AlarmEvent.TRIGGERED);
            
            tdef.addColumn("triggerPV", PpProviderAdapter.PP_DATA_TYPE);
            al.add(activeAlarm.triggerValue);
            
            Tuple t = new Tuple(tdef, al);
            stream.emitTuple(t);
        }
        
        @Override
        public void notifySeverityIncrease(ActiveAlarm activeAlarm) {
            TupleDefinition tdef = ALARM_TUPLE_DEFINITION.copy();
            ArrayList<Object> al = getTupleKey(activeAlarm, AlarmEvent.SEVERITY_INCREASED);
            
            tdef.addColumn("severityIncreasedPV", PpProviderAdapter.PP_DATA_TYPE);
            al.add(activeAlarm.mostSevereValue);
        
            Tuple t = new Tuple(tdef, al);
            stream.emitTuple(t);
        }
        
        @Override
        public void notifyUpdate(ActiveAlarm activeAlarm) {	  
            TupleDefinition tdef = ALARM_TUPLE_DEFINITION.copy();
            ArrayList<Object> al = getTupleKey(activeAlarm, AlarmEvent.UPDATED);
            
            tdef.addColumn("updatedPV", PpProviderAdapter.PP_DATA_TYPE);
            al.add(activeAlarm.currentValue);
        
            Tuple t = new Tuple(tdef, al);
            stream.emitTuple(t);
        }
        
        @Override
        public void notifyCleared(ActiveAlarm activeAlarm) {
            TupleDefinition tdef = ALARM_TUPLE_DEFINITION.copy();
            ArrayList<Object> al = getTupleKey(activeAlarm, AlarmEvent.CLEARED);
            
            tdef.addColumn("clearedPV", PpProviderAdapter.PP_DATA_TYPE);
            al.add(activeAlarm.currentValue);
            
            String username = activeAlarm.usernameThatAcknowledged;
            
            if(username==null) {
                if(activeAlarm.autoAcknoledge) {
                    username = "autoAcknoledged";
                } else {
                    username = "unknown";
                }
            }
            tdef.addColumn("username", DataType.STRING);
            al.add(username);
            
            Tuple t = new Tuple(tdef, al);
            stream.emitTuple(t);
        }
    }
}
