package org.yamcs.examples.cascading;


import org.yamcs.tctm.CommandPostprocessor;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;

/**
 * This command post-processor applies to the binary commands
 */
public class MyCommandPostprocessor implements CommandPostprocessor {
    CommandHistoryPublisher cmdHistoryPublisher;
    EventProducer eventProducer;
    public MyCommandPostprocessor(String yamcsInstance) {
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, "MyCommandPostprocessor", -1);
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        byte[] cmd = pc.getBinary();

        if (cmd.length != 2) {
            return cmd;
        }

        int voltageNum = cmd[1] & 0xFF;
        
        if (voltageNum < 1 || voltageNum > 3) {
            byte nv = (byte) ((voltageNum - 1) % 3 + 1);
            eventProducer.sendWarning("Changing voltage number from " + voltageNum + " to " + nv);
            cmd[1] = (byte) nv;
        }
        return cmd;
    }

    public void setCommandHistoryPublisher(CommandHistoryPublisher cmdHistoryPublisher) {
        this.cmdHistoryPublisher = cmdHistoryPublisher;
    }
}