package org.yamcs.tctm;

/** Receives complete downlink frame byte sequences. */
public interface TmFrameTransport extends DataLinkComponent {

    interface Receiver {
        void onFrame(byte[] data, int offset, int length);

        default void onInvalidFrame(String reason) {
        }

        void onFailure(Throwable cause);
    }

    void start(int maximumFrameLength, Receiver receiver) throws Exception;

    void stop();

    default String getDetailedStatus() {
        return "";
    }
}
