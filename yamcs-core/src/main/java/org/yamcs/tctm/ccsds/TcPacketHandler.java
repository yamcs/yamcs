package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

import org.yamcs.ConfigurationException;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.tctm.ccsds.TcManagedParameters.TcVcManagedParameters;
import org.yamcs.utils.TimeEncoding;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.*;

/**
 * Assembles command packets into TC frames as per CCSDS 232.0-B-3.
 * <p>
 * All frames have the bypass flag set (i.e. they are BD frames).
 * 
 */
public class TcPacketHandler extends AbstractTcDataLink implements VcUplinkHandler {
    protected BlockingQueue<PreparedCommand> commandQueue;
    final TcVcManagedParameters vmp;
    private TcFrameFactory frameFactory;
    boolean blockSenderOnQueueFull;
    private Semaphore dataAvailableSemaphore;

    public TcPacketHandler(String yamcsInstance, String linkName, TcVcManagedParameters vmp)
            throws ConfigurationException {
        super(yamcsInstance, linkName, vmp.config);
        this.vmp = vmp;
        this.frameFactory = vmp.getFrameFactory();

        int queueSize = vmp.config.getInt("tcQueueSize", 10);
        blockSenderOnQueueFull = vmp.config.getBoolean("blockSenderOnQueueFull", false);
        commandQueue = new ArrayBlockingQueue<>(queueSize);
    }

    @Override
    public void sendTc(PreparedCommand preparedCommand) {
        if (disabled) {
            log.debug("TC disabled, ignoring command {}", preparedCommand.getCommandId());
            if (failCommandOnDisabled) {
                failedCommand(preparedCommand.getCommandId(), "Link "+name+" disabled");
            }
            return;
        }
        if (blockSenderOnQueueFull) {
            try {
                commandQueue.put(preparedCommand);
                dataAvailableSemaphore.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                commandHistoryPublisher.publishWithTime(preparedCommand.getCommandId(), ACK_SENT_CNAME_PREFIX,
                        getCurrentTime(), "NOK");
                commandHistoryPublisher.commandFailed(preparedCommand.getCommandId(), "Interrupted");
            }
        } else {
            if (commandQueue.offer(preparedCommand)) {
                dataAvailableSemaphore.release();
            } else {
                commandHistoryPublisher.publishWithTime(preparedCommand.getCommandId(), ACK_SENT_CNAME_PREFIX,
                        getCurrentTime(), "NOK");
            }
        }
    }

  

    @Override
    public TcTransferFrame getFrame() {
        if (commandQueue.isEmpty()) {
            return null;
        }
        int framingLength = frameFactory.getFramingLength(vmp.vcId);
        int dataLength = 0;
        List<PreparedCommand> l = new ArrayList<>();
        PreparedCommand pc;
        while ((pc = commandQueue.peek()) != null) {
            int pcLength = pc.getBinary().length;
            if (framingLength + dataLength + pcLength < vmp.maxFrameLength) {
                pc = commandQueue.poll();
                if (pc == null) {
                    break;
                }
                l.add(pc);
                dataLength += pcLength;
                if (!vmp.blocking) {
                    break;
                }
            } else { // command doesn't fit into frame
                break;
            }
        }

        if (l.isEmpty()) {
            return null;
        }
        TcTransferFrame tf = frameFactory.makeFrame(vmp.vcId, dataLength);
        tf.setBypass(true);
        tf.setCommands(l);

        byte[] data = tf.getData();
        int offset = tf.getDataStart();
        for (PreparedCommand pc1 : l) {
            byte[] binary = cmdPostProcessor.process(pc1);
            int length = binary.length;
            System.arraycopy(binary, 0, data, offset, length);
            offset += length;
        }
        dataCount += l.size();
        frameFactory.encodeFrame(tf);
        return tf;
    }

    @Override
    public long getFirstFrameTimestamp() {
        if (commandQueue.isEmpty()) {
            return TimeEncoding.INVALID_INSTANT;
        }
        return commandQueue.peek().getGenerationTime();
    }

    @Override
    public VcUplinkManagedParameters getParameters() {
        return vmp;
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    @Override
    public void setDataAvailableSemaphore(Semaphore dataAvailableSemaphore) {
        this.dataAvailableSemaphore = dataAvailableSemaphore;

    }
}
