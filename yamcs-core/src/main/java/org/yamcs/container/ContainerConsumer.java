package org.yamcs.container;


import org.yamcs.ContainerExtractionResult;
import org.yamcs.mdb.ContainerProcessingResult;

/**
 * Interface for consuming extracted containers.
 */
public interface ContainerConsumer {

    /**
     * Processes an extracted container together with the full processing result for the packet: the link, all the
     * containers extracted from the packet and the parameters extracted from the same packet.
     *
     * @param cpr
     *            the container processing result for the packet the container was extracted from
     * @param cer
     *            the container extraction result for the specific container this consumer subscribed to
     */
    default void processContainer(ContainerProcessingResult cpr, ContainerExtractionResult cer) {
        processContainer(cpr.getLink(), cer);
    }

    /**
     * Processes the extracted container.
     *
     * @param link
     *            the name of the link on which the container was received. The link name is preserved in the archive
     *            and available during the replays as well.
     * @param cer
     *            the container extraction result
     * @deprecated implement {@link #processContainer(ContainerProcessingResult, ContainerExtractionResult)} instead.
     */
    @Deprecated
    default void processContainer(String link, ContainerExtractionResult cer) {
        throw new UnsupportedOperationException(getClass().getName()
                + " must implement processContainer(ContainerProcessingResult, ContainerExtractionResult)");
    }
}
