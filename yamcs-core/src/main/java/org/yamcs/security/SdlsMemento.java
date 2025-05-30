package org.yamcs.security;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SdlsMemento {
    public static final String MEMENTO_KEY = "yamcs.sdls";
    private final Map<String, Map<Short, BigInteger>> linkAndSpiToSeqNum = new HashMap<>();

    public void delSeqNum(String link, short spi) {
        if (linkAndSpiToSeqNum.containsKey(link)) {
            linkAndSpiToSeqNum.get(link).remove(spi);
        }
    }
    public void saveSeqNum(String link, short spi, BigInteger seqNum) {
        if (!linkAndSpiToSeqNum.containsKey(link)) {
            linkAndSpiToSeqNum.put(link, new HashMap<>());
        }
        linkAndSpiToSeqNum.get(link).put(spi, seqNum);
    }

    public BigInteger getSeqNum(String link, short spi) {
        var linkSpiToSeqNum = linkAndSpiToSeqNum.get(link);
        if (linkSpiToSeqNum == null) {
            return null;
        }
        return linkSpiToSeqNum.get(spi);
    }

    public Set<Short> getSpis(String link) {
        var linkSpiToSeqNum = linkAndSpiToSeqNum.get(link);
        if (linkSpiToSeqNum == null) {
            return new HashSet<>();
        }
        return linkSpiToSeqNum.keySet();
    }
}