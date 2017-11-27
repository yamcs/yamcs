package org.yamcs.artemis;

import java.util.List;
import org.yamcs.ConfigurationException;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;

/**
 * takes cmd history data from yamcs streams and publishes it to artemis address (reverse of {@link ArtemisCmdHistoryDataLink})
 *
 */
public class ArtemisCmdHistoryService extends AbstractArtemisTranslatorService {
    public ArtemisCmdHistoryService(String instance, List<String> streamNames) throws ConfigurationException {
        super(instance, streamNames, new CmdHistoryTupleTranslator());
    }
    public ArtemisCmdHistoryService(String instance) throws ConfigurationException {
        this(instance, StreamConfig.getInstance(instance).getStreamNames(StandardStreamType.cmdHist));
    }
}
