package org.yamcs.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsConnectDialog;
import org.yamcs.api.YamcsConnector;
import org.yamcs.protobuf.GapRequest.CcsdsGap;
import org.yamcs.protobuf.GapRequest.CcsdsGapRequest;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.ui.archivebrowser.ArchiveIndexListener;
import org.yamcs.ui.archivebrowser.PrefsToolbar;
import org.yamcs.utils.TimeEncoding;



public class CcsdsCompletenessGui extends JFrame implements ArchiveIndexListener, ConnectionListener{
    private static final long serialVersionUID = 1L;

    final YamcsConnector yconnector;
    final YamcsArchiveIndexReceiver indexReceiver;
    MyTableModel ccsdsTableModel = new MyTableModel(MyTableModel.Type.Ccsds);

    PrefsToolbar prefs;
    YamcsClient yclient;
    final static public String DASS_PLAYBACK_REQUEST_ADDRESS = "DassPlaybackRequest";
    long maxDassRetrievalTime = 24 *3600 *1000;

    public CcsdsCompletenessGui(YamcsConnector yconnector) throws Exception {
        setTitle("CCSDS Index");
        this.yconnector = yconnector;
        YConfiguration conf = YConfiguration.getConfiguration("yamcs-ui");
        if(conf.containsKey("maxDassRetrievalTime")) {
            maxDassRetrievalTime = conf.getInt("maxDassRetrievalTime") * 3600*1000;
        }
          
        indexReceiver = new YamcsArchiveIndexReceiver(yconnector);
        yconnector.addConnectionListener(this);

        indexReceiver.setIndexListener(this);
        buildGui();
    }

