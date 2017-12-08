package org.yamcs.xtce;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract algorithm - just defines the inputs, outputs and triggers
 * @author nm
 *
 */
public abstract class Algorithm extends NameDescription {
    private static final long serialVersionUID = 6L;
    
    private TriggerSetType triggerSet;
    private ArrayList<InputParameter> inputSet = new ArrayList<>();
    private ArrayList<OutputParameter> outputSet = new ArrayList<>();
    
    //commandVerification algorithms can only be run in the context of a command verifier
    public enum Scope {GLOBAL, COMMAND_VERIFICATION, CONTAINER_PROCESSING};
    
    private Scope scope = Scope.GLOBAL;
    
    public Algorithm(String name) {
        super(name);
    }
    
    public TriggerSetType getTriggerSet() {
        return triggerSet;
    }
    
    public void setTriggerSet(TriggerSetType triggerSet) {
        this.triggerSet = triggerSet;
    }
    
    public void addInput(InputParameter inputParameter) {
        inputSet.add(inputParameter);
    }
    
    public void addOutput(OutputParameter outputParameter) {
        outputSet.add(outputParameter);
    }
    
    /**
     * same as {@link getInputList}, although it's a list this method calls it Set due to XTCE terminology. 
     * @return ordered list of input parameters
     */
    public List<InputParameter> getInputSet() {
        return inputSet;
    }
    /**
     * Returns the list of input parameters
     * @return
     */
    public List<InputParameter> getInputList() {
        return inputSet;
    }
    /**
     * same as {@link getOutputList}, although it's a list this method calls it Set due to XTCE terminology. 
     * @return
     */
    public List<OutputParameter> getOutputSet() {
        return outputSet;
    }
    /**
     * 
     * @return ordered list of output parameters
     */
    public List<OutputParameter> getOutputList() {
        return outputSet;
    }
    
    public boolean canProvide(Parameter parameter) {
        for(OutputParameter p:outputSet) {
            if(p.getParameter()==parameter) {
                return true;
            }
        }
        return false;
    }
    
    public void print(PrintStream out) {
        out.print("Algorithm name: "+name);
        if(scope!=Scope.GLOBAL) {
            out.print(", scope: "+scope);
        }
        if(getAliasSet()!=null) {
            out.print(", aliases: "+getAliasSet());
        }
        out.println();
        for(InputParameter p:inputSet) {
            out.println("\t\tInputParameter "+p);
        }
        for(OutputParameter p:outputSet) {
            out.println("\t\tOutputParameter "+p);
        }
        out.println("\t\tTriggers "+triggerSet);
    }


    public Scope getScope() {
        return scope;
    }


    public void setScope(Scope scope) {
        this.scope = scope;
    }
}
