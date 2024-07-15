package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.tctm.ccsds.TcManagedParameters.TcVcManagedParameters;
import org.yamcs.tctm.srs3.CspFrameFactory;
import org.yamcs.utils.TimeEncoding;

/**
 * Assembles command packets into TC frames as per CCSDS 232.0-B-4.
 * <p>
 * All frames have the bypass flag set (i.e. they are BD frames).
 * 
 */
public class TcPacketHandler extends AbstractTcDataLink implements VcUplinkHandler {
    protected BlockingQueue<PreparedCommand> commandQueue;
    final TcVcManagedParameters vmp;
    private TcFrameFactory frameFactory;
    private CspFrameFactory cspFrameFactory;
    boolean blockSenderOnQueueFull;
    private Semaphore dataAvailableSemaphore;

    public TcPacketHandler(String yamcsInstance, String linkName, TcVcManagedParameters vmp)
            throws ConfigurationException {
        super.init(yamcsInstance, linkName, vmp.config);
        this.vmp = vmp;
        this.frameFactory = vmp.getFrameFactory();
        this.cspFrameFactory = vmp.getCspFrameFactory();

        int queueSize = vmp.config.getInt("tcQueueSize", 10);
        blockSenderOnQueueFull = vmp.config.getBoolean("blockSenderOnQueueFull", false);
        commandQueue = new ArrayBlockingQueue<>(queueSize);
    }

    @Override
    public boolean sendCommand(PreparedCommand preparedCommand) {
        int framingLength = frameFactory.getFramingLength(vmp.vcId);
        if (cspFrameFactory != null) {
            framingLength += cspFrameFactory.getFramingLength();
        }

        int pcLength = cmdPostProcessor.getBinaryLength(preparedCommand);
        if (framingLength + pcLength > vmp.maxFrameLength) {
            log.warn("Command {} does not fit into frame ({} + {} > {})", preparedCommand.getLoggingId(), framingLength,
                    pcLength, vmp.maxFrameLength);
            failedCommand(preparedCommand.getCommandId(),
                    "Command too large to fit in a frame; cmd size: " + pcLength + "; max frame length: "
                            + vmp.maxFrameLength + "; frame overhead: " + framingLength + "; cspHeader included: " + (cspFrameFactory != null? "Yes | " + cspFrameFactory.getCspHeaderLength() + " bytes" : "No"));
            return true;
        }

        if (blockSenderOnQueueFull) {
            try {
                commandQueue.put(preparedCommand);
                dataAvailableSemaphore.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failedCommand(preparedCommand.getCommandId(), "Interrupted");
            }
        } else {
            if (commandQueue.offer(preparedCommand)) {
                dataAvailableSemaphore.release();
            } else {
                failedCommand(preparedCommand.getCommandId(), "queue full");
            }
        }
        return true;
    }

    @Override
    public TcTransferFrame getFrame() {

        if (commandQueue.isEmpty()) {
            return null;
        }
        int framingLength = frameFactory.getFramingLength(vmp.vcId);
        int cspRadioHeaderFramingLength = cspFrameFactory.getFramingLength();

        int dataLength = 0;
        List<PreparedCommand> l = new ArrayList<>();
        PreparedCommand pc;
        while ((pc = commandQueue.peek()) != null) {
            int pcLength = cmdPostProcessor.getBinaryLength(pc);
            if (framingLength + dataLength + pcLength + cspRadioHeaderFramingLength <= vmp.maxFrameLength) {
                pc = commandQueue.poll();
                if (pc == null) {
                    break;
                }
                l.add(pc);
                dataLength += pcLength;
                if (!vmp.multiplePacketsPerFrame) {
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
            byte[] binary = postprocess(pc1);
            if (binary == null) {
                continue;
            }
            int length = binary.length;
            System.arraycopy(binary, 0, data, offset, length);
            offset += length;
        }

        frameFactory.encodeFrame(tf);

        if (cspFrameFactory != null) {
            AtomicInteger dataStart, dataEnd;

            dataStart = new AtomicInteger(0);
            dataEnd = new AtomicInteger(dataStart.get() + cspFrameFactory.getPaddingAndDataLength());
    
            if (cspFrameFactory.getCspManagedParameters().getEncryption() != null) {
                dataStart.set(dataStart.get() + cspFrameFactory.getIVLength());
                dataEnd.set(dataEnd.get() + cspFrameFactory.getIVLength());
            }

            byte[] cspFrame = cspFrameFactory.makeFrame(data.length);

            int cspOffset = cspFrameFactory.getCspHeaderLength();
            int radioOffset = cspFrameFactory.getRadioHeaderLength();
            dataEnd.set(dataEnd.get() + cspOffset + radioOffset);

            System.arraycopy(data, 0, cspFrame, dataStart.get() + cspOffset + radioOffset, data.length);

            // Add the cspHeader + radioHeader + Encryption to the CCSDS Frame
            cspFrameFactory.encodeFrame(cspFrame, dataStart, dataEnd);

            // Set the cspFrame
            tf.setData(cspFrame);
            tf.setDataStart(dataStart.get());
            tf.setDataEnd(dataEnd.get());
        }

        // BC frames contain no command but we still count it as one item out
        var count = tf.commands == null ? 1 : tf.commands.size();
        dataOut(count, tf.getData().length);

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

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
