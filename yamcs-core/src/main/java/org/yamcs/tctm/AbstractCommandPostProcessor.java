package org.yamcs.tctm;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.time.TimeService;

public abstract class AbstractCommandPostProcessor implements CommandPostprocessor {
    protected CommandHistoryPublisher commandHistoryPublisher;
    protected TimeService timeService;

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    public void init(String yamcsInstance, YConfiguration config) {
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryPublisher) {
        this.commandHistoryPublisher = commandHistoryPublisher;
    }

    /** Send to command history the failed command */
    protected void failCommand(CommandId commandId, String reason) {
        log.debug("Failing command {}: {}", commandId, reason);
        long currentTime = timeService.getMissionTime();
        commandHistoryPublisher.publishAck(commandId, AcknowledgeSent_KEY,
                currentTime, AckStatus.NOK, reason);
        commandHistoryPublisher.commandFailed(commandId, currentTime, reason);
    }


}
