package org.yamcs.ui.eventviewer;

import static org.yamcs.api.Protocol.decode;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.api.core.client.SessionFailureListener;
import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnector;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.EventReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.utils.TimeEncoding;

public class YamcsEventReceiver implements ConnectionListener, EventReceiver, MessageHandler, SessionFailureListener {
    EventViewer eventViewer;
    YamcsConnector yconnector;
    YamcsClient yamcsClient;
  /*  volatile String url;
    volatile boolean connected, connecting;
    Protocol.ClientBuilder protocolBuilder;
    Thread connectingThread;
    YarchConnectData values;*/
    
    public YamcsEventReceiver(YamcsConnector yconnector) {
        this.yconnector=yconnector;
        yconnector.addConnectionListener(this);
    }
    
    @Override
    public void setEventViewer(EventViewer ev) {
        this.eventViewer=ev;
    }
    
   
    @Override
    public void onMessage(ClientMessage msg) {
        try {
            Event ev=(Event)decode(msg, Event.newBuilder());
            eventViewer.addEvent(ev);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void connected(String url) {
        try {
            yamcsClient=yconnector.getSession().newClientBuilder()
                .setDataConsumer(new SimpleString(yconnector.getConnectionParams().getInstance()+".events_realtime"), null).build();
            yamcsClient.dataConsumer.setMessageHandler(this);
        } catch (HornetQException e) {
            eventViewer.log(e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void beforeReconnect(HornetQException arg0) {
        //should not be called because reconnection is not configured in the factory
    }

    @Override
    public void retrievePastEvents() {
        PastEventParams params=YarchPastEventsDialog.showDialog(eventViewer);
        if(params.ok) {
            (new Thread(new EventDumpReceiver(params))).start();
        }
        
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

            params = new PastEventParams(TimeEncoding.currentInstant()-1000L*3600*24*30, TimeEncoding.currentInstant());

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

    class EventDumpReceiver implements Runnable {
        
        PastEventParams params;
        EventDumpReceiver(PastEventParams params) {
            this.params=params;
        }
        @Override
        public void run() {
            try {
                YamcsClient msgClient=yconnector.getSession().newClientBuilder().setRpc(true).setDataConsumer(null, null).
                    build();
               
                EventReplayRequest err=EventReplayRequest.newBuilder().build();
                ReplayRequest crr=ReplayRequest.newBuilder().setStart(params.start).setStop(params.stop)
                        .setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP).build())
                        .setEndAction(EndAction.QUIT).setEventRequest(err).build();
                
                SimpleString replayServer=Protocol.getYarchRetrievalControlAddress(yconnector.getConnectionParams().getInstance());
                StringMessage answer=(StringMessage) msgClient.executeRpc(replayServer, "createReplay", crr, StringMessage.newBuilder());
                SimpleString replayAddress=new SimpleString(answer.getMessage());
                eventViewer.log("Retrieving archived events from "+TimeEncoding.toString(params.start)+" to "+TimeEncoding.toString(params.stop));
                msgClient.executeRpc(replayAddress, "START", null, null);
                int count=0;
                //send events to the event viewer in batches, otherwise the swing will choke
                List<Event> events=new ArrayList<Event>();
                while(true) {
                    ClientMessage msg=msgClient.dataConsumer.receive(1000);
                    if(msg==null) {
                        if(!events.isEmpty()) {
                            eventViewer.addEvents(events);
                            events=new ArrayList<Event>();
                        }
                        continue;
                    }
                    if(Protocol.endOfStream(msg)) {
                        if(!events.isEmpty()) {
                            eventViewer.addEvents(events);
                        }
                        break;
                    }
                    count++;
                    events.add((Event)decode(msg, Event.newBuilder()));
                    if(events.size()>=1000) {
                        eventViewer.addEvents(events);
                        events=new ArrayList<Event>();
                    }
                }
                msgClient.close();
                eventViewer.log("Archive retrieval finished, retrieved "+count+" events");
            } catch (Exception e) {
                e.printStackTrace();
                eventViewer.log(e.toString());
            }
            
        }
    }

    @Override
    public void connectionFailed(HornetQException exception, boolean failedOver) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void connecting(String url) {
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
}