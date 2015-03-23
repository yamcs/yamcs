package org.yamcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.utils.StringConvertors;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

/**
 * Mantains a list of active alarms. 
 * 
 *  
 * <p>
 * <b>Must be declared after {@link YarchChannel}</b>
 */
public class AlarmServer extends AbstractService implements ParameterConsumer {

    private EventProducer eventProducer;
    private Map<Parameter, ActiveAlarm> activeAlarms=new HashMap<Parameter, ActiveAlarm>();
    // Last value of each param (for detecting changes in value)
    private Map<Parameter, ParameterValue> lastValuePerParameter = new HashMap<Parameter, ParameterValue>();
    final String yamcsInstance;
    final String channelName;
    private final Logger log = LoggerFactory.getLogger(AlarmServer.class);
    private AlarmListener listener;


    public AlarmServer(String yamcsInstance) {
	this(yamcsInstance, "realtime");
    }

    public AlarmServer(String yamcsInstance, String channelName) {
	this.yamcsInstance = yamcsInstance;
	this.channelName = channelName;    			
	eventProducer=EventProducerFactory.getEventProducer(yamcsInstance);
	eventProducer.setSource("AlarmChecker");
    }

    public void setListener(AlarmListener listener) {
	this.listener = listener;
    }
    
    
    @Override
    public void doStart() {
	Channel channel = Channel.getInstance(yamcsInstance, channelName);
	if(channel==null) {
	    ConfigurationException e = new ConfigurationException("Cannot find a channel '"+channelName+"' in instance '"+yamcsInstance+"'");
	    notifyFailed(e);
	    return;
	}
	ParameterRequestManager prm = channel.getParameterRequestManager();

	// Auto-subscribe to parameters with alarms
	Set<Parameter> requiredParameters=new HashSet<Parameter>();
	try {
	    XtceDb xtcedb=XtceDbFactory.getInstance(yamcsInstance);
	    for (Parameter parameter:xtcedb.getParameters()) {
		ParameterType ptype=parameter.getParameterType();
		if(ptype!=null && ptype.hasAlarm()) {
		    requiredParameters.add(parameter);
		    Set<Parameter> dependentParameters = ptype.getDependentParameters();
		    if(dependentParameters!=null) {
			requiredParameters.addAll(dependentParameters);
		    }
		}
	    }
	} catch(ConfigurationException e) {
	    notifyFailed(e);
	    return;
	}

	if(!requiredParameters.isEmpty()) {
	    List<Parameter> params=new ArrayList<Parameter>(requiredParameters); // Now that we have uniques..
	    try {
		prm.addRequest(params, this);
	    } catch(InvalidIdentification e) {
		throw new RuntimeException("Could not register dependencies for alarms", e);
	    }
	}
	notifyStarted();
    }

    @Override
    public void doStop() {
	notifyStopped();
    }

    @Override
    public void updateItems(int subscriptionId, ArrayList<ParameterValue> items) {
	// Nothing. The real business of sending events, happens while checking the alarms
	// because that's where we have easy access to the XTCE definition of the active
	// alarm. The PRM is only used to signal the parameter subscriptions.
    }

    /**
     * Sends an event if an alarm condition for the active context has been
     * triggered <tt>minViolations</tt> times. This configuration does not
     * affect events for parameters that go back to normal, or that change
     * severity levels while the alarm is already active.
     */
    public void update(ParameterValue pv, int minViolations) {
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
		activeAlarm=new ActiveAlarm(pv);
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
		if(moreSevere(pv.monitoringResult, activeAlarm.msValue.monitoringResult)){
		    activeAlarm.msValue = pv;
		    listener.notifySeverityIncrease(activeAlarm);
		} else {
		    listener.notifyUpdate(activeAlarm);
		}
	    }

	    activeAlarms.put(pv.getParameter(), activeAlarm);
	}

    }


    private boolean moreSevere(MonitoringResult mr1, MonitoringResult mr2) {
	// TODO Auto-generated method stub
	return false;
    }




    private boolean hasChanged(ParameterValue pvOld, ParameterValue pvNew) {
	// Crude string value comparison.
	return !StringConvertors.toString(pvOld.getEngValue(), false)
		.equals(StringConvertors.toString(pvNew.getEngValue(), false));
    }

    public static interface AlarmListener {
	public void notifySeverityIncrease(ActiveAlarm activeAlarm);


	public void notifyTriggered(ActiveAlarm activeAlarm) ;

	public void notifyUpdate(ActiveAlarm activeAlarm);

	public void notifyCleared(ActiveAlarm activeAlarm);
    }

    
    
    public static class ActiveAlarm {
	public boolean acknoledged;

	public boolean autoAcknoledge;

	//the value that triggered the alarm
	ParameterValue triggerValue;

	//most severe value
	ParameterValue msValue;

	//current value of the parameter
	ParameterValue currentValue;

	//message provided at triggering time  
	String message;

	int violations=1;

	ActiveAlarm(ParameterValue pv) {
	    this.triggerValue = this.currentValue = this.msValue = pv;
	}
    }
}
