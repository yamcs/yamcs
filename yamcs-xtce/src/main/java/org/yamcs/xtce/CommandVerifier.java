package org.yamcs.xtce;

import java.io.Serializable;

/**
 *XTCE:
 * A command verifier is used to check that the command has been successfully executed. 
 * Command Verifiers may be either a Custom Algorithm or a Boolean Check or the presence of a Container for a relative change in the value of a Parameter.  
 * The CheckWindow is a time period where the verification must test true to pass.
 *
 * @author nm
 *
 */
public class CommandVerifier implements Serializable {
    private static final long serialVersionUID = 2L;
    public enum Type {container, algorithm};

    private final Type type;

    /**
     * what can happen when the verification finishes
     * XTCE does not specify very well, just that each verifier returns true or false. 
     * 
     * We acknowledge the fact that the verifier can also timeout and define three TerminationAction for the three outcomes: true, false or timeout. 
     */
    public enum TerminationAction {
        SUCCESS, //the command is declared successful
        FAIL //the command is declared failed
    }      
    private TerminationAction onSuccess = null, onFail = null, onTimeout = null;


    /** 
     * differs from XTCE
     * 
     * Command verification stage. We use this to implement the different stages hardcoded in the XTCE: TransferredToRange, SentFromRange, etc
     * In XTCE some of those verifications have extra parameters. This can be implemented in the future by subclassing this class.
     *  
     */
    final private String stage;


    /**
     * XTCE: A time based check window
     */
    final private CheckWindow checkWindow;


    /**
     * When verification is a new instance of the referenced Container; this verifier return true when the referenced container has been received and processed.
     */
    SequenceContainer containerRef;
    Algorithm algorithm;

    //NOT implemented from XTCE
    /*       
     * comparisonList;
     * ParameterValueChange
     * BooleanExpression
     */


    public CommandVerifier(Type type, String stage, CheckWindow checkWindow) {
        this.type = type;
        this.stage = stage;
        this.checkWindow = checkWindow;
    }

    public String getStage() {
        return stage;
    }

    public Type getType() {
        return type;
    }
    
    public void setContainerRef(SequenceContainer containerRef) {
        this.containerRef = containerRef;
    }

    public SequenceContainer getContainerRef() {
        return containerRef;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(Algorithm algo) {
        this.algorithm = algo;        
    }

    public CheckWindow getCheckWindow() {
        return checkWindow;
    }

    public TerminationAction getOnTimeout() {
        return onTimeout;
    }

    public void setOnTimeout(TerminationAction onTimeout) {
        this.onTimeout = onTimeout;
    }

    public TerminationAction getOnFail() {
        return onFail;
    }

    public void setOnFail(TerminationAction onFail) {
        this.onFail = onFail;
    }

    public TerminationAction getOnSuccess() {
        return onSuccess;
    }

    public void setOnSuccess(TerminationAction onSuccess) {
        this.onSuccess = onSuccess;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{stage: ").append(stage);
        if(containerRef!=null) {
            sb.append(", containerRef: ").append(containerRef.getName());
        }
        if(algorithm!=null) {
            sb.append(", algorithm: ").append(algorithm.getName());
        }
        sb.append(", checkWindow: ").append(checkWindow.toString()).append("}");
        return sb.toString();
    }
}
