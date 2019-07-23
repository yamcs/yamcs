package org.yamcs.artemis;

import java.util.List;

import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.YConfiguration;

/**
 * Takes cmd history data from yamcs streams and publishes it to artemis address (reverse of
 * {@link ArtemisCmdHistoryDataLink})
 *
 */
public class ArtemisCmdHistoryService extends AbstractArtemisTranslatorService {

    @Deprecated
    public ArtemisCmdHistoryService(String instance, List<String> streamNames) {
        super(instance, streamNames, new CmdHistoryTupleTranslator());
        log.warn("DEPRECATION: Define stream names under 'args -> streamNames' "
                + "in the configuration of ArtemisCmdHistoryService");
    }

    public ArtemisCmdHistoryService(String instance) {
        this(instance, StreamConfig.getInstance(instance).getStreamNames(StandardStreamType.cmdHist));
    }

    public ArtemisCmdHistoryService(String instance, YConfiguration args) {
        super(instance, args.getList("streamNames"), new CmdHistoryTupleTranslator());
    }
}
