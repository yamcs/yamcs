package org.yamcs.ui.eventviewer;


import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.rest.BulkRestDataReceiver;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.api.ws.WebSocketResponseHandler;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.ui.YamcsConnector;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.websocket.EventResource;

import com.google.protobuf.InvalidProtocolBufferException;

public class YamcsEventReceiver implements ConnectionListener, EventReceiver, WebSocketClientCallback, WebSocketResponseHandler {
    EventViewer eventViewer;
    YamcsConnector yconnector;

    public YamcsEventReceiver(YamcsConnector yconnector) {
        this.yconnector = yconnector;
        yconnector.addConnectionListener(this);
    }

    @Override
    public void setEventViewer(EventViewer ev) {
        this.eventViewer=ev;
    }


    @Override
    public void onMessage(WebSocketSubscriptionData data) {
        if(data.hasEvent()) {
            Event ev = data.getEvent();
            eventViewer.addEvent(ev);
        }
    }

    @Override
    public void connected(String url) {
        WebSocketRequest wsr = new WebSocketRequest(EventResource.RESOURCE_NAME, EventResource.OP_subscribe);
        yconnector.performSubscription(wsr, this, this);
    }

    @Override
    public void retrievePastEvents() {
        PastEventParams params=YarchPastEventsDialog.showDialog(eventViewer);
        if(!params.ok) return;

        RestClient restClient = yconnector.getRestClient();
        StringBuilder resource = new StringBuilder().append("/archive/"+yconnector.getConnectionParams().getInstance()+"/downloads/events?");

        resource.append("start="+TimeEncoding.toString(params.start));
        resource.append("&stop="+TimeEncoding.toString(params.stop));
        
        int batchSize = 1000; //we do this to limit the number of swing calls
        List<Event> evList = new ArrayList<>(batchSize);
        AtomicInteger count = new AtomicInteger();
        CompletableFuture<Void> f = restClient.doBulkGetRequest(resource.toString(), new BulkRestDataReceiver() {
            @Override
            public void receiveData(byte[] data) throws YamcsApiException {
                try {
                    evList.add(Event.parseFrom(data));
                    if(evList.size()==batchSize) {
                        count.addAndGet(batchSize);
                        eventViewer.addEvents(new ArrayList<Event>(evList));
                        evList.clear();
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new YamcsApiException("Error parsing index result: "+e.getMessage());
                }
            }
            @Override
            public void receiveException(Throwable t) {
                t.printStackTrace();
                eventViewer.log("Received error when downloading events: "+t.getMessage());
            }
        });
        f.whenComplete((result, exception) -> {
            if(exception==null) {
                count.addAndGet(evList.size());
                eventViewer.addEvents(evList);
                eventViewer.log("Past Event retrieval finished; retrieved "+count.get()+" events");
            } else {
                exception.printStackTrace();
                count.addAndGet(evList.size());
                eventViewer.addEvents(evList);
                eventViewer.log("Past Event retrieval finished with error; retrieved "+count.get()+" events");
            }
        });
    }

    static class PastEventParams {
        boolean ok;
        long start, stop;
        public PastEventParams(long start, long stop) {
            super();
            this.start = start;
            this.stop = stop;
        }

    }

    static class YarchPastEventsDialog extends JDialog implements ActionListener {
        private static final long serialVersionUID = 1L;
        private PastEventParams params;
        JTextField startTextField;
        JTextField stopTextField;
        //JCheckBox sslCheckBox;
        private JTextField instanceTextField;

        static YarchPastEventsDialog dialog;
        YarchPastEventsDialog( JFrame parent ) {
            super(parent, "Retrieve past events", true);

            params = new PastEventParams(TimeEncoding.getWallclockTime()-1000L*3600*24*30, TimeEncoding.getWallclockTime());

            JPanel inputPanel, buttonPanel;
            JLabel lab;
            JButton button;

            // input panel

            inputPanel = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
            getContentPane().add(inputPanel, BorderLayout.CENTER);

            lab = new JLabel("Start: ");
            lab.setHorizontalAlignment(SwingConstants.RIGHT);
            c.gridy=1;c.gridx=0;c.anchor=GridBagConstraints.EAST;inputPanel.add(lab,c);
            startTextField = new JTextField(TimeEncoding.toString(params.start));
            c.gridy=1;c.gridx=1;c.anchor=GridBagConstraints.WEST;inputPanel.add(startTextField,c);

            lab = new JLabel("Stop: ");
            lab.setHorizontalAlignment(SwingConstants.RIGHT);
            c.gridy=2;c.gridx=0;c.anchor=GridBagConstraints.EAST;inputPanel.add(lab,c);
            stopTextField = new JTextField(TimeEncoding.toString(params.stop));
            c.gridy=2;c.gridx=1;c.anchor=GridBagConstraints.WEST;inputPanel.add(stopTextField,c);

            // button panel

            buttonPanel = new JPanel();
            getContentPane().add(buttonPanel, BorderLayout.SOUTH);

            button = new JButton("OK");
            button.setActionCommand("ok");
            button.addActionListener(this);
            getRootPane().setDefaultButton(button);
            buttonPanel.add(button);

            button = new JButton("Cancel");
            button.setActionCommand("cancel");
            button.addActionListener(this);
            buttonPanel.add(button);

            setMinimumSize(new Dimension(150, 100));
            setLocationRelativeTo(parent);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            pack();
        }

        @Override
        public void actionPerformed( ActionEvent e ) {
            if ( e.getActionCommand().equals("ok") ) {
                try {
                    params.start = TimeEncoding.parse(startTextField.getText());
                    params.stop = TimeEncoding.parse(stopTextField.getText());
                    params.ok = true;
                    setVisible(false);
                } catch (NumberFormatException x) {
                    // do not close the dialogue
                }
            } else if ( e.getActionCommand().equals("cancel") ) {
                params.ok = false;
                setVisible(false);
            }
        }

        public final static PastEventParams showDialog( JFrame parent) {
            if(dialog==null) dialog = new YarchPastEventsDialog(parent);
            dialog.setVisible(true);
            return dialog.params;
        }
    }

    
    @Override
    public void connecting(String url) {
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
        eventViewer.log("Connection to "+url+" failed: "+exception.getMessage());
    }

    @Override
    public void disconnected() {
        eventViewer.log("Disconnected ");
    }

    @Override
    public void log(String message) {
        
    }

    @Override
    public void onException(WebSocketExceptionData e) {
        eventViewer.log("Received error on subscription: "+e.getMessage());
    }
}