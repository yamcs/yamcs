package org.yamcs.xtce;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * A trigger is used to initiate the processing of some algorithm. A trigger may
 * be based on an update of a Parameter or on a time basis. Triggers may also
 * have a rate that limits their firing to a 1/rate basis.
 */
public class TriggerSetType implements Serializable {
    private static final long serialVersionUID = -9191842839880223058L;
    
    // private String name;
    // private int triggerRate;
    private ArrayList<OnParameterUpdateTrigger> onParameterUpdateTriggers=new ArrayList<OnParameterUpdateTrigger>();
    //private ArrayList<OnContainerUpdateTrigger> onContainerUpdateTriggers=new ArrayList<OnContainerUpdateTrigger>();
    private ArrayList<OnPeriodicRateTrigger> onPeriodicRateTriggers=new ArrayList<OnPeriodicRateTrigger>();
    
    public void addOnParameterUpdateTrigger(OnParameterUpdateTrigger trigger) {
        onParameterUpdateTriggers.add(trigger);
    }
    
    public ArrayList<OnParameterUpdateTrigger> getOnParameterUpdateTriggers() {
        return onParameterUpdateTriggers;
    }
    
    public void addOnPeriodicRateTrigger(OnPeriodicRateTrigger trigger) {
        onPeriodicRateTriggers.add(trigger);
    }
    
    public ArrayList<OnPeriodicRateTrigger> getOnPeriodicRateTriggers() {
        return onPeriodicRateTriggers;
    }
    
    @Override
    public String toString() {
        StringBuilder buf=new StringBuilder();
        buf.append("onParameterUpdate:").append(onParameterUpdateTriggers).append("\n");
        buf.append("onPeriodicRate:").append(onPeriodicRateTriggers);
        return buf.toString();
    }
}
