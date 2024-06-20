package org.yamcs.tctm.csp;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.ccsds.encryption.SymmetricEncryption;
import org.yamcs.utils.YObjectLoader;


public abstract class AbstractCspTcFrameLink extends AbstractTcDataLink implements Runnable {
    Semaphore dataAvailableSemaphore = new Semaphore(0);
    BlockingQueue<PreparedCommand> commandQueue;

    boolean blockSenderOnQueueFull;
    int initialDelay;

    private CspFrameFactory frameFactory;
    private CspManagedParameters cspManagedParameters; 

    int maxFrameLength;

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration config) throws ConfigurationException {
        super.init(yamcsInstance, linkName, config);

        initialDelay = config.getInt("initialDelay", 2000);
        blockSenderOnQueueFull = config.getBoolean("blockSenderOnQueueFull", false);

        int queueSize = config.getInt("tcQueueSize", 10);
        commandQueue = new ArrayBlockingQueue<>(queueSize);

        maxFrameLength = config.getInt("maxFrameLength");

        if (maxFrameLength < 8 || maxFrameLength > 0xFFFF) {
            throw new ConfigurationException("Invalid CSP frame length " + maxFrameLength);
        }

        cspManagedParameters = new CspManagedParameters(config, maxFrameLength);
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
            if (dataLength + framingLength + pcLength <= getMaxFrameLength()) {
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

        byte[] pcBinary = postprocess(pc);
        int length = pcBinary.length;

        AtomicInteger dataStart, dataEnd;

        dataStart = new AtomicInteger(0);
        dataEnd = new AtomicInteger(dataStart.get() + frameFactory.getPaddingAndDataLength());

        if (cspManagedParameters.getEncryption() != null) {
            dataStart.set(dataStart.get() + frameFactory.getIVLength());
            dataEnd.set(dataEnd.get() + frameFactory.getIVLength());
        }

        byte[] cspFrame = frameFactory.makeFrame(dataLength);

        int cspOffset = frameFactory.getCspHeaderLength();
        int radioOffset = frameFactory.getRadioHeaderLength();
        dataEnd.set(dataEnd.get() + cspOffset + radioOffset);

        System.arraycopy(pcBinary, 0, cspFrame, dataStart.get() + cspOffset + radioOffset, length);

        dataOut(1, cspFrame.length);
        return frameFactory.encodeFrame(cspFrame, dataStart, dataEnd);
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

    public int getMaxFrameLength() {
        return cspManagedParameters.maxFrameLength;
    }


    /**
     * Called at shutdown (if the link is enabled) or when the link is disabled
     * 
     * @throws Exception
     */
    protected abstract void shutDown() throws Exception;

    static public class CspManagedParameters {
        byte[] cspHeader;
        byte[] radioHeader;

        // which error detection algorithm to use (null = no checksum)
        protected ErrorDetectionWordCalculator errorDetectionCalculator;

        // Encryption parameters
        protected SymmetricEncryption se;

        int maxFrameLength;
        boolean multiplePacketsPerFrame;
        boolean enforceFrameLength;

        // Default CRC and Encryption field sizes, if enforceFrameLength = true
        int defaultCrcSize;

        public CspManagedParameters(YConfiguration config, int maxFrameLength) {
            this.maxFrameLength = maxFrameLength;
            multiplePacketsPerFrame = config.getBoolean("multiplePacketsPerFrame", false);
            enforceFrameLength = config.getBoolean("enforceFrameLength", false);

            if (enforceFrameLength) {
                if (!config.containsKey("defaultCrcSize")) {
                    throw new IllegalArgumentException("If `enforceFrameLength=true`, then the field `defaultCrcSize` must be set");
                }
                defaultCrcSize = config.getInt("defaultCrcSize");
            }

            if (config.containsKey("radioHeader"))
                radioHeader = config.getBinary("radioHeader");

            errorDetectionCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);
            if (errorDetectionCalculator != null) {
                if (!enforceFrameLength)
                    throw new IllegalArgumentException("If `errorDetection` is set, then the field `enforceFrameLength` must be set to true");
            
                if (radioHeader == null) {
                    throw new IllegalArgumentException("If `errorDetection` is set, then the field `radioHeader` must be set");
                }
            }

            cspHeader = config.getBinary("cspHeader");

            if (config.containsKey("encryption")) {
                if (!enforceFrameLength)
                    throw new IllegalArgumentException("If `encryption` is set, then the field `enforceFrameLength` must be set to true");

                if (radioHeader == null) {
                    throw new IllegalArgumentException("If `errorDetection` is set, then the field `radioHeader` must be set");
                }

                YConfiguration en = config.getConfig("encryption");

                String className = en.getString("class");
                YConfiguration enConfig = en.getConfigOrEmpty("args");

                se = YObjectLoader.loadObject(className);
                se.init(enConfig);
            }
        }

        public CspFrameFactory getFrameFactory() {
            return new CspFrameFactory(this);
        }

        /**
         * Returns the error detection used for this virtual channel.
         */
        public ErrorDetectionWordCalculator getErrorDetection() {
            return errorDetectionCalculator;
        }

        public int getMaxFrameLength() {
            return maxFrameLength;
        }

        public byte[] getCspHeader() {
            return cspHeader;
        }

        public byte[] getRadioHeader() {
            return radioHeader;
        }

        public SymmetricEncryption getEncryption() {
            return se;
        }

        public int getDefaultCrcSize() {
            return defaultCrcSize;
        }
    }
}
