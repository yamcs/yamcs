package org.yamcs.ui.archivebrowser;

import static org.yamcs.utils.TimeEncoding.INVALID_INSTANT;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import org.yamcs.TimeInterval;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.ui.UiColors;
import org.yamcs.utils.TimeEncoding;

/**
 * Main panel of the ArchiveBrowser
 * @author nm
 *
 */
public class ArchivePanel extends JPanel implements ActionListener, PropertyChangeListener {
    private static final long serialVersionUID = 1L;
    static final SimpleDateFormat format_yyyymmdd    = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
    static final SimpleDateFormat format_yyyymmddT   = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private List<ActionListener> actionListeners=new ArrayList<ActionListener>();

    public ReplayPanel replayPanel;

    volatile boolean lowOnMemoryReported=false;

    public JToolBar buttonToolbar;
    protected PrefsToolbar prefs;

    ProgressMonitor progressMonitor;

    Stack<ZoomSpec> zoomStack;
    long dataStart, dataStop;
    long lastStartTimestamp, lastEndTimestamp;
    int loadCount,recCount;
    boolean replayEnabled;
    
    public Map<String, IndexBox> indexBoxes=new HashMap<String, IndexBox>();
    
    TMScale scale;
    boolean hideResponsePackets=true;
    private boolean showTagBox=true;
    
    JScrollPane tmscrollpane;
    public JButton reloadButton;
    JButton zoomInButton, zoomOutButton, showAllButton, applyButton;
    JLabel totalRangeLabel, packetsLabel, instanceLabel;
    JFormattedTextField selectionStart, selectionStop;
    boolean passiveUpdate = false;
    JFrame parentFrame;
    public TagBox tagBox;
    private Box scrolledPanel;
    
    //used to check for out of memory errors that may happen when receiving too many archive records 
    MemoryPoolMXBean heapMemoryPoolBean = null;
    
    public ArchivePanel(JFrame parentFrame,  boolean replayEnabled)	{
        this.replayEnabled = replayEnabled;
        this.parentFrame=parentFrame;

 
        zoomStack = new Stack<ZoomSpec>();
        dataStart = dataStop = TimeEncoding.INVALID_INSTANT;
        lastStartTimestamp = lastEndTimestamp  = -1;
        // toolbars

        buttonToolbar = new JToolBar("Button Toolbar");
        buttonToolbar.setFloatable(false);


        reloadButton = new JButton("Reload View");
        reloadButton.setActionCommand("reload");
        reloadButton.setEnabled(false);
        buttonToolbar.add(reloadButton);

        buttonToolbar.addSeparator();

        zoomInButton = new JButton("Zoom In");
        zoomInButton.setActionCommand("zoomin");
        zoomInButton.addActionListener(this);
        zoomInButton.setEnabled(false);
        buttonToolbar.add(zoomInButton);

        zoomOutButton = new JButton("Zoom Out");
        zoomOutButton.setActionCommand("zoomout");
        zoomOutButton.addActionListener(this);
        zoomOutButton.setEnabled(false);
        buttonToolbar.add(zoomOutButton);

        showAllButton = new JButton("Show All");
        showAllButton.setActionCommand("showall");
        showAllButton.addActionListener(this);
        showAllButton.setEnabled(false);
        buttonToolbar.add(showAllButton);

        buttonToolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonToolbar.getPreferredSize().height));

        BoxLayout blay=new BoxLayout(this, BoxLayout.Y_AXIS);
        setLayout(blay);
        add(buttonToolbar);
        buttonToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);

        prefs = new PrefsToolbar("Prefs Toolbar");
        prefs.setFloatable(false);
        prefs.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(prefs);

        Box top=getTopBox();
        top.setAlignmentX(0);
        add(top);
        top.setMaximumSize(new Dimension(top.getMaximumSize().width, top.getPreferredSize().height));

        //TM and Tag box panel
        scale = new TMScale();
        scale.setAlignmentX(Component.LEFT_ALIGNMENT);

        tagBox=new TagBox(this);
        tagBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        //tagBox.setBorder(BorderFactory.createEmptyBorder());
        //tagBox.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        

        IndexBox cindexBox = new IndexBox(this, "completeness index");
