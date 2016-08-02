package org.yamcs.ui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;


import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClient;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.api.ws.WebSocketResponseHandler;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.CommandQueueRequest;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;

/**
 * Controls yamcs command queues via Hornet
 * Allows to register one CommandQueueListener per (instance, yprocessor)
 * 
 * @author nm
 *
 */
public class CommandQueueControlClient implements ConnectionListener, WebSocketResponseHandler {
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
        receiveInitialConfig();
    }

    /*this should do some filtering for the registered listeners*/
    public void receiveInitialConfig() {
        try {
            WebSocketClient wsClient = yconnector.getWebSocketClient();
            WebSocketRequest wsr = new WebSocketRequest("bla", "bla");
            wsClient.sendRequest(wsr, this);

        } catch (Exception e) {
            e.printStackTrace();
            sendLog("error when retrieving the initial command queue list: "+e);
        }
    }


    private void sendLog(String message) {
        for(int i=0;i<listeners.size();i++) {
            listeners.get(i).log(message);
        }
    }

    /**
     * Send a message to the server to change the queue state.
     * If newState=ENABLED, then changing the queue state may result in some commands being sent.
     *  The rebuild flag indicates that the command shall be rebuild (new timestamp, new pvt checks, etc)
     */
    public void setQueueState(CommandQueueInfo cqi, boolean rebuild) throws YamcsApiException, YamcsException {
        RestClient restClient = yconnector.getRestClient();
    //    restClient.doGetRequest("/");
    }

    /**
     * Send a message to the server to release the command
     * @param cqe - reference to the command to be released
     * @param rebuild - indicate that the binary shall be rebuilt (new timestamp, new pvt checks, etc)
     * @throws YamcsException 
     */
    public void sendCommand(CommandQueueEntry cqe, boolean rebuild) throws YamcsApiException, YamcsException {
        RestClient restClient = yconnector.getRestClient();
        CommandQueueRequest cqr=CommandQueueRequest.newBuilder().setQueueEntry(cqe).setRebuild(rebuild).build();
      //  yclient.executeRpc(CMDQUEUE_CONTROL_ADDRESS, "sendCommand", cqr, null);
    }

    /**
     * Send a message to the server to reject the command
     * @param cqe
     */
    public void rejectCommand(CommandQueueEntry cqe) throws YamcsApiException, YamcsException {
        CommandQueueRequest cqr=CommandQueueRequest.newBuilder().setQueueEntry(cqe).build();
        RestClient restClient = yconnector.getRestClient();
        //yclient.executeRpc(CMDQUEUE_CONTROL_ADDRESS, "rejectCommand", cqr, null);
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