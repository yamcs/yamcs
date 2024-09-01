package org.yamcs.commanding;

import java.util.List;

import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterValueChange;
import org.yamcs.xtce.PathElement;

/**
 * Command verifier which succeeds when a parameter changes with a delta above a threshold.
 * <p>
 * The parameter is first sampled when the verifier starts and that value is compared with the new values when received.
 * <p>
 * This verifier never fails but it timeouts if the value does not change (enough)
 * 
 * @author nm
 *
 */
public class ValueChangeVerifier  extends Verifier implements ParameterConsumer {
    double firstValue;
    boolean firstValueReceived = false;

    ParameterValueChange pvc;
    final ParameterRequestManager prm;
    int subscriptionId = -1;


    ValueChangeVerifier(CommandVerificationHandler cvh, CommandVerifier cv) {
        super(cvh, cv);
        this.pvc = cv.getParameterValueChange();
        this.prm = cvh.getProcessor().getParameterRequestManager();
    }


    @Override
    void doStart() {
        try {
            Parameter param = pvc.getParameterRef().getParameter();
            subscriptionId = prm.addRequest(param, this);
            if (cv.getReturnParameter() != null) {
                prm.addItemsToRequest(subscriptionId, cv.getReturnParameter());
            }
            ParameterValue pv = prm.getLastValueFromCache(param);
            if (pv != null) {
                timer.submit(() -> process(pv, true));
            }

        } catch (Exception e) {
            log.warn("Failed to subscribe to parameters", e);
        }

    }

    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> params) {
        timer.submit(() -> params.forEach(pv -> process(pv, false)));
    }

    private void process(ParameterValue pv, boolean fromCache) {
        if (pv.getAcquisitionStatus() != AcquisitionStatus.ACQUIRED) {
            log.debug("Ignoring invalid value {}", pv);
            return;
        }

        if (!fromCache && pv.getParameter() == cv.getReturnParameter()) {
            returnPv = pv;
        }

        Value engValue = pv.getEngValue();
        if (engValue == null) {
            log.warn("Received parameter value without engineering value {}", pv);
            return;
        }

        if ((engValue instanceof AggregateValue) || (engValue instanceof ArrayValue)) {
            PathElement[] path = pvc.getParameterRef().getMemberPath();
            if (path == null) {
                log.warn("Received an {} for verifier but the referenced parameter {} is not of an that type",
                        engValue.getClass(), pvc.getParameterRef().getParameter().getQualifiedName());
                return;
            }
            engValue = AggregateUtil.getMemberValue(engValue, path);
        }
        log.trace("Processing {} ", engValue);
        if (!firstValueReceived) {
            firstValueReceived = ValueUtility.processAsDouble(engValue, d -> firstValue = d);
        } else if (!fromCache) {
            ValueUtility.processAsDouble(engValue, secondValue -> {
                double delta = pvc.getDelta();
                boolean ok = delta > 0 ? secondValue - firstValue >= delta : secondValue - firstValue <= delta;
                if (ok) {
                    unsubscribe();
                    finished(true, null);
                }
            });
        }
    }

    @Override
    void doCancel() {
        unsubscribe();
    }

    private synchronized void unsubscribe() {
        if (subscriptionId != -1) {
            prm.unsubscribeAll(subscriptionId);
            subscriptionId = -1;
        }
    }
}
