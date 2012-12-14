package org.yamcs.api;

import java.util.concurrent.ArrayBlockingQueue;

import org.hornetq.api.core.HornetQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;


public class HornetQEventProducer extends AbstractEventProducer implements ConnectionListener {
    YamcsConnector yconnector;
    YamcsClient yclient;
    static Logger logger=LoggerFactory.getLogger(HornetQEventProducer.class);
    
    static final int MAX_QUEUE_SIZE=1000;
    ArrayBlockingQueue<Event> queue=new ArrayBlockingQueue<Event>(MAX_QUEUE_SIZE);
    
    HornetQEventProducer(YamcsConnectData ycd) {
        yconnector=new YamcsConnector();
        yconnector.addConnectionListener(this);
        yconnector.connect(ycd);
        address=Protocol.getEventRealtimeAddress(ycd.instance);
    }
    
    
    @Override
    public void connecting(String url) { }

    @Override
    public void connected(String url) {
        try {
            yclient=yconnector.getSession().newClientBuilder().setDataProducer(true).build();
            while(!queue.isEmpty()) {
                yclient.sendData(address, ProtoDataType.EVENT, queue.poll());
            }
        } catch (HornetQException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
    }

    @Override
    public void disconnected() {
    }

    @Override
    public void log(String message) {
    }

    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendEvent(org.yamcs.protobuf.Yamcs.Event)
     */
    @Override
    public synchronized void sendEvent(Event event) throws  HornetQException {
        logger.debug("Sending Event: {}", event.getMessage());
        if(yconnector.isConnected()) {
            yclient.sendData(address, ProtoDataType.EVENT, event);
        } else {
            queue.offer(event);
        }
    }
    
    @Override
    public String toString() {
        return HornetQEventProducer.class.getName()+" connected to "+yconnector.getUrl();
    }
 
}
