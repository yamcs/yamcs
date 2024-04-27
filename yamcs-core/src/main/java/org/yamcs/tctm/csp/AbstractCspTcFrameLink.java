package org.yamcs.tctm.csp;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractTcDataLink;



public abstract class AbstractCspTcFrameLink extends AbstractTcDataLink implements Runnable {
    Semaphore dataAvailableSemaphore = new Semaphore(0);

    protected BlockingQueue<PreparedCommand> commandQueue;
    boolean blockSenderOnQueueFull;
    int initialDelay;

    private CspFrameFactory frameFactory;
    private CspManagedParameters cspManagedParameters; 

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration config) throws ConfigurationException {
        super.init(yamcsInstance, linkName, config);

        initialDelay = config.getInt("initialDelay", 2000);
        blockSenderOnQueueFull = config.getBoolean("blockSenderOnQueueFull", false);

        int queueSize = config.getInt("cspFrameQueueSize", 10);
        commandQueue = new ArrayBlockingQueue<>(queueSize);

        cspManagedParameters = new CspManagedParameters(config);
        frameFactory = cspManagedParameters.getFrameFactory();
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    @Override
    public boolean sendCommand(PreparedCommand preparedCommand) {
        int framingLength = frameFactory.getFramingLength();
        int pcLength = cmdPostProcessor.getBinaryLength(preparedCommand);

        if (framingLength + pcLength > getMaxFrameLength()) {
            log.warn("CSP Frame {} does not fit into ({} + {} > {})", preparedCommand.getLoggingId(), framingLength, pcLength, getMaxFrameLength());
            failedCommand(preparedCommand.getCommandId(), "CSP Frame too large to fit in a frame; cmd size: " + pcLength + "; max frame length: " + getMaxFrameLength() + "; frame overhead: " + framingLength);

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
                failedCommand(preparedCommand.getCommandId(), "Command Queue full");
            }
        }

        return true;
    }

    @Override
    public void run() {
        if (initialDelay > 0) {
            try {
                Thread.sleep(initialDelay);
                initialDelay = 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        try {
            startUp();
        } catch (Exception e) {
            log.error("Failed to startUp", e);
        }

        while(isRunningAndEnabled()) {
            doHousekeeping();

            byte[] cspFrame = getFrame();
            uplinkCspFrame(cspFrame);
        }
    }

    /**
     * Get the next frame blocking until one is available or until {@link #quit()}
     * is called.
     * 
     * @return next frame or null if the multiplexer has been closed or the thread
     *         interrupted
     */
    public byte[] getFrame() {
        byte[] cspFrame = null;
        try{
            cspFrame = getCspFrame();

        } catch (Exception e) {
            e.printStackTrace();
        }
        if (cspFrame != null) {
            return cspFrame;
        }

        try {
            dataAvailableSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        return null;
    }

    public byte[] getCspFrame() {
        if (commandQueue.isEmpty()) {
            return null;
        }

        int framingLength = frameFactory.getFramingLength();
        PreparedCommand pc;

        int dataLength = 0;
        while ((pc = commandQueue.peek()) != null) {
            int pcLength = cmdPostProcessor.getBinaryLength(pc);
            if (framingLength + pcLength <= getMaxFrameLength()) {
                pc = commandQueue.poll();
                if (pc == null) {
                    break;
                }
                dataLength += pcLength;
                if (!cspManagedParameters.multiplePacketsPerFrame) {
                    break;
                }

            } else { // command doesn't fit into frame
                break;
            }
        }

        if (pc == null) {
            return null;
        }

        byte[] cspFrame = frameFactory.makeFrame(dataLength);
        byte[] pcBinary = postprocess(pc);

        int length = pcBinary.length;

        int offset = cspManagedParameters.getCspHeader().length;
        System.arraycopy(pcBinary, 0, cspFrame, offset, length);

        dataCount.getAndAdd(1);
        return frameFactory.encodeFrame(cspFrame);
    }

    public int getMaxFrameLength() {
        return cspManagedParameters.getMaxFrameLength();
    }

    @Override
    protected void doStart() {
        if (!isDisabled()) {
            try {
                Thread thread = new Thread(this);
                thread.setName(getClass().getSimpleName() + "-" + linkName);
                thread.start();

            } catch (Exception e) {
                notifyFailed(e);
                return;
            }
        }
        notifyStarted();
    }

    @Override
    protected void doEnable() {
        Thread thread = new Thread(this);
        thread.setName(getClass().getSimpleName() + "-" + linkName);
        thread.start();
    }

    /**
     * Called
     * 
     * @param pc
     * @throws IOException
     */
    protected abstract void uplinkCspFrame(byte[] cspFrame);

    /**
     * Called at start up (if the link is enabled) or when the link is enabled
     * 
     * @throws Exception
     */
    protected abstract void startUp();

    /**
     * Called each {@link #housekeepingInterval} milliseconds, can be used to
     * establish tcp connections or similar
     * things
     */
    protected void doHousekeeping() {

    }

    /**
     * Called at shutdown (if the link is enabled) or when the link is disabled
     * 
     * @throws Exception
     */
    protected abstract void shutDown() throws Exception;

    static public class CspManagedParameters {
        public enum FrameErrorDetection {
            NONE("NONE"), CRC16("CRC16"), CRC32("CRC32");

            private final String value;

            FrameErrorDetection(String value) {
                this.value = value;
            }
    
            public String getValue() {
                return value;
            }

            public static FrameErrorDetection fromValue(String value) {
                for (FrameErrorDetection enumValue : FrameErrorDetection.values()) {
                    if (enumValue.value == value) {
                        return enumValue;
                    }
                }
                throw new IllegalArgumentException("No enum constant with value " + value);
            }
        };

        boolean multiplePacketsPerFrame;
        int maxFrameLength;
        byte[] cspHeader;
        FrameErrorDetection errorDetection;

        public CspManagedParameters(String errorDetection, int maxFrameLength, boolean multiplePacketsPerFrame, byte[] cspHeader) {
            this.multiplePacketsPerFrame = multiplePacketsPerFrame;

            this.maxFrameLength = maxFrameLength;
            if (maxFrameLength < 8 || maxFrameLength > 0xFFFF) {
                throw new ConfigurationException("Invalid CSP frame length " + maxFrameLength);
            }

            this.errorDetection = FrameErrorDetection.fromValue(errorDetection);
            this.cspHeader = cspHeader;
        }

        public CspManagedParameters(YConfiguration config) {
            maxFrameLength = config.getInt("maxFrameLength");
            if (maxFrameLength < 8 || maxFrameLength > 0xFFFF) {
                throw new ConfigurationException("Invalid CSP frame length " + maxFrameLength);
            }

            errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class, FrameErrorDetection.NONE);
            multiplePacketsPerFrame = config.getBoolean("multiplePacketsPerFrame", false);

            try {
                this.cspHeader = Hex.decodeHex(config.getString("cspHeader"));
            } catch (DecoderException e) {
                e.printStackTrace();
                throw new ConfigurationException(e);
            }

        }

        public CspFrameFactory getFrameFactory() {
            return new CspFrameFactory(this);
        }

        /**
         * Returns the error detection used for this virtual channel.
         */
        public FrameErrorDetection getErrorDetection() {
            return errorDetection;
        }

        public int getMaxFrameLength() {
            return maxFrameLength;
        }

        public byte[] getCspHeader() {
            return cspHeader;
        }
    }
}
