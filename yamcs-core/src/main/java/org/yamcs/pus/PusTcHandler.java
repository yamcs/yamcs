package org.yamcs.pus;

import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;

/**
 * Abstract base for a single PUS service handler registered with {@link PusCommandReleaser}.
 * <p>
 * Subclasses implement {@link #handleTc(PreparedCommand)} and may override
 * {@link #doInit}, {@link #doStart}, and {@link #doStop} for lifecycle needs.
 * The helper methods ({@code emitTm}, {@code publishAckSent}, etc.) delegate to the
 * owning {@code PusCommandReleaser}.
 */
public abstract class PusTcHandler {

    /** Byte offset of application data in a PUS TC packet. */
    protected static final int APP_DATA_OFFSET = 11;

    protected Log log;
    PusCommandReleaser releaser;

    final void init(PusCommandReleaser releaser, YConfiguration config) {
        this.releaser = releaser;
        this.log = new Log(getClass(), releaser.getYamcsInstance());
        doInit(config);
    }

    protected void doInit(YConfiguration config) {
    }

    protected void doStart() {
    }

    protected void doStop() {
    }

    public abstract void handleTc(PreparedCommand pc);

    /**
     * Builds a PUS TM packet and emits it on the TM stream.
     *
     * @param serviceType PUS service type
     * @param subtype     PUS message subtype
     * @param appData     application data bytes (placed after the PUS secondary header and time field)
     */
    protected void emitTm(int serviceType, int subtype, byte[] appData) {
        releaser.emitTm(serviceType, subtype, appData);
    }

    /** Publishes {@code Acknowledge_Sent OK} to command history. */
    protected void publishAckSent(PreparedCommand pc) {
        releaser.publishAckSent(pc);
    }

    /**
     * Publishes {@code CommandComplete OK} or {@code NOK} to command history.
     *
     * @param success {@code true} → OK, {@code false} → NOK
     * @param msg     optional message (may be {@code null})
     */
    protected void publishCompletion(PreparedCommand pc, boolean success, String msg) {
        releaser.publishCompletion(pc, success, msg);
    }

    /** Returns the current YAMCS mission time in milliseconds. */
    protected long getCurrentTime() {
        return releaser.getCurrentTime();
    }
}
