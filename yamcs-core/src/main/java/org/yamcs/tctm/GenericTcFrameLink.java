package org.yamcs.tctm;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.srs3.Srs3FrameFactory;
import org.yamcs.tctm.srs3.Srs3ManagedParameters;


public abstract class GenericTcFrameLink extends AbstractTcDataLink implements Runnable {
    Semaphore dataAvailableSemaphore = new Semaphore(0);
    BlockingQueue<PreparedCommand> commandQueue;

    boolean blockSenderOnQueueFull;
    int initialDelay;

    private Srs3FrameFactory frameFactory;
    private Srs3ManagedParameters srs3Mp; 

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
            throw new ConfigurationException("Invalid Frame length " + maxFrameLength);
        }

        if (config.containsKey("srs3")) {
            YConfiguration srs3 = config.getConfig("srs3");

            srs3Mp = new Srs3ManagedParameters(srs3, maxFrameLength);
            frameFactory = srs3Mp.getFrameFactory();
        }
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
        int framingLength = 0;        
        if (frameFactory != null)
            framingLength += frameFactory.getInnerFramingLength();

        int pcLength = cmdPostProcessor.getBinaryLength(preparedCommand);

        if (framingLength + pcLength > getMaxFrameLength()) {
            log.warn("Frame {} does not fit into ({} + {} > {})", preparedCommand.getLoggingId(), framingLength, pcLength, getMaxFrameLength());
            failedCommand(preparedCommand.getCommandId(), "Frame too large to fit in a frame; cmd size: " + pcLength + "; max frame length: " + getMaxFrameLength() + "; frame overhead: " + framingLength);

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

            byte[] frame = getFrame();
            uplinkFrame(frame);
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
        byte[] srs3Frame = null;
        try{
            srs3Frame = constructFrame();

        } catch (Exception e) {
            e.printStackTrace();
        }
        if (srs3Frame != null) {
            return srs3Frame;
        }

        try {
            dataAvailableSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        return null;
    }

    public byte[] constructFrame() {
        if (commandQueue.isEmpty()) {
            return null;
        }

        PreparedCommand pc;
        int framingLength = 0;

        if (frameFactory != null) {
            framingLength = frameFactory.getInnerFramingLength();  
        }
        
        if ((pc = commandQueue.peek()) != null) {
            int pcLength = cmdPostProcessor.getBinaryLength(pc);

            // Set pc to null which will be later assigned if the length condition is satisfied
            pc = null;
            if (framingLength + pcLength <= getMaxFrameLength())
                pc = commandQueue.poll();
        }  

        if (pc == null)
            return null;

        byte[] pcBinary = postprocess(pc);
        int length = pcBinary.length;

        if (frameFactory != null) {
            byte[] srs3Frame = frameFactory.makeFrame(length);
            AtomicInteger dataStart, dataEnd;

            dataStart = new AtomicInteger(0);
            dataEnd = new AtomicInteger(0);
    
            if (srs3Mp.getEncryption() != null) {
                dataStart.set(dataStart.get() + frameFactory.getIVLength());
                dataEnd.set(dataEnd.get() + frameFactory.getIVLength());
            }
    
            int srs3Offset = frameFactory.getCspHeaderLength() + frameFactory.getRadioHeaderLength() + frameFactory.getSpacecraftIdLength();
            dataEnd.set(dataEnd.get() + srs3Offset);
    
            System.arraycopy(pcBinary, 0, srs3Frame, dataStart.get() + srs3Offset, length);
            dataEnd.set(dataEnd.get() + length + frameFactory.getPaddingLength(length));
    
            dataOut(1, srs3Frame.length);
            return frameFactory.encodeFrame(srs3Frame, dataStart, dataEnd, length + frameFactory.getCspHeaderLength());
        }

        return pcBinary;
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
    protected abstract void uplinkFrame(byte[] frame);

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
        return maxFrameLength;
    }


    /**
     * Called at shutdown (if the link is enabled) or when the link is disabled
     * 
     * @throws Exception
     */
    protected abstract void shutDown() throws Exception;
}
