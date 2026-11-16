package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.algorithms.AlgorithmExecutionResult;
import org.yamcs.commanding.VerificationResult;
import org.yamcs.mdb.ProcessingContext;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm.Scope;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;

public class Pus1VerifierTest {
    static final int APID = 100;
    static final int SEQ = 42;

    Parameter sentApid = new Parameter("sentApid");
    Parameter sentSeq = new Parameter("sentSeq");
    Parameter rcvdApid = new Parameter("rcvdApid");
    Parameter rcvdSeq = new Parameter("rcvdSeq");
    Parameter subType = new Parameter("subType");
    Parameter type = new Parameter("type");

    @Test
    public void testCompletionSuccess() {
        var verifier = createVerifier(7, true);
        assertSame(Pus1Verifier.NO_RESULT, run(verifier, pus1Report(1)));
        assertVerification(true, run(verifier, pus1Report(7)));
    }

    @Test
    public void testCompletionFailure() {
        var verifier = createVerifier(7, true);
        assertSame(Pus1Verifier.NO_RESULT, run(verifier, pus1Report(1)));
        assertVerification(false, run(verifier, pus1Report(8)));
    }

    @Test
    public void testNonPus1PacketIgnored() {
        var verifier = createVerifier(7, true);
        assertSame(Pus1Verifier.NO_RESULT, run(verifier, pus1Report(1)));

        // TM(5,8) does not contain the PUS1 apid and seq count; the verifier has the old values from the TM(1,1)
        assertSame(Pus1Verifier.NO_RESULT, run(verifier, otherReport(5, 8)));
        assertSame(Pus1Verifier.NO_RESULT, run(verifier, otherReport(3, 7)));

        assertVerification(true, run(verifier, pus1Report(7)));
    }

    @Test
    public void testWithoutReportType() {
        // without the reportType input the verifier cannot distinguish a TM(5,8) from a TM(1,8)
        var verifier = createVerifier(7, false);
        assertSame(Pus1Verifier.NO_RESULT, run(verifier, pus1Report(1)));
        assertVerification(false, run(verifier, otherReport(5, 8)));
    }

    private void assertVerification(boolean success, AlgorithmExecutionResult result) {
        var vr = (VerificationResult) result.getReturnValue();
        if (success) {
            assertTrue(vr.success);
        } else {
            assertFalse(vr.success);
        }
    }

    private Pus1Verifier createVerifier(int stage, boolean withReportType) {
        var alg = new CustomAlgorithm("pus1-verifier");
        alg.setScope(Scope.COMMAND_VERIFICATION);
        List<InputParameter> inputs = new ArrayList<>();
        inputs.add(new InputParameter(new ParameterInstanceRef(sentApid), "sentApid"));
        inputs.add(new InputParameter(new ParameterInstanceRef(sentSeq), "sentSeq"));
        inputs.add(new InputParameter(new ParameterInstanceRef(rcvdApid), "rcvdApid"));
        inputs.add(new InputParameter(new ParameterInstanceRef(rcvdSeq), "rcvdSeq"));
        inputs.add(new InputParameter(new ParameterInstanceRef(subType), "reportSubType"));
        if (withReportType) {
            inputs.add(new InputParameter(new ParameterInstanceRef(type), "reportType"));
        }
        alg.setInputList(inputs);

        HashMap<String, Object> config = new HashMap<>();
        config.put("stage", stage);
        return new Pus1Verifier(alg, null, config);
    }

    private ParameterValue[] pus1Report(int stype) {
        return new ParameterValue[] { uintPv(type, 1), uintPv(subType, stype), uintPv(rcvdApid, APID),
                uintPv(rcvdSeq, SEQ) };
    }

    private ParameterValue[] otherReport(int ptype, int stype) {
        return new ParameterValue[] { uintPv(type, ptype), uintPv(subType, stype) };
    }

    private AlgorithmExecutionResult run(Pus1Verifier verifier, ParameterValue... tm) {
        var sentApidPv = uintPv(sentApid, APID);
        // the sequence count set by the post-processor has only the engineering value
        var sentSeqPv = new ParameterValue(sentSeq);
        sentSeqPv.setEngValue(ValueUtility.getSint32Value(SEQ));

        var pctx = ProcessingContext.createForTestTm(sentApidPv, sentSeqPv);
        for (var pv : tm) {
            pctx.addTmParam(pv);
        }
        assertTrue(verifier.update(pctx));
        return verifier.execute(0, 0, pctx);
    }

    private ParameterValue uintPv(Parameter p, int x) {
        var pv = new ParameterValue(p);
        pv.setRawUnsignedInteger(x);
        pv.setEngValue(ValueUtility.getUint32Value(x));
        return pv;
    }
}
