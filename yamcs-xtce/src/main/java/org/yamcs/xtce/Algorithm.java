package org.yamcs.xtce;

import java.io.PrintStream;
import java.util.ArrayList;

public class Algorithm extends NameDescription {
    private static final long serialVersionUID = 5L;
    
    private String language;
    private String algorithmText;
    private TriggerSetType triggerSet;
    private ArrayList<InputParameter> inputSet = new ArrayList<InputParameter>();
    private ArrayList<OutputParameter> outputSet = new ArrayList<OutputParameter>();
    
    //commandVerification algorithms can only be run in the context of a command verifier
    public enum Scope {global, commandVerification};
    
    private Scope scope = Scope.global;
    
    // Contrary to XTCE, no support for multiple languages per algorithm
    // private String algorithmLocation;
    public Algorithm(String name) {
        super(name);
    }
    
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAlgorithmText() {
        return algorithmText;
    }

    public void setAlgorithmText(String algorithmText) {
        this.algorithmText = algorithmText;
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
    
    public ArrayList<InputParameter> getInputSet() {
        return inputSet;
    }

    public ArrayList<OutputParameter> getOutputSet() {
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
        if(scope!=Scope.global) {
            out.print(", scope: "+scope);
        }
        if(getAliasSet()!=null) out.print(", aliases: "+getAliasSet());
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
