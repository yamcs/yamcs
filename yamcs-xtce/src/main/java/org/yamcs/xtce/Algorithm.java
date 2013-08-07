package org.yamcs.xtce;

import java.io.PrintStream;
import java.util.ArrayList;

// No triggers yet
public class Algorithm extends NameDescription {
    private static final long serialVersionUID = -8568698748072662696L;
    
    private String language;
    private String algorithmText;
    private ArrayList<Parameter> inputSet = new ArrayList<Parameter>();
    private ArrayList<Parameter> outputSet = new ArrayList<Parameter>();
    
    // Contrary to XTCE, no support for multiple algo's at this level
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
    
    public void addInput(Parameter inputParameter) {
        inputSet.add(inputParameter);
    }
    
    public void addOutput(Parameter outputParameter) {
        outputSet.add(outputParameter);
    }
    
    public ArrayList<Parameter> getInputSet() {
        return inputSet;
    }

    public ArrayList<Parameter> getOutputSet() {
        return outputSet;
    }
    
    public void print(PrintStream out) {
        out.print("Algorithm name: "+name);
        if(getAliasSet()!=null) out.print(", aliases: "+getAliasSet());
        out.println( "." );
    }
}
