package org.yamcs.container;


import org.yamcs.ContainerExtractionResult;

/**
 * Interface for consuming extracted containers.
 */
public interface ContainerConsumer {
    /**
     * Processes the extracted container with additional context.
     *
     * @param link
     *            the name of the link on which the container was received. The link name is preserved in the archive
     *            and available during the replays as well.
     * @param cer
     *            the container extraction result
     */
    void processContainer(String link, ContainerExtractionResult cer);
}
