package org.yamcs.xtce;

/**
 * Algorithm implemented in a specific language.
 * 
 * This is XTCE InputOutputTriggerAlgorithmType
 * 
 * @author nm
 *
 */
public class CustomAlgorithm extends Algorithm {
    private static final long serialVersionUID = 1L;

    private String language;
   
    private String algorithmText;
    
    public CustomAlgorithm(String name) {
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

    @Override
    public String toString() {
        return "CustomAlgorithm [language=" + language + ", algorithmText='" + algorithmText + "', inputSet: "+getInputList()+", outputSet: "+getOutputList()+"]";
    }

}
