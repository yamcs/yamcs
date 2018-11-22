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
    
    public CustomAlgorithm(CustomAlgorithm a) {
        super(a);
        this.language = a.language;
        this.algorithmText = a.algorithmText;
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
    
    /**
     * return a shallow copy of the algorithm
     * @return
     */
    public CustomAlgorithm copy() {
        return new CustomAlgorithm(this);
    }

}