    private void buildGui() {

        prefs = new PrefsToolbar();
        prefs.reloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshData();
            }
        });

        getContentPane().add(prefs, BorderLayout.NORTH);

        getContentPane().add(getTable(), BorderLayout.CENTER);

        JPanel buttons=new JPanel();

        JButton b=new JButton("Send DaSS Request");
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendDassRequest();
            }
        });
        buttons.add(b);

        JButton closeButton=new JButton("Close");
        buttons.add(closeButton);
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CcsdsCompletenessGui.this.setVisible(false);

            }
        });

        getContentPane().add(buttons, BorderLayout.SOUTH);

        pack();
    }

    private void sendDassRequest() {
        ReplayParamDialog rpd = new ReplayParamDialog(this);
        rpd.setVisible(true);
        if(!rpd.ok) return;
        
        long mergeTime = rpd.getMergeTimeMinutes()*60*1000;
        long maxInterval = rpd.getMaxIntervalMinutes()*60*1000;
            
        List<Gap> consolidated = consolidate(ccsdsTableModel.getSelected(), mergeTime, maxInterval);
        long total =0;
        for(Gap g:consolidated) {
            total+=(g.end - g.start);
        }
        if(total > 24*3600*1000) {
            JOptionPane.showConfirmDialog(this, "The request will result in downloading "+getDuration(total)
                    + " of data.\n Are you sure?",
                    "Long download",
                    JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
        }
        
        try {
            if(yclient == null) {
                yclient = yconnector.getSession().newClientBuilder().setDataProducer(true).build();
            }
            CcsdsGapRequest.Builder requestb = CcsdsGapRequest.newBuilder();

            for(Gap g: consolidated) {
                int apid=Integer.valueOf(g.name.substring(5));
                CcsdsGap cg=CcsdsGap.newBuilder().setApid(apid).setStartTime(g.start).setStopTime(g.end).build();
                requestb.addGaps(cg);
            }
            CcsdsGapRequest request = requestb.build();
           
            ClientMessage msg = yconnector.getSession().session.createMessage(false);
            Protocol.encode(msg, request);
            yclient.dataProducer.send(new SimpleString(prefs.getInstance()+"."+DASS_PLAYBACK_REQUEST_ADDRESS), msg);
        } catch (HornetQException e) {
            e.printStackTrace();
        }

    }
    static List<Gap> consolidate( List<Gap> list, long mergeTime, long maxInterval) {
        List<Gap> consolidated = new ArrayList<Gap>();
        Map<String, Gap> lastGap = new HashMap<String, Gap>();
        
        Iterator<Gap> it = list.iterator();
        
        while(it.hasNext()) {
            Gap g=it.next();
            Gap prev = lastGap.get(g.name);
            if(g.start==-1) {
                g.start = g.end - maxInterval;
            }
            if(g.end==-1) {
                g.end = g.start + maxInterval;
            }
            
            Gap extrag = null;
            if(g.end-g.start > 2*maxInterval+mergeTime) {
                extrag = new Gap(g.name, g.end-maxInterval, g.end, -1, g.endSeqCount);
                g.end = g.start + maxInterval;
            }
            
            if((prev!=null) && ((g.start - prev.end) < mergeTime)) {
                prev.end = g.end;
            } else {
                consolidated.add(g);
                lastGap.put(g.name, g);
            }
            if(extrag!=null) {
                consolidated.add(extrag);
                lastGap.put(g.name, extrag);
            }
        }
        return consolidated;
    }
    
    private JComponent getTable() {
        JTable table=new JTable(ccsdsTableModel);
        table.getColumn("Item").setPreferredWidth(120);
        table.getColumn("First Time").setPreferredWidth(180);
        table.getColumn("Last Time").setPreferredWidth(180);
        table.getColumn("Duration").setPreferredWidth(120);

        Dimension size = table.getPreferredScrollableViewportSize();
        table.setPreferredScrollableViewportSize(new Dimension(table.getPreferredSize().width, size.height));
        JScrollPane scrollPane = new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        table.setFillsViewportHeight(true);

        return scrollPane;
    }


    @Override
    public void connected(String url) {
        List<String> instances = yconnector.getYamcsInstances();
        yclient = null;
        prefs.setInstances(instances);
        System.out.println("connected to "+url);
        refreshData();
    }

    private void refreshData() {
        ccsdsTableModel.clear();
        indexReceiver.getIndex(prefs.getInstance(), prefs.getInterval()); 
    }

    @Override
    public void connecting(String url) {
        System.out.println("connecting to "+url);
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
        System.out.println("Connection to "+url+" failed: "+exception);

    }
    @Override
    public void disconnected() {
        // TODO Auto-generated method stub

    }
    @Override
    public void log(String arg0) {
        // TODO Auto-generated method stub

    }
    @Override
    public void receiveArchiveRecords(IndexResult ir) {
        ccsdsTableModel.populate(ir.getRecordsList());
    }

    @Override
    public void receiveArchiveRecordsError(String errMsg) {
        System.out.println("Error receiving archive records: "+errMsg);
    }
    @Override
    public void receiveArchiveRecordsFinished() {

    }
    @Override
    public void receiveTags(List<ArchiveTag> arg0) {
        // TODO Auto-generated method stub

    }
    @Override
    public void receiveTagsFinished() {
        // TODO Auto-generated method stub

    }
    @Override
    public void tagAdded(ArchiveTag arg0) {
        // TODO Auto-generated method stub

    }
    @Override
    public void tagChanged(ArchiveTag tag0, ArchiveTag tag1) {
        // TODO Auto-generated method stub

    }
    @Override
    public void tagRemoved(ArchiveTag tag) {
        // TODO Auto-generated method stub

    }
    //get human readable duration
    static String getDuration(long d) {
        d = d/1000;
        if(d<60) {
            return d+" secs";
        } else if(d<3600) {
            return d/60 +" mins, "+d%60+" secs";
        } else if(d<86400) {
            return d/3600 +" hrs, " + Math.round((d%3600)/60.0) +" mins";
        } else {
            return d/86400 +" days, " +Math.round((d%86400)/3600.0) +" hrs";
        }
    }



    static class Gap {
        String name;
        long start; //last packet received before the gap
        long end; //first packet received after the gap
        int startSeqCount; //the sequence count of the last packet received before the gap
        int endSeqCount; //the sequence count of the first packet received after the gap
        boolean selected=true;
        
        
        public Gap(String name, long start, long end, int startSeqCount, int endSeqCount) {
            super();
            this.name = name;
            this.start = start;
            this.end = end;
            this.startSeqCount = startSeqCount;
            this.endSeqCount = endSeqCount;
        }
        
        @Override
        public String toString() {
            return "Gap [name=" + name + ", start=" + start + ", end=" + end
                    + ", startSeqCount=" + startSeqCount + ", endSeqCount="
                    + endSeqCount + "]";
        }
    }

    static class MyTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        enum Type {Ccsds, Aces};
        Type type;
        //this is used while loading to remember the last gap for each name (i.e. apid)
        Map<String, Gap> lastGap = new HashMap<String, Gap>();
        List<Gap> list=new ArrayList<Gap>();

        private String[] columnNames = {"Select", "Item", "Last Time",  "First Time",  "Last Seq", "First Seq", "Duration", "Missing packets"};
        Pattern pattern;

        public MyTableModel(Type type) {
            this.type=type;
            if(type==Type.Ccsds) {
                pattern = Pattern.compile("seqFirst: (\\d+) seqLast: (\\d+)");
            } else {
                pattern = Pattern.compile("seqFirst: (\\d+) seqLast: (\\d+) fileFirst: (\\d+) fileLast: (\\d+)");
            }
        }
        @Override
        public int getRowCount() {
            return list.size();
        }
        public void clear() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    lastGap.clear();
                    list.clear();
                    fireTableDataChanged();
                }
            });
        }
        public void populate(final List<ArchiveRecord> arlist) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    for(ArchiveRecord ar:arlist) {
                        Matcher m =pattern.matcher(ar.getInfo());
                        if(!m.matches()) continue;
                        int seqFirst = Integer.valueOf(m.group(1));;
                        int seqLast = Integer.valueOf(m.group(2));
                        String name= ar.getId().getName();
                        Gap gap = lastGap.get(name);
                        if(gap == null ) {
                            gap = new Gap(name, -1, ar.getFirst(), -1, seqFirst);
                            list.add(gap);
                        } else {
                            //we use this instead of ar.getId such that only one version of the String is in memory
                            name = gap.name;
                        }
                        gap.end = ar.getFirst();
                        gap.endSeqCount = seqFirst;

                        gap = new Gap(name, ar.getLast(), -1, seqLast, -1);

                        lastGap.put(name, gap);
                        list.add(gap);
                    }
                    fireTableDataChanged();        
                }
            });
        }



        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int i) {
            return columnNames[i];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Gap gap= list.get(rowIndex);
            switch(columnIndex) {
            case 0:
                return gap.selected;
            case 1:
                return gap.name;
            case 2:
                return gap.start<0? "N/A": TimeEncoding.toString(gap.start);
            case 3:
                return gap.end<0? "N/A": TimeEncoding.toString(gap.end);
            case 4:
                return gap.startSeqCount<0? "N/A": gap.startSeqCount;
            case 5:
                return gap.endSeqCount<0? "N/A": gap.endSeqCount;
            case 6:
                if(gap.start<0 || gap.end<0) return "N/A";
                return getDuration(gap.end-gap.start);
            case 7:
                if(gap.start<0 || gap.end<0) return "N/A";
                if(gap.end-gap.start > 3*3600*1000) return "N/A";
                return getMissing(gap.startSeqCount, gap.endSeqCount);
            }

            return null;
        }

        private String getMissing(int lastSeqCount, int firstSeqCount) {
            if(firstSeqCount<lastSeqCount) {
                if(type==Type.Aces) {
                    return "N/A";
                } else {
                    firstSeqCount+=16384;
                }
            }
            return Integer.toString(firstSeqCount-lastSeqCount-1);
        }

    
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if(columnIndex==0)return true;
            else return false;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if(columnIndex==0) return Boolean.class;
            else return Object.class;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if(columnIndex!=0) return;
            Gap gap = list.get(rowIndex);
            gap.selected = (Boolean)aValue;
        }

        public  List<Gap> getSelected() {
            ArrayList<Gap> al = new ArrayList<Gap>();
            
            //CcsdsGapRequest.Builder requestb = CcsdsGapRequest.newBuilder();
            for(Gap g:list) {
                if(g.selected) al.add(g);
            }
            return al;
            //return requestb.build();
        }
    }

    public static void main(String args[]) throws Exception {
        TimeEncoding.setUp();
        YamcsConnector yconnector = new YamcsConnector();
        CcsdsCompletenessGui cgui=new CcsdsCompletenessGui(yconnector);
        cgui.setVisible(true);
        yconnector.connect(YamcsConnectData.parse("yamcs://localhost/aces-test/"));
    }
}

