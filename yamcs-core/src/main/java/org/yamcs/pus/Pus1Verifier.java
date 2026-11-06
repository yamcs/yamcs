package org.yamcs.pus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.YConfiguration;
import org.yamcs.algorithms.AbstractAlgorithmExecutor;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmExecutionResult;
import org.yamcs.commanding.VerificationResult;
import org.yamcs.mdb.ProcessingContext;
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
 * <p>
 * An input named <b>reportType</b> must also be given (in any position after the first 5). It is the PUS type of the
 * incoming packet, with a raw type as unsigned integer. The algorithm returns no result unless the type is 1.
 * <p>
 * For backwards compatibility, the reportType input is currently not enforced: if it is missing, a warning is logged
 * and the type check is skipped. It will become mandatory in a future version.
 * 
 * <p>
 * The algorithm checks the following conditions:
 * </p>
 * <ul>
 * <li><code>sentApid == rcvdApid</code></li>
 * <li><code>sentSeqCount == rcvdSeqCount</code></li>
 * <li><code>reportType == 1</code></li>
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
    // index of the reportType input or -1 if not present
    private final int reportTypeIdx;
    // the executor is created for each command, this is to warn only once per algorithm
    private static final Set<String> warnedAlgorithms = ConcurrentHashMap.newKeySet();
    public static AlgorithmExecutionResult NO_RESULT = new AlgorithmExecutionResult(Collections.emptyList());

    public Pus1Verifier(Algorithm algorithmDef, AlgorithmExecutionContext execCtx, HashMap<String, Object> config) {
        super(algorithmDef, execCtx);
        var yc = YConfiguration.wrap(config);

        verificationStage = yc.getInt("stage");

        if (yc.containsKey("template")) {
            template = new MessageTemplate(yc.getString("template"));
        } else {
            template = null;
        }

        int idx = -1;
        var algInputList = algorithmDef.getInputList();
        for (int i = 0; i < algInputList.size(); i++) {
            if ("reportType".equals(algInputList.get(i).getInputName())) {
                idx = i;
                break;
            }
        }
        reportTypeIdx = idx;
        String algName = algorithmDef.getQualifiedName() != null ? algorithmDef.getQualifiedName()
                : algorithmDef.getName();
        if (reportTypeIdx < 0 && warnedAlgorithms.add(algName)) {
            log.warn("PUS1 verifier {} has no reportType input; it will become mandatory in a future version",
                    algName);
        }
    }

    @Override
    public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingContext pctx) {

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

        if (reportTypeIdx >= 0) {
            var reportType = inputValues.get(reportTypeIdx);
            if (reportType == null || reportType.getRawValue().getUint32Value() != 1) {
                return NO_RESULT;
            }
        }

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
                        return pctx.getTmParams().getLastInserted(p);
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
