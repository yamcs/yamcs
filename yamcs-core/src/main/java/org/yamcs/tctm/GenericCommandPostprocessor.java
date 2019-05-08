package org.yamcs.tctm;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;

import static org.yamcs.tctm.GenericPacketPreprocessor.CONFIG_KEY_ERROR_DETECTION;


public class GenericCommandPostprocessor implements CommandPostprocessor {
    static Logger log = LoggerFactory.getLogger(IssCommandPostprocessor.class);
    
    final ErrorDetectionWordCalculator errorDetectionCalculator;
   
    protected CommandHistoryPublisher commandHistoryListener;
    
    public GenericCommandPostprocessor(String yamcsInstance) {
        errorDetectionCalculator = new Running16BitChecksumCalculator();
    }
    
    public GenericCommandPostprocessor(String yamcsInstance, Map<String, Object> config) {
        if(config!=null && config.containsKey(CONFIG_KEY_ERROR_DETECTION)) {
            errorDetectionCalculator = GenericPacketPreprocessor.getErrorDetectionWordCalculator(config);
        } else {
            errorDetectionCalculator = new Running16BitChecksumCalculator();
        }
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        byte [] binary = pc.getBinary();
        return binary;
    }
    
    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryListener = commandHistoryListener;
    }

}
