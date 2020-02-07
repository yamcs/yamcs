package org.yamcs.tctm;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;

import com.google.common.util.concurrent.RateLimiter;

/**
 * 
 */
public abstract class AbstractThreadedTcDataLink extends AbstractTcDataLink implements Runnable {
    Thread thread;
    RateLimiter rateLimiter;
    protected BlockingQueue<PreparedCommand> commandQueue;
    long initialDelay;
  

    public AbstractThreadedTcDataLink(String yamcsInstance, String linkName, YConfiguration config)
            throws ConfigurationException {
        super(yamcsInstance, linkName, config);
        if (config.containsKey("tcQueueSize")) {
            commandQueue = new LinkedBlockingQueue<>(config.getInt("tcQueueSize"));
        } else {
            commandQueue = new LinkedBlockingQueue<>();
        }

        initialDelay = config.getLong("initialDelay", 0);

        if (config.containsKey("tcMaxRate")) {
            rateLimiter = RateLimiter.create(config.getInt("tcMaxRate"));
        }
        
        
    }

    @Override
    protected void doStart() {

        try {
            startUp();
        } catch (Exception e) {
            notifyFailed(e);
        }
        thread = new Thread(this);
        thread.start();
        notifyStarted();
    }

    @Override
    protected void doStop() {
        try {
            shutDown();
        } catch (Exception e) {
            notifyFailed(e);
        }
        commandQueue.clear();
        commandQueue.offer(SIGNAL_QUIT);
        try {
            thread.join();
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for thread shutdown");
            Thread.currentThread().interrupt();
        }
        notifyStopped();
    }

    /**
     * Sends
     */
    @Override
    public void uplinkTc(PreparedCommand pc) {
        if (!commandQueue.offer(pc)) {
            log.warn("Cannot put command {} in the queue, because it's full; sending NACK", pc);
            commandHistoryPublisher.commandFailed(pc.getCommandId(), getCurrentTime(), "Link " + name + ": queue full");
        }
    }

    @Override
    public void run() {
        if (initialDelay > 0) {
            try {
                Thread.sleep(initialDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        while (isRunning()) {
            doHousekeeping();
            try {
                PreparedCommand pc = commandQueue.poll(housekeepingInterval, TimeUnit.MILLISECONDS);
                if (pc == null) {
                    continue;
                }
                if (pc == SIGNAL_QUIT) {
                    return;
                }

                if (rateLimiter != null) {
                    rateLimiter.acquire();
                }
                uplinkCommand(pc);
            } catch (Exception e) {
                log.error("Error when sending command: ", e);
                throw new RuntimeException(e);
            }
        }
    }

    protected abstract void uplinkCommand(PreparedCommand pc) throws IOException;

    protected abstract void startUp() throws Exception;

    protected abstract void shutDown() throws Exception;
    
    /**
     * Called each {@link #housekeepingInterval} milliseconds, can be used to establish tcp connections or similar things
     */
    protected void doHousekeeping() {
    }
}
