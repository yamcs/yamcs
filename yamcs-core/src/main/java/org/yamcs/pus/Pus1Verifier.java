package org.yamcs.pus;

import java.util.Collections;
import java.util.HashMap;

import org.yamcs.YConfiguration;
import org.yamcs.algorithms.AbstractAlgorithmExecutor;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmExecutionResult;
import org.yamcs.commanding.VerificationResult;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.pus.MessageTemplate.ParameterValueResolver;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Parameter;

/**
 * PUS1 verifier.
 * <p>
 * The algorithm verifies one stage which is given as <code>verificationStage</code> in the constructor. For exmaple
 * verificationStage=3 works for the stage Start - i.e. verifies reports PUS(1,3) and PUS(1,4).
 * 
 * <p>
 * The algorithm requires at least 5 inputs, which must be defined in this exact order in the algorithm input list:
 * </p>
 * <ul>
 * <li><b>sentApid</b> - the APID of the sent command.</li>
 * <li><b>sentSeqCount</b> - the Sequence Count of the command sent, usually obtained from the command history.</li>
 * <li><b>rcvdApid</b> - the APID present in the PUS1 report.</li>
 * <li><b>rcvdSeqCount</b> - the Sequence Count in the PUS1 report.</li>
 * <li><b>reportSubType</b> - the PUS1 sub-type of the incoming PUS1 report.</li>
 * </ul>
 * The sentApid, rcvdApid, rcvdSeqCount and reportSubType are expected to have a raw type as unsigned integer. The
 * sentSeqCount is filled in by the post-processor and is expected to be signed 32 bit integer (the reason for that is
 * becuase it is converted from a command history attribute)
 * <p>
 * In addition to these 5, other inputs may be used to get values in case of failure.
 * 
 * <p>
 * The algorithm checks the following conditions:
 * </p>
 * <ul>
 * <li><code>sentApid == rcvdApid</code></li>
 * <li><code>sentSeqCount == rcvdSeqCount</code></li>
 * <li>The <code>reportSubType=verificationStage or reportSubType=verificationStage+1</code></li>
 * </ul>
 * <p>
 * If these conditions are met:
 * </p>
 * <ul>
 * <li>If <code>reportSubType == verificationStage</code>, the verifier returns success.</li>
 * <li>If <code>reportSubType == verificationStage + 1</code>, the verifier returns failure, using the provided template
 * to construct the failure message. The template can use the inputs of the algorithm for message formatting.</li>
 * </ul>
 */
public class Pus1Verifier extends AbstractAlgorithmExecutor {

    private final int verificationStage;
    private final MessageTemplate template;
    public static AlgorithmExecutionResult NO_RESULT = new AlgorithmExecutionResult(Collections.emptyList());

    /**
     * Constructs a new Pus1Verifier.
     *
     * @param algorithmDef
     *            The algorithm definition.
     * @param execCtx
     *            The execution context.
     * @param subType
     *            The subtype of the algorithm.
     * @param template
     *            The template used to construct the failure message.
     */
    public Pus1Verifier(Algorithm algorithmDef, AlgorithmExecutionContext execCtx, HashMap<String, Object> config) {
        super(algorithmDef, execCtx);
        var yc = YConfiguration.wrap(config);

        verificationStage = yc.getInt("stage");

        if (yc.containsKey("template")) {
            template = new MessageTemplate(yc.getString("template"));
        } else {
            template = null;
        }
    }

    @Override
    public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData pdata) {

        for (int i = 0; i < 5; i++) {
            if (inputValues.get(i) == null) {
                return NO_RESULT;
            }
        }
        int sentApid = inputValues.get(0).getRawValue().getUint32Value();

        // the sequence count set by the post-processor has no raw value
        int sentSeq = inputValues.get(1).getEngValue().getSint32Value();
        int rcvdApid = inputValues.get(2).getRawValue().getUint32Value();
        int rcvdSeq = inputValues.get(3).getRawValue().getUint32Value();
        int reportSubType = inputValues.get(4).getRawValue().getUint32Value();

        if (sentApid != rcvdApid || sentSeq != rcvdSeq) {
            return NO_RESULT;
        }

        if (reportSubType == verificationStage) {
            return new AlgorithmExecutionResult(inputValues, VerificationResult.SUCCESS, Collections.emptyList());
        } else if (reportSubType == verificationStage + 1) {
            String msg = null;
            if (template != null) {

                msg = template.format(new ParameterValueResolver() {
                    @Override
                    public RawEngValue resolve(String name) {
                        var algInputList = algorithmDef.getInputList();
                        for (int i = 0; i < algInputList.size(); i++) {
                            var alginput = algInputList.get(i);
                            if (name.equals(alginput.getInputName())) {
                                return inputValues.get(i);
                            }
                        }
                        return null;
                    }

                    @Override
                    public RawEngValue resolve(Parameter p) {
                        return pdata.getTmParams().getLastInserted(p);
                    }
                });
            }
            VerificationResult result = new VerificationResult(false, msg);
            return new AlgorithmExecutionResult(inputValues, result, Collections.emptyList());
        } else {
            return NO_RESULT;
        }
    }
}
