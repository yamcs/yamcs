package org.yamcs.ui;

import io.netty.handler.codec.http.HttpMethod;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.api.ws.WebSocketResponseHandler;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueEvent;
import org.yamcs.protobuf.Commanding.CommandQueueEvent.Type;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.web.websocket.CommandQueueResource;

/**
 * Controls yamcs command queues via Web
 * Allows to register one CommandQueueListener per (instance, yprocessor)
 * 
 * @author nm
 *
 */
public class CommandQueueControlClient implements ConnectionListener,  WebSocketClientCallback, WebSocketResponseHandler {
    YamcsConnector yconnector;

    List<CommandQueueListener> listeners=new CopyOnWriteArrayList<CommandQueueListener>(); //listeners for instance.chanelName

    public CommandQueueControlClient(YamcsConnector yconnector) {
        this.yconnector=yconnector;
        yconnector.addConnectionListener(this);
    }

    public void addCommandQueueListener(CommandQueueListener cmdQueueListener) {
        listeners.add(cmdQueueListener);
    }


    @Override
    public void connected(String url) {
        WebSocketRequest wsr = new WebSocketRequest(CommandQueueResource.RESOURCE_NAME, CommandQueueResource.OP_subscribe);
        yconnector.performSubscription(wsr, this, this);
    }

    
    private void sendLog(String message) {
        for(int i=0;i<listeners.size();i++) {
            listeners.get(i).log(message);
        }
    }

    /**
     * Send a message to the server to change the queue state.
     * If newState=ENABLED, then changing the queue state may result in some commands being sent.
     *  
     * @param cqi command queue info
     * @param rebuild The rebuild flag indicates that the command shall be rebuild (new timestamp, new pvt checks, etc) if the queue enabling results in commands being sent TODO
     */
    public void setQueueState(CommandQueueInfo cqi, boolean rebuild) {
        RestClient restClient = yconnector.getRestClient();
        // PATCH /api/processors/:instance/:processor/cqueues/:name
        String resource = "/processors/"+cqi.getInstance()+"/"+cqi.getProcessorName()+"/cqueues/"+cqi.getName()+"?state="+cqi.getState().toString();
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH);
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                sendLog("Exception setting queue state: "+exception.getMessage());
            }
        });
    }

    /**
     * Send a message to the server to release the command
     * @param cqe - reference to the command to be released
     * @param rebuild - indicate that the binary shall be rebuilt (new timestamp, new pvt checks, etc)
     * @throws YamcsException 
     */
    public void releaseCommand(CommandQueueEntry cqe, boolean rebuild) throws YamcsApiException, YamcsException {
        //PATCH /api/processors/:instance/:processor/cqueues/:cqueue/entries/:uuid
        RestClient restClient = yconnector.getRestClient();
        
        String resource = "/processors/"+cqe.getInstance()+"/"+cqe.getProcessorName()+"/cqueues/"+cqe.getQueueName()+"/entries/"+cqe.getUuid()+"?state=released";
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH);
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                sendLog("Exception releasing command: "+exception.getMessage());
            }
        });
    }

    /**
     * Send a message to the server to reject the command
     * @param cqe
     */
    public void rejectCommand(CommandQueueEntry cqe) {
        //PATCH /api/processors/:instance/:processor/cqueues/:cqueue/entries/:uuid
        RestClient restClient = yconnector.getRestClient();
        
        String resource = "/processors/"+cqe.getInstance()+"/"+cqe.getProcessorName()+"/cqueues/"+cqe.getQueueName()+"/entries/"+cqe.getUuid()+"?state=rejected";
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH);
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                sendLog("Exception releasing command: "+exception.getMessage());
            }
        });
    }

    @Override
    public void connecting(String url) {    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {}

    @Override
    public void disconnected() {}

    @Override
    public void log(String message) {}

    @Override
    public void onMessage(WebSocketSubscriptionData data) {
        if(data.hasCommandQueueInfo()) {
            CommandQueueInfo cmdQueueInfo = data.getCommandQueueInfo();
            System.out.println("onMessage cmdQueueInfo: "+cmdQueueInfo);
            for(CommandQueueListener cql: listeners) {
                cql.updateQueue(cmdQueueInfo);
            }
        }
        if(data.hasCommandQueueEvent()) {
            CommandQueueEvent cmdQueueEvent = data.getCommandQueueEvent();
            CommandQueueEntry cqe = cmdQueueEvent.getData();
            Type eventType = cmdQueueEvent.getType();
            System.out.println("evneType: "+eventType+" cqe: "+cqe+" listeners: "+listeners);
            for(CommandQueueListener cql: listeners) {
                switch(eventType) {
                case COMMAND_ADDED:
                    cql.commandAdded(cqe);
                    break;
                case COMMAND_REJECTED:
                    cql.commandRejected(cqe);
                    break;
                case COMMAND_SENT:
                    cql.commandSent(cqe);
                    break;
                }
            }
        }

    }


    @Override
    public void onException(WebSocketExceptionData e) {
        /*
        try {
            String eventName=msg.getStringProperty(HDR_EVENT_NAME);
            for(int i=0;i<listeners.size();i++) {
                CommandQueueListener cql=listeners.get(i);
                if("commandAdded".equals(eventName)) {
                    CommandQueueEntry cqe=(CommandQueueEntry)Protocol.decode(msg, CommandQueueEntry.newBuilder());
                    cql.commandAdded(cqe);
                } else if("commandRejected".equals(eventName)) {
                    CommandQueueEntry cqe=(CommandQueueEntry)Protocol.decode(msg, CommandQueueEntry.newBuilder());
                    cql.commandRejected(cqe);
                } else if("commandSent".equals(eventName)) {
                    CommandQueueEntry cqe=(CommandQueueEntry)Protocol.decode(msg, CommandQueueEntry.newBuilder());
                    cql.commandSent(cqe);
                } else if("queueUpdated".equals(eventName)) {
                    CommandQueueInfo cqi=(CommandQueueInfo)Protocol.decode(msg, CommandQueueInfo.newBuilder());
                    listeners.get(i).updateQueue(cqi);
                } else {
                    cql.log("received an unknown event on the command update queue: '"+eventName+"'");
                }
            }
        } catch (YamcsApiException e) {
            sendLog("error when decoding command queue info message: "+e);
        }*/
    }
}