class ReplayParamDialog extends JDialog implements ActionListener {
    private static final long serialVersionUID = 1L;
    JTextField mergeTimeTextField;
    JTextField maxTimeIntervalTextField;
    JTextArea tagDescrTextArea;
    JCheckBox generateTagCheckBox;
    
    int mergeTime;
    int maxInterval;
    boolean ok = false;
    
    static YamcsConnectDialog dialog;
    ReplayParamDialog( JFrame parent) {
        super(parent, "Gap Request parameters", true);

        JPanel inputPanel, buttonPanel;
        JLabel lab;
        JButton button;

        
        inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints cfull = new GridBagConstraints();
        cfull.weightx = 1; cfull.fill=GridBagConstraints.HORIZONTAL; cfull.gridx=0; cfull.gridy=0;cfull.gridwidth=2;
        JLabel l1 = new JLabel("Please enter the gap request parameters");
        l1.setBorder(new EmptyBorder(2,2,10,2));
        inputPanel.add(l1, cfull);
        
        GridBagConstraints ceast = new GridBagConstraints();
        
        ceast.anchor=GridBagConstraints.EAST;
        GridBagConstraints cwest = new GridBagConstraints();
        cwest.weightx=1; cwest.fill=GridBagConstraints.HORIZONTAL;
        
        
        lab = new JLabel("Merge Time (minutes): ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        ceast.gridy=1;      inputPanel.add(lab,ceast);
        mergeTimeTextField = new JTextField("30");
        mergeTimeTextField.setPreferredSize(new Dimension(80, mergeTimeTextField.getPreferredSize().height));
        mergeTimeTextField.setToolTipText("The gaps with a distance shorter than this will be merged into one");
        cwest.gridy=1;inputPanel.add(mergeTimeTextField,cwest);

        lab = new JLabel("Max Duration (minutes): ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        ceast.gridy=2; inputPanel.add(lab,ceast);
        maxTimeIntervalTextField = new JTextField("720");
        maxTimeIntervalTextField.setToolTipText("Select the amount of data to be requested for the open gaps (without a start or an end) or the very long gaps");
        cwest.gridy=2; inputPanel.add(maxTimeIntervalTextField,cwest);

        lab = new JLabel("Generate Tag: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        ceast.gridy=3; ceast.gridx=0 ;inputPanel.add(lab, ceast);
        generateTagCheckBox = new JCheckBox(); generateTagCheckBox.setSelected(false);
        cwest.gridy=3; cwest.gridx=1; cwest.anchor=GridBagConstraints.WEST; inputPanel.add(generateTagCheckBox, cwest);
        generateTagCheckBox.setActionCommand("GenerateTag");
        generateTagCheckBox.setSelected(false);
        generateTagCheckBox.addActionListener(this);
        generateTagCheckBox.setToolTipText("If selected, a tag will be inserted for each requested data interval, with the description below");
        
        lab = new JLabel("Tag Description: ");
        lab.setHorizontalAlignment(SwingConstants.LEFT);
        ceast.gridy=4 ;inputPanel.add(lab, ceast);
        tagDescrTextArea = new JTextArea();
        cwest.fill = GridBagConstraints.BOTH; cwest.weightx=1;cwest.weighty=1;
        cwest.gridy=4 ;inputPanel.add(tagDescrTextArea, cwest);
        tagDescrTextArea.setEnabled(false);
        

        inputPanel.setPreferredSize(new Dimension(500, 250));

        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        getContentPane().add(inputPanel, BorderLayout.CENTER);

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

        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
    }

    boolean generateTag() {
        return generateTagCheckBox.isSelected();
    }
    
    String getTagDescription() {
        return tagDescrTextArea.getText();
    }
    int getMergeTimeMinutes() {
        return mergeTime;
    }
    
    int getMaxIntervalMinutes() {
        return maxInterval;
    }
    @Override
    public void actionPerformed( ActionEvent e ) {
        if ( e.getActionCommand().equals("ok") ) {
            try {
                mergeTime = Integer.parseInt(mergeTimeTextField.getText());
                maxInterval = Integer.parseInt(maxTimeIntervalTextField.getText());
                ok = true;
                setVisible(false);
            } catch (NumberFormatException x) {
                // do not close the dialogue
            }
        } else if ( e.getActionCommand().equals("cancel") ) {
            ok = false;
            setVisible(false);
        } else if ( e.getActionCommand().equals("GenerateTag") ) {
            tagDescrTextArea.setEnabled(generateTagCheckBox.isSelected());
        }
    }
}
