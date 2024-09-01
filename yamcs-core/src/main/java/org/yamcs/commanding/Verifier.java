package org.yamcs.commanding;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.mdb.Mdb;

abstract class Verifier {
    /**
     * Imaginary parameter for publishing an optional return value to cmdhist
     */
    public static final String YAMCS_PARAMETER_RETURN_VALUE = Mdb.YAMCS_CMD_SPACESYSTEM_NAME + "/returnValue";

    final protected Log log;
    final protected CommandVerifier cv;
    final protected CommandVerificationHandler cvh;
    final ActiveCommand activeCommand;
    final ScheduledThreadPoolExecutor timer;
    protected ParameterValue returnPv;

    enum State {
        NEW, RUNNING, OK, NOK, TIMEOUT, DISABLED, CANCELLED
    };

    volatile State state = State.NEW;

    Verifier nextVerifier;

    Verifier(CommandVerificationHandler cvh, CommandVerifier cv) {
        this.cv = cv;
        this.cvh = cvh;
        this.timer = cvh.timer;
        this.activeCommand = cvh.getActiveCommand();
        this.log = new Log(this.getClass(), cvh.getProcessor().getInstance());
    }

    void start() {
        state = State.RUNNING;
        doStart();
    }

    void timeout() {
        if (state != State.RUNNING) {
            return;
        }
        state = State.TIMEOUT;
        doCancel();
        cvh.onVerifierFinished(this);
    }

    void cancel() {
        if (state != State.RUNNING && state != State.NEW) {
            return;
        }
        state = State.CANCELLED;
        doCancel();
        cvh.onVerifierFinished(this);
    }

    void finished(boolean success, String message) {
        if (state != State.RUNNING) {
            return;
        }

        // we set the returnPv from cache unless it has been set by one of the subclasses to a more relevant value
        // for example if a specific packet triggered a verifier and that packet contained a sample for the
        // returnParameter, we want that sample to be used as returnPv.
        // We assume that the sub-classes do that, if not the code below will use whatever latest value is available.
        if (cv.getReturnParameter() != null && returnPv == null) {
            returnPv = cvh.getProcessor().getLastValueCache()
                    .getValue(cv.getReturnParameter());
        }
        state = success ? State.OK : State.NOK;
        cvh.onVerifierFinished(this, message, returnPv);
    }

    void finished(boolean success) {
        finished(success, null);
    }

    void finishOK() {
        finished(true, null);
    }

    void finishNOK() {
        finished(false, null);
    }

    abstract void doStart();

    /**
     * Called to cancel the verification in case it didn't finish in the expected time.
     */
    abstract void doCancel();

    public State getState() {
        return state;
    }

    public String getStage() {
        return cv.getStage();
    }
}
