package org.yamcs.yarch;


public class SpeedLimitStream extends AbstractStream implements StreamSubscriber {
    Stream input;
    volatile SpeedSpec speedSpec;
    private long ltst=-1; //time when the last tuple has been sent 
    private long ltt=-1; //time of the last tuple sent

    /**
     * maximum time to wait if SPEED is ORIGINAL 
     *  meaning that if there is a gap in the data longer than this, we continue)
     */
    public final static long MAX_WAIT_TIME=60000;

    public SpeedLimitStream(YarchDatabase dict, String name, TupleDefinition definition, SpeedSpec speedSpec){
        super(dict, name, definition);
        this.speedSpec=speedSpec;
    }


    public void setSubscribedStream(Stream s) {
        this.input=s;
    }

    @Override
    public void start() {
        input.start();
    }

    @Override
    public void onTuple(Stream s, Tuple t) {
        long waitTime=0;
        try {
            switch (speedSpec.getType()){
            case AFAP:
                break;
            case FIXED_DELAY:
                long ctime=System.currentTimeMillis();
                if(ltst!=-1) {
                    waitTime=(long)(speedSpec.getFixedDelay()-(ctime-ltst));
                }
                break;
            case ORIGINAL:
                long time=(Long)t.getColumn(speedSpec.column);
                if(ltt!=-1) {
                    waitTime=(long) ((time-ltt)/speedSpec.getMultiplier());
                }
                if(waitTime>MAX_WAIT_TIME) waitTime=MAX_WAIT_TIME;
                ltt=time;
                break;
            case STEP_BY_STEP: //TODO 
                break;
            }
            //System.out.println("sleeping "+waitTime+" ms");

            if(waitTime>0) {
                Thread.sleep(waitTime);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
        ltst=System.currentTimeMillis();
        emitTuple(t);
    }

    /**
     * Called when the subcribed stream is closed
     * we close this stream also.
     */
    @Override
    public void streamClosed(Stream stream) {
        close();
    }

    public void setSpeedSpec(SpeedSpec speedSpec) {
        this.speedSpec=speedSpec;

    }

    @Override
    public String toString() {
        return "SPEED LIMIT "+speedSpec.toString();
    }


    @Override
    protected void doClose() {
        input.close(); //TODO replace with removeSubscriber
    }

    public void changeSpeed(SpeedSpec speedSpec) {
        this.speedSpec = speedSpec;
    }
}
