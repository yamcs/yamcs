package org.yamcs.security.sdls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistence for SDLS-related parameters. Currently used to save the current sequence number across crashes/restarts.
 */
public class SdlsMemento {
    public static final String MEMENTO_KEY = "yamcs.sdls";

    /**
     * Maps a link-SPI combination (i.e., a Security Association) to a sequence number
     */
    private final Map<String, Map<Short, IvSeqNum>> linkAndSpiToSeqNum = new HashMap<>();

    /**
     * Delete a persisted sequence number.
     *
     * @param link
     *            the name of the link
     * @param spi
     *            the Security Parameter Index
     */
    public void delSeqNum(String link, short spi) {
        if (linkAndSpiToSeqNum.containsKey(link)) {
            linkAndSpiToSeqNum.get(link).remove(spi);
        }
    }

    /**
     * Persist a sequence number
     *
     * @param link
     *            the name of the link
     * @param spi
     *            the Security Parameter Index
     * @param seqNum
     *            the sequence number to save
     */
    public void saveSeqNum(String link, short spi, IvSeqNum seqNum) {
        if (!linkAndSpiToSeqNum.containsKey(link)) {
            linkAndSpiToSeqNum.put(link, new HashMap<>());
        }
        linkAndSpiToSeqNum.get(link).put(spi, seqNum);
    }

    /**
     * Get the saved sequence number
     *
     * @param link
     *            the name of the link
     * @param spi
     *            the Security Parameter Index
     * @return the sequence number saved in the database, or null if not present.
     */
    public IvSeqNum getSeqNum(String link, short spi) {
        var linkSpiToSeqNum = linkAndSpiToSeqNum.get(link);
        if (linkSpiToSeqNum == null) {
            return null;
        }
        return linkSpiToSeqNum.get(spi);
    }

    /**
     * Get the SPIs used on a link.
     *
     * @param link
     *            the name of the link
     * @return a set of SPIs
     */
    public Set<Short> getSpis(String link) {
        var linkSpiToSeqNum = linkAndSpiToSeqNum.get(link);
        if (linkSpiToSeqNum == null) {
            return new HashSet<>();
        }
        return linkSpiToSeqNum.keySet();
    }
}