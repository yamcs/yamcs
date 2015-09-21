package org.yamcs.simulator;

import java.text.SimpleDateFormat;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

/**
 * Used to scheduled an LOS of 1 minute every 2 minutes
 */
public class SignalClock {

    String timeStamp = "";
    int losSeconds;
    int aosSeconds;

    Timer timer;
    Semaphore losSignal;
    Semaphore aosSignal;

    public Semaphore getLosSignal() {
        return losSignal;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public SignalClock(int losSeconds, int aosSeconds) {
        this.losSeconds = losSeconds;
        this.aosSeconds = aosSeconds;
    }

    public void startClock(){
        if(timer != null) {
            timer.cancel();
            timer.purge();
        }
        timer = new Timer();
        losSignal = new Semaphore(1);
        try {
            losSignal.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        aosSignal = new Semaphore(1);
        timer.scheduleAtFixedRate(new LosTask(), 1000, (losSeconds+aosSeconds)*1000);
        timer.scheduleAtFixedRate(new AosTask(), 1000 + losSeconds * 1000, (losSeconds+aosSeconds)*1000);
    }

    class LosTask extends TimerTask {
        @Override
        public void run() {
            timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date());
            try {
                // case starting los
                aosSignal.acquire();
                losSignal.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class AosTask extends TimerTask {
        @Override
        public void run() {
            try {
                // case starting aos
                aosSignal.release();
                losSignal.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public Semaphore getAosSignal() {
        return aosSignal;
    }
}
