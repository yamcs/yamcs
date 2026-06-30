package org.yamcs.simulator.pus;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ST[09] Time Management simulator service.
 *
 * Periodically sends TM[9,2] CUC time packets on APID=0. The generation rate
 * is controlled by TC[9,1], which carries a single byte rateExponent such that
 * the period is 2^rateExponent seconds (valid range 0-8 per §6.9.3c).
 */
public class Pus9Service extends AbstractPusService {
    static final int DEFAULT_RATE_EXPONENT = 2; // 2^2 = 4 s

    private volatile int rateExponent = DEFAULT_RATE_EXPONENT;
    private ScheduledFuture<?> scheduledFuture;

    Pus9Service(PusSimulator pusSimulator) {
        super(pusSimulator, 9);
    }

    @Override
    public void start() {
        reschedule(rateExponent);
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        if (tc.getSubtype() != 1) {
            log.warn("Unknown ST[09] subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
            return;
        }
        handleSetRate(tc);
    }

    private void handleSetRate(PusTcPacket tc) {
        int exp = tc.getUserDataBuffer().get(0) & 0xFF;
        if (exp > 8) {
            log.warn("rateExponent {} exceeds max 8, sending NACK start", exp);
            nack_start(tc, START_ERR_INVALID_RATE_EXPONENT);
            return;
        }
        ack_start(tc);
        rateExponent = exp;
        reschedule(exp);
        ack_completion(tc);
    }

    private synchronized void reschedule(int exp) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        long periodSec = 1L << exp;
        scheduledFuture = pusSimulator.executor.scheduleAtFixedRate(
                this::sendTimePacket, 0, periodSec, TimeUnit.SECONDS);
    }

    private void sendTimePacket() {
        pusSimulator.tmLink.sendImmediate(new PusTmTimePacket(rateExponent));
    }
}
