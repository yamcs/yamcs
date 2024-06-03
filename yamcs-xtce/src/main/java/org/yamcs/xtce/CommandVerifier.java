package org.yamcs.xtce;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * XTCE: A command verifier is used to check that the command has been successfully executed.
 * <p>
 * Command Verifiers may be either a Custom Algorithm or a Boolean Check or the presence of a Container for a relative
 * change in the value of a Parameter.
 * <p>
 * The CheckWindow is a time period where the verification must test true to pass.
 *
 * @author nm
 *
 */
public class CommandVerifier implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum Type {
        /** verifier succeeds if a container is received */
        CONTAINER,
        /** an algorithm runs to decide if the verifier succeeds or fails */
        ALGORITHM,
        /** succeeds when some conditions are met */
        MATCH_CRITERIA,
        /** succeeds when a parameter changes with a delta above a threshold */
        PARAMETER_VALUE_CHANGE
    };

    private final Type type;

    /**
     * what can happen when the verification finishes XTCE does not specify very well, just that each verifier returns
     * true or false.
     * 
     * We acknowledge the fact that the verifier can also timeout and define three TerminationAction for the three
     * outcomes: true, false or timeout.
     */
    public enum TerminationAction {
        SUCCESS, // the command is declared successful
        FAIL // the command is declared failed
    }

    private TerminationAction onSuccess = null, onFail = null, onTimeout = null;

    /**
     * 
     * Command verification stage. This corresponds to the verifier name from XTCE.
     * 
     */
    final private String stage;

    /**
     * XTCE: A time based check window
     */
    private CheckWindow checkWindow;

    SequenceContainer containerRef;
    Algorithm algorithm;
    MatchCriteria matchCriteria;
    ParameterValueChange paraValueChange;

    // valid for matchCriteria - if true the first time the condition can be checked (i.e. all input parameters
    // available) and the condition does not match, the verifier will fail.
    // if false, the verifier will keep checking until timeout (it will never fail)
    boolean verifierFailOnFirstFailedMatch = false;

    // if not null, the value will be used as the "result" of verification
    private Parameter returnParameter;

    public CommandVerifier(Type type, String stage) {
        this.type = type;
        this.stage = stage;
    }

    public CommandVerifier(Type type, String stage, CheckWindow checkWindow) {
        this(type, stage);
        this.checkWindow = checkWindow;
    }

    // copy constructor
    public CommandVerifier(CommandVerifier cv) {
        this.algorithm = cv.algorithm;
        this.checkWindow = cv.checkWindow;
        this.type = cv.type;
        this.stage = cv.stage;
        this.containerRef = cv.containerRef;
        this.onSuccess = cv.onSuccess;
        this.onFail = cv.onFail;
        this.onTimeout = cv.onTimeout;
        this.matchCriteria = cv.matchCriteria;
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

    public void setMatchCriteria(MatchCriteria matchCriteria) {
        if (type != Type.MATCH_CRITERIA) {
            throw new IllegalStateException("This verifier is of type " + type);
        }
        this.matchCriteria = matchCriteria;
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

    public void setCheckWindow(CheckWindow checkWindow) {
        this.checkWindow = checkWindow;
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

    public MatchCriteria getMatchCriteria() {
        return matchCriteria;
    }

    public ParameterValueChange getParameterValueChange() {
        return paraValueChange;
    }

    public void setParameterValueChange(ParameterValueChange paraValueChange) {
        this.paraValueChange = paraValueChange;
    }

    public boolean failOnFirstFailedMatch() {
        return verifierFailOnFirstFailedMatch;
    }

    public Parameter getReturnParameter() {
        return returnParameter;
    }

    public void setReturnParameter(Parameter returnParameter) {
        this.returnParameter = returnParameter;
    }

    public List<Parameter> getDependentParameters() {
        List<Parameter> plist = new ArrayList<>();
        if (matchCriteria != null) {
            plist.addAll(matchCriteria.getDependentParameters());
        }
        if (paraValueChange != null) {
            plist.add(paraValueChange.getParameterRef().getParameter());
        }

        if (returnParameter != null) {
            plist.add(returnParameter);
        }
        // parameters on which the algorithms depend are handled by AlgorithmManager

        return plist;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{stage: ").append(stage);
        if (containerRef != null) {
            sb.append(", containerRef: ").append(containerRef.getName());
        }
        if (algorithm != null) {
            sb.append(", algorithm: ").append(algorithm.getName());
        }
        if (matchCriteria != null) {
            sb.append(", matchCriteria: ").append(matchCriteria);
        }
        sb.append(", checkWindow: ").append(checkWindow.toString()).append("}");
        return sb.toString();
    }
}
