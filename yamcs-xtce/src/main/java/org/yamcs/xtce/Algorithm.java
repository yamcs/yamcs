package org.yamcs.xtce;

import java.io.PrintStream;
import java.util.ArrayList;

// No triggers yet
public class Algorithm extends NameDescription {
    private static final long serialVersionUID = 201308201317L;
    
    private String language;
    private String algorithmText;
    private AutoActivateType autoActivate; // This is not in XTCE
    private ArrayList<InputParameter> inputSet = new ArrayList<InputParameter>();
    private ArrayList<OutputParameter> outputSet = new ArrayList<OutputParameter>();
    
    // Contrary to XTCE, no support for multiple algorithms at this level
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
    
    public void setAutoActivate(AutoActivateType autoActivate) {
        this.autoActivate=autoActivate;
    }
    
    public AutoActivateType getAutoActivate() {
        return autoActivate;
    }
    
    public void print(PrintStream out) {
        out.print("Algorithm name: "+name);
        if(getAliasSet()!=null) out.print(", aliases: "+getAliasSet());
        out.println(".");
    }
    
    public enum AutoActivateType {
        ALWAYS,
        REALTIME_ONLY,
        REPLAY_ONLY
    }
}