//        cindexBox.setBackground(UiColors.DISABLED_FAINT_BG);
        cindexBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        cindexBox.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.BORDER_COLOR));
        indexBoxes.put("completeness", cindexBox);
        
        scrolledPanel=Box.createVerticalBox();
        scrolledPanel.add(tagBox);
        scrolledPanel.add(cindexBox);
        
        
        for (String type: new String[] {"tm", "pp", "cmdhist"}) {
            IndexBox histoBox= new IndexBox(this, type+" histogram");
            histoBox.setMergeTime(1000);
//            histoBox.setBackground(UiColors.DISABLED_FAINT_BG);
            histoBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            histoBox.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.BORDER_COLOR));
            scrolledPanel.add(histoBox);
            indexBoxes.put(type, histoBox);
        }
        
        
        
        if (replayEnabled) {
            replayPanel.setTmBox(indexBoxes.get("tm"));
        }
        
        tmscrollpane = new JScrollPane(scrolledPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        
        //put the scale in a box such that it is not resized above its maximum
        Box scalebox=Box.createHorizontalBox();
        scalebox.add(scale);
        tmscrollpane.setColumnHeaderView(scalebox);
        
       // tmscrollpane.getViewport().setBackground(tmViewColor);
        tmscrollpane.setPreferredSize(new Dimension(850, 400));
        tmscrollpane.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        add(tmscrollpane);
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported()) {
                heapMemoryPoolBean = pool;
                heapMemoryPoolBean.setCollectionUsageThreshold((int)Math.floor(heapMemoryPoolBean.getUsage().getMax()*0.95));
            }
        }
    }

    Box getTopBox() {
        // status bars on the northern part of the window

        Box top = Box.createHorizontalBox();

        GridBagLayout lay = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel archiveinfo = new JPanel(lay);
        archiveinfo.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Archive Information"));
        top.add(archiveinfo);
        gbc.insets = new Insets(4, 4, 0, 1);

        JLabel lab = new JLabel("Instance:");
        gbc.fill = GridBagConstraints.NONE; gbc.gridwidth = 1; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        lay.setConstraints(lab, gbc);
        archiveinfo.add(lab);
        instanceLabel = new JLabel();
        instanceLabel.setPreferredSize(new Dimension(100, instanceLabel.getPreferredSize().height));
        gbc.fill = GridBagConstraints.BOTH; gbc.gridwidth = GridBagConstraints.REMAINDER; gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        lay.setConstraints(instanceLabel, gbc);
        archiveinfo.add(instanceLabel);

        lab = new JLabel("Total Range:");
        gbc.fill = GridBagConstraints.NONE; gbc.gridwidth = 1; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        lay.setConstraints(lab, gbc);
        archiveinfo.add(lab);
        totalRangeLabel = new JLabel();
        totalRangeLabel.setPreferredSize(new Dimension(250, totalRangeLabel.getPreferredSize().height));
        gbc.fill = GridBagConstraints.BOTH; gbc.gridwidth = GridBagConstraints.REMAINDER; gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        lay.setConstraints(totalRangeLabel, gbc);
        archiveinfo.add(totalRangeLabel);

        lab = new JLabel("Selection:");
        gbc.fill = GridBagConstraints.NONE; gbc.gridwidth = 1; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        lay.setConstraints(lab, gbc);
        archiveinfo.add(lab);
        InstantFormat iformat=new InstantFormat();
        selectionStart = new JFormattedTextField(iformat);
        selectionStart.setHorizontalAlignment(JTextField.RIGHT);
        selectionStart.setMaximumSize(new Dimension(175, selectionStart.getPreferredSize().height));
        selectionStart.setMinimumSize(selectionStart.getMaximumSize());
        selectionStart.setPreferredSize(selectionStart.getMaximumSize());
        lay.setConstraints(selectionStart, gbc);
        archiveinfo.add(selectionStart);
        selectionStart.addPropertyChangeListener("value", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                updateSelection();
            }
        });

        lab = new JLabel("-");
        lay.setConstraints(lab, gbc);
        archiveinfo.add(lab);

        selectionStop = new JFormattedTextField(iformat);
        selectionStop.setHorizontalAlignment(JTextField.RIGHT);
        selectionStop.setMaximumSize(selectionStop.getPreferredSize());
        selectionStop.setMaximumSize(new Dimension(175, selectionStop.getPreferredSize().height));
        selectionStop.setMinimumSize(selectionStop.getMaximumSize());
        selectionStop.setPreferredSize(selectionStop.getMaximumSize());
        gbc.gridwidth = GridBagConstraints.REMAINDER; gbc.anchor = GridBagConstraints.WEST;
        lay.setConstraints(selectionStop, gbc);
        archiveinfo.add(selectionStop);
        selectionStop.addPropertyChangeListener("value", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                updateSelection();
            }
        });

        lab = new JLabel("Packets:");
        gbc.fill = GridBagConstraints.NONE; gbc.gridwidth = 1; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.NORTHEAST;
        gbc.weighty = 1.0;
        lay.setConstraints(lab, gbc);
        archiveinfo.add(lab);
        packetsLabel = new JLabel();
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.gridwidth = GridBagConstraints.REMAINDER; gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        lay.setConstraints(packetsLabel, gbc);
        archiveinfo.add(packetsLabel);
        //
        // transport control panel (only enabled when a HRDP data channel is selected)
        //
        if (replayEnabled) {
            replayPanel = new ReplayPanel(new GridBagLayout());
            replayPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Replay Control"));
            replayPanel.setToolTipText("Right-click between the start/stop locators to reposition the replay.");
            top.add(replayPanel);

            

        }
        return top;
    }
    /*
    public void setVisible(boolean vis)	{
        super.setVisible(vis);
        if (vis) {
            if (tmData.isEmpty() && reloadButton.isEnabled()) {
                requestData();
            } else {
                refreshTmDisplay();
            }
        }
    }
     */
    
    public void updateSelection() {
        if(!passiveUpdate) {
            Long sstart=(Long) selectionStart.getValue();
            Long sstop=(Long) selectionStop.getValue();
            if ((sstart!=null) && (sstop!=null)) {
                long start =sstart ;
                long stop =sstop; 
                if ((start != INVALID_INSTANT) && (stop != INVALID_INSTANT)) {
                    for(IndexBox histoBox:indexBoxes.values()) {
                        histoBox.updateSelection(start, stop);
                    }
                    emitActionEvent("histo_selection_finished");
                }
            }
        }
    }

    //called from the IndexBox when a selection finishes
    public void selectionFinished(IndexBox ibox) {
        for(Map.Entry<String, IndexBox>e: indexBoxes.entrySet()) {
            IndexBox ib=e.getValue();
            String name=e.getKey();
            if(ibox==ib) emitActionEvent(name+"_selection_finished");
        }
    }
    
    
    public void addActionListener(ActionListener al) {
        actionListeners.add(al);
    }

    public void setBusyPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    public void setNormalPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    public void setMoveLeftPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    }

    public void setMoveRightPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    }

    public static ImageIcon getIcon(String imagename) {
        return new ImageIcon(ArchivePanel.class.getResource("/org/yamcs/images/" + imagename));
    }
    
    /**
     * Called after the mouse dragging selection is updated on the boxes to update the selectionStart/Stop fields
     * We use the passiveUpdate to avoid a ping pong effect
     * 
     * @param ibox
     */
    void updateSelectionFields(IndexBox ibox) {
        passiveUpdate=true;
        for(IndexBox ib: indexBoxes.values()) {
            if(ibox==ib) {
                Selection s = ibox.getSelection();
                if (s != null) {
                    selectionStart.setValue(s.getStartInstant());
                    selectionStop.setValue(s.getStopInstant());
                } else {
                    selectionStart.setValue(TimeEncoding.INVALID_INSTANT);
                    selectionStop.setValue(TimeEncoding.INVALID_INSTANT);
                }
            } else {
                ib.resetSelection();
            }
        }
        passiveUpdate=false;
    }

    void updateStatusBar() {
        passiveUpdate = true;

        if (zoomStack.empty()) {
            if (loadCount == 0) {
                totalRangeLabel.setText("(no range displayed)");
            } else {
                totalRangeLabel.setText("Loading Data ... (" + loadCount + ")");
                totalRangeLabel.repaint();
            }
        } else {
            final ZoomSpec zoom = zoomStack.peek();
            totalRangeLabel.setText(TimeEncoding.toString(zoom.startInstant) + " - "
                    + TimeEncoding.toString(zoom.stopInstant));
        }
        StringBuilder sb = new StringBuilder();
        for(IndexBox ib: indexBoxes.values()) {
            sb.append(ib.getPacketsStatus());
        }
        packetsLabel.setText(sb.toString());
   //     updateSelectionFields();

        passiveUpdate = false;
    }

    static protected void debugLog(String s) {
        System.out.println(s);
    }

    static protected void debugLogComponent(String name, JComponent c)  {
        Insets in = c.getInsets();
        debugLog("component " + name + ": "
                + "min(" + c.getMinimumSize().width + "," + c.getMinimumSize().height + ") "
                + "pref(" + c.getPreferredSize().width + "," + c.getPreferredSize().height + ") "
                + "max(" + c.getMaximumSize().width + "," + c.getMaximumSize().height + ") "
                + "size(" + c.getSize().width + "," + c.getSize().height + ") "
                + "insets(" + in.top + "," + in.left + "," +in.bottom + "," +in.right + ")");
    }

    void emitActionEvent(String cmd) {
        ActionEvent ae=new ActionEvent(this, ActionEvent.ACTION_PERFORMED, cmd);
        for(ActionListener al:actionListeners) {
            al.actionPerformed(ae);
        }
    }

    void emitActionEvent(ActionEvent ae) {
        for(ActionListener al:actionListeners) {
            al.actionPerformed(ae);
        }
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String cmd = ae.getActionCommand();
        if (cmd.equals("prefs")) {
            //prefs.setVisible(true);

        } else if (cmd.equals("showall")) {
            while (zoomStack.size() > 1) {
                zoomStack.pop();
            }
            for(IndexBox ib:indexBoxes.values()) {
                ib.resetSelection();
            }
            refreshTmDisplay();
            setViewLocationFromZoomstack();
        } else if (cmd.equals("zoomout")) {

            if (zoomStack.size() > 1) {
                zoomStack.pop();
                refreshTmDisplay();

                // place the view where it was
                setViewLocationFromZoomstack();
            }

        } else if (cmd.equals("zoomin")) {
            for(IndexBox ib:indexBoxes.values()) {
                Selection s = ib.getSelection();
                if (s != null) {
                    zoomIn(s);
                    break;
                }
            }
        }

    }


    private void setViewLocationFromZoomstack()	{
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final ZoomSpec currentZoom = zoomStack.peek();
                final JViewport vp = tmscrollpane.getViewport();
                int x = (int)((currentZoom.viewLocation - currentZoom.startInstant) / currentZoom.pixelRatio);
                vp.setViewPosition(new Point(x, vp.getViewPosition().y));
                //debugLog("zoom out, view width " + vp.getView().getSize().width + " location " + x + " = " + currentZoom.viewLocation);
            }
        });
    }

    private void zoomIn(Selection sel)	{
        ZoomSpec zoom = zoomStack.peek();
        final JViewport vp = tmscrollpane.getViewport();

        // save current location in current zoom spec
        zoom.viewLocation = zoom.convertPixelToInstant(vp.getViewPosition().x);

        // create new zoom spec and add it to the stack
        long startInstant = sel.getStartInstant();
        long stopInstant = sel.getStopInstant();
        long range = stopInstant - startInstant;
        
        long reqStart=getRequestedDataStart();
        long reqStop=getRequestedDataStop();
        long zstart=startInstant - range * 2;
        if(reqStart!=TimeEncoding.INVALID_INSTANT) {
            zstart=Math.max(zstart, reqStart);
        }
        long zstop=stopInstant + range * 2;
        if(reqStop!=TimeEncoding.INVALID_INSTANT) {
            zstop=Math.min(zstop, reqStop);
        }
        zoom = new ZoomSpec(zstart, zstop, vp.getExtentSize().width, range);
        zoom.viewLocation = sel.getStartInstant();
        zoomStack.push(zoom);

        for(IndexBox ib:indexBoxes.values()) {
            ib.resetSelection();
        }
        refreshTmDisplay();

        // set the view to the previously selected region
        setViewLocationFromZoomstack();
    }

   

    void refreshTmDisplay()	{
        setBusyPointer();

        int panelw = tmscrollpane.getViewport().getExtentSize().width;
        
        if ( !zoomStack.empty() ) {
            ZoomSpec zoom = zoomStack.peek();
            if (panelw > zoom.getPixels()) {
                zoom.setPixels(panelw);
            }
            panelw = zoom.getPixels();
            scale.setToZoom(zoom);
            if(showTagBox) {
                tagBox.setToZoom(zoom);
            } else {
                tagBox.removeAll();
            }
            
            for(IndexBox ib:indexBoxes.values()) {
                ib.setToZoom(zoom);
            }
        }
        scale.setMaximumSize(new Dimension(panelw, scale.getPreferredSize().height));
        scale.setMinimumSize(scale.getMaximumSize());
        scale.setPreferredSize(scale.getMaximumSize());
        scale.setSize(scale.getMaximumSize());

        //histoBox.revalidate();
        //histoBox.repaint();
        //cindexBox.revalidate();

        updateStatusBar();
        if (progressMonitor != null) {
            progressMonitor.setProgress(80);
            progressMonitor.setNote("Repainting display");
        }

        // reset mouse pointer after everything is painted
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setNormalPointer();
                //debugLog("refreshTmDisplay() mark 99");
                if (progressMonitor != null) {
                    progressMonitor.close();
                    progressMonitor = null;
                }
            }
        });
    }


    void seekReplay(long newPosition) {
        replayPanel.seekReplay(newPosition);
    }

    void playOrStopPressed() {
        // to be reimplemented by subclass ArchiveReplay in YamcsMonitor
    }

    public  synchronized void receiveArchiveRecords(IndexResult ir) {
        if((heapMemoryPoolBean!=null) && (heapMemoryPoolBean.isCollectionUsageThresholdExceeded())) {
            if(!lowOnMemoryReported) {
                lowOnMemoryReported=true;
                receiveArchiveRecordsError("The memory is almost exhausted, ignoring received Archive Records. Consider increasing the maximum heap size -Xmx parameter");
            }
            return;
        }
        
        long start, stop;
        if("completeness".equals(ir.getType())) {
            indexBoxes.get("completeness").receiveArchiveRecords(ir.getRecordsList());
        } else if("histogram".equals(ir.getType())) {
            String tableName=ir.getTableName();
            if(indexBoxes.containsKey(tableName)) {
                indexBoxes.get(tableName).receiveArchiveRecords(ir.getRecordsList());
            } else {
                debugLog("Received histogram records for unknown table '"+tableName+"': "+ir);
            }
        } else {
            debugLog("Received archive records of type "+ir.getType()+" don't know what to do with them");
            return;
        }
        //progressMonitor.setProgress(30);
        //progressMonitor.setNote("Receiving data");
        
        for (ArchiveRecord r:ir.getRecordsList()) {
            //debugLog(r.packet+"\t"+r.num+"\t"+new Date(r.first)+"\t"+new Date(r.last));
            start = r.getFirst();
            stop = r.getLast();

            if ((dataStart == INVALID_INSTANT) || (start<dataStart)) dataStart = start;
            if ((dataStop == INVALID_INSTANT) || (stop>dataStop)) dataStop = stop;

            recCount++;
            ++loadCount;
            updateStatusBar();
        }
    }
    
    public synchronized void dataLoadFinished() {
        
        for(IndexBox ib:indexBoxes.values()) {
            ib.dataLoadFinished();
        }
   //     debugLog("loaded " + recCount + " records, " + histoBox.groups.size() + 
     //           " PLs, " + histoBox.allPackets.size() + " packets, start \"" + dataStart + "\" stop \"" + dataStop + "\"");
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                reloadButton.setEnabled(true);
            }
        });
        loadCount = 0;

        if ((dataStart == INVALID_INSTANT) || (dataStop == INVALID_INSTANT)) {
            setNormalPointer();
        } else {
            if (zoomStack.isEmpty() ||
                    ((prefs.getStartTimestamp() != lastStartTimestamp) ||
                            (prefs.getEndTimestamp() != lastEndTimestamp)
                    )) {

                int w = tmscrollpane.getViewport().getExtentSize().width;
                zoomStack.clear();
                long reqStart=getRequestedDataStart();
                long zstart=dataStart;
                if(reqStart!=TimeEncoding.INVALID_INSTANT) {
                    zstart=Math.min(reqStart, zstart);
                }
                
                long reqStop=getRequestedDataStop();
                long zstop=dataStop;
              
                if(reqStop!=TimeEncoding.INVALID_INSTANT) {
                    zstop=Math.max(reqStop, zstop);
                }
                long range=zstop - zstart;
                zstart-=range/100;
                zstop+=range/100;
                zoomStack.push(new ZoomSpec(zstart, zstop, w, zstop - zstart));
            }

            lastStartTimestamp = prefs.getStartTimestamp();
            lastEndTimestamp = prefs.getEndTimestamp();


            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    //debugLog("receiveHrdpRecords() mark 1");
                    zoomInButton.setEnabled(true);
                    zoomOutButton.setEnabled(true);
                    showAllButton.setEnabled(true);
                    if (applyButton != null) applyButton.setEnabled(true);
                }
            });
        }
        refreshTmDisplay();
       
        reloadButton.setEnabled(true);
        setNormalPointer();
        prefs.savePreferences();
    }

    public void receiveArchiveRecordsError(final String errorMessage) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(ArchivePanel.this, "Error when receiving archive records: "+errorMessage, "error receiving archive records", JOptionPane.ERROR_MESSAGE);
                reloadButton.setEnabled(true);
                setNormalPointer();
            }
        });
    }
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        debugLog(e.getPropertyName()+"/"+e.getOldValue()+"/"+e.getNewValue());
    }

    public void startReloading() {
        recCount=0;
        
        setBusyPointer();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                reloadButton.setEnabled(false);
                //debugLog("requestData() mark 5 "+new Date());
                zoomInButton.setEnabled(false);
                zoomOutButton.setEnabled(false);
                showAllButton.setEnabled(false);
                if (applyButton != null) applyButton.setEnabled(false);
            }
        });
        final String inst = prefs.getInstance();
        instanceLabel.setText(inst);
        for(IndexBox ib:indexBoxes.values()) {
            ib.startReloading();
        }
        tagBox.tags.clear();
        
        if(lowOnMemoryReported) {
            System.gc();
            lowOnMemoryReported=false;
        }
        dataStart = dataStop = INVALID_INSTANT;
    }


    public List<String> getSelectedPackets(String type) {
       return indexBoxes.get(type).getSelectedPackets();
    }

    public Selection getSelection() {
        for(IndexBox ib:indexBoxes.values()) {
            Selection s=ib.getSelection();
            if(s!=null) return s;
        }
        return null;
     }

    /**caled from the tagBox when a tag is selected. Update tmBox selection to this*/
    public void selectedTag(ArchiveTag tag) {
        passiveUpdate=true;
        if(tag.hasStart())  {
            selectionStart.setValue(tag.getStart());
        } else {
            selectionStart.setValue(dataStart);
        }
        passiveUpdate=false;
        
        if(tag.hasStop()) {
            selectionStop.setValue(tag.getStop());
        } else {
            selectionStop.setValue(dataStop);
        }
    }

    public TimeInterval getRequestedDataInterval() {
        return prefs.getInterval();
    }
    public long getRequestedDataStop() {
        return prefs.getEndTimestamp();
    }

    public long getRequestedDataStart() {
        return prefs.getStartTimestamp();
    }

    public String getInstance() {
        return instanceLabel.getText();
    }

    public void connected()	{
        reloadButton.setEnabled(true);
    }

    public void disconnected() {
        reloadButton.setEnabled(false);
    }

    
    /**
     * Called when the connection to yamcs is (re)estabilished, (re)populates the list of hrdp instances
     * @param archiveInstances
     */
    public void setInstances(final List<String> archiveInstances) {
        prefs.setInstances(archiveInstances);
    }

    class SelectionImpl implements Selection {
        long start, stop;

        SelectionImpl(int x1, int x2) {
            set(x1, x2);
        }

        SelectionImpl(long start1, long stop1) {
            set(start1, stop1);
        }

        @Override
        public long getStartInstant() {
            return start;
        }

        @Override
        public long getStopInstant() {
            return stop;
        }

        int getStartX() {
            final ZoomSpec zoom = zoomStack.peek();
            return zoom.convertInstantToPixel(start);
        }

        int getStopX() {
            final ZoomSpec zoom = zoomStack.peek();
            return zoom.convertInstantToPixel(stop);
        }

        public void set(long start1, long stop1) {
            if (start1>stop1) {
                start = stop1;
                stop = start1;
            } else {
                start = start1;
                stop = stop1;
            }

        }

        public void set(int x1, int x2) {
            if(x1>x2) {
                int xt=x1;
                x1=x2;
                x2=xt;
            }

            final ZoomSpec zoom = zoomStack.peek();
            start = zoom.convertPixelToInstant(x1);
            stop = zoom.convertPixelToInstant(x2);
        }
    }


    static class ZoomSpec {
        long startInstant, stopInstant; //the start and stop of all the visible data (i.e. scrolling to the left and right)
        long viewLocation;
        long viewTimeWindow; //the total time visible at one time(i.e. if the scroll is not used)
        double pixelRatio; // ms per pixel

        ZoomSpec(long start, long stop, int pixelwidth, long viewTimeWindow) {
            this.startInstant = start;
            this.stopInstant = stop;
            this.viewTimeWindow = viewTimeWindow;
            viewLocation = start;
            setPixels(pixelwidth);
        }

        void setPixels(int pixelwidth) {
            pixelRatio = (double)(viewTimeWindow) / pixelwidth; // ms per pixel
        }

        int getPixels() {
            return convertInstantToPixel(stopInstant);
        }

        int convertInstantToPixel(long ms) {
            return (int)Math.round((ms - startInstant) / pixelRatio);
        }

        long convertPixelToInstant(int x) {
            return (long)(x * pixelRatio) + startInstant;
        }

        public IndexChunkSpec convertPixelToChunk(int x) {
            return new IndexChunkSpec((long)(x * pixelRatio) + startInstant, (long)((x+1) * pixelRatio) + startInstant-1, 0, null);
        }
        
        @Override
        public String toString() {
            return "start: "+startInstant+" stop:"+stopInstant+" viewTimeWindow: "+viewTimeWindow+" pixelRatio: "+pixelRatio;
        }
    }

    static class IndexChunkSpec implements Comparable<IndexChunkSpec>{
        long startInstant, stopInstant;
        int tmcount;
        String info;
        
        IndexChunkSpec(long start, long stop, int tmcount, String info) {
            this.startInstant = start;
            this.stopInstant = stop;
            this.tmcount = tmcount;
            this.info=info;
        }

        /**
         * 
         * @return frequency in Hz
         */
        float getFrequency() {
            float freq = (float)(tmcount-1) / ((stopInstant - startInstant) / 1000.0f);
            freq = Math.round(freq * 1000) / 1000.0f;
            return freq;
        }
        @Override
        public int compareTo(IndexChunkSpec a) {
            return Long.signum(startInstant-a.startInstant);
        }
        
        //merge two records if close enough to eachother
        public boolean merge(IndexChunkSpec t, long mergeTime) {
            boolean merge=false;
            if(tmcount==1) {
                if(t.startInstant-stopInstant<mergeTime) merge=true; 
            } else {
                float dist=(stopInstant-startInstant)/((float)(tmcount-1));
                if(t.startInstant-stopInstant<dist+mergeTime) merge=true;
            }
            if(merge) {
                stopInstant=t.stopInstant;
                tmcount+=t.tmcount;
            }
            return merge;
        }
        
        @Override
        public String toString() {
            return "start: "+startInstant+" stop: "+stopInstant+" count:"+tmcount;
        }
    }

    public void resetSelection() {
        emitActionEvent("selection_reset");
    }

    public void enableCompletenessIndex(boolean enabled) {
        if(!enabled) {
            scrolledPanel.remove(indexBoxes.get("completeness"));
        } else {
            scrolledPanel.add(indexBoxes.get("completeness"), 1);
        }
        validate();
    }
}
