package org.yamcs.xtce;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract algorithm - defines the inputs, outputs and triggers
 * 
 * @author nm
 *
 */
public abstract class Algorithm extends NameDescription {
    private static final long serialVersionUID = 6L;
    
    private TriggerSetType triggerSet;
    private List<InputParameter> inputList = new ArrayList<>();
    private List<OutputParameter> outputList = new ArrayList<>();
    
    //commandVerification algorithms can only be run in the context of a command verifier
    public enum Scope {GLOBAL, COMMAND_VERIFICATION, CONTAINER_PROCESSING};
    
    private Scope scope = Scope.GLOBAL;
    
    /**
     * copy constructor
     * @param a
     */
    Algorithm(Algorithm a) {
        super(a);
        this.triggerSet = a.triggerSet;
        this.inputList = a.inputList;
        this.outputList = a.outputList;
        this.scope = a.scope;
    }
    
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
        inputList.add(inputParameter);
    }
    
    public void addOutput(OutputParameter outputParameter) {
        outputList.add(outputParameter);
    }
    
    /**
     * same as {@link getInputList}, although it's a list this method calls it Set due to XTCE terminology. 
     * @return ordered list of input parameters
     */
    public List<InputParameter> getInputSet() {
        return inputList;
    }
    /**
     * Returns the list of input parameters
     * @return
     */
    public List<InputParameter> getInputList() {
        return inputList;
    }
    /**
     * same as {@link getOutputList}, although it's a list this method calls it Set due to XTCE terminology. 
     * @return
     */
    public List<OutputParameter> getOutputSet() {
        return outputList;
    }
    /**
     * 
     * @return ordered list of output parameters
     */
    public List<OutputParameter> getOutputList() {
        return outputList;
    }
    
    public void setOutputSet(List<OutputParameter> outputSet) {
        this.outputList = outputSet;
    }
    public void setOutputList(List<OutputParameter> outputList) {
        this.outputList = outputList;
    }
    
    public void setInputSet(List<InputParameter> inputSet) {
        setInputList(inputSet);
    }
    
    public void setInputList(List<InputParameter> inputList) {
        this.inputList = inputList;
    }
    
    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
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
        for(InputParameter p:inputList) {
            out.println("\t\tInputParameter "+p);
        }
        for(OutputParameter p:outputList) {
            out.println("\t\tOutputParameter "+p);
        }
        out.println("\t\tTriggers "+triggerSet);
    }
}
