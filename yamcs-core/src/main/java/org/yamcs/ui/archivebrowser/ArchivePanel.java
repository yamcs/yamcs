package org.yamcs.ui.archivebrowser;

import org.yamcs.TimeInterval;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.ui.UiColors;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.yamcs.utils.TimeEncoding.INVALID_INSTANT;

/**
 * Main panel of the ArchiveBrowser
 * @author nm
 *
 */
public class ArchivePanel extends JPanel implements PropertyChangeListener {
    private static final long serialVersionUID = 1L;
    static final SimpleDateFormat format_yyyymmdd    = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
    static final SimpleDateFormat format_yyyymmddT   = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    ProgressMonitor progressMonitor;
    
    JFrame parentFrame;
    JLabel totalRangeLabel;
    JLabel statusInfoLabel;
    JLabel instanceLabel;
    private List<DataViewer> dataViewers = new ArrayList<DataViewer>();

    public JToolBar archiveToolbar;
    protected PrefsToolbar prefs;
    
    JPanel dataViewerPanel;
    private DataViewer activeDataViewer; // currently shown in cardlayout
    public ReplayPanel replayPanel;
    
    int loadCount,recCount;
    boolean passiveUpdate = false;
    
    long dataStart = TimeEncoding.INVALID_INSTANT;
    long dataStop = TimeEncoding.INVALID_INSTANT;
    
    volatile boolean lowOnMemoryReported=false;
    
    //used to check for out of memory errors that may happen when receiving too many archive records 
    MemoryPoolMXBean heapMemoryPoolBean = null;
        
    public ArchivePanel(JFrame parentFrame,  boolean replayEnabled)	{
        super(new BorderLayout());
        this.parentFrame=parentFrame;
        
        /*
         * Upper fixed content
         */
        Box fixedTop = Box.createVerticalBox();
        fixedTop.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, UiColors.BORDER_COLOR));
        prefs = new PrefsToolbar();
        prefs.setAlignmentX(Component.LEFT_ALIGNMENT);
        fixedTop.add(prefs);
        
        archiveToolbar = new JToolBar();
        archiveToolbar.setFloatable(false);
        archiveToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);

        fixedTop.add(archiveToolbar);
        
        //
        // transport control panel (only enabled when a HRDP data channel is selected)
        //
        if (replayEnabled) {
            replayPanel = new ReplayPanel(new GridBagLayout());
            replayPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Replay Control"));
            replayPanel.setToolTipText("Right-click between the start/stop locators to reposition the replay.");
            fixedTop.add(replayPanel);
        }

        DataViewer allViewer = new DataViewer(this, replayEnabled);
        allViewer.addIndex("completeness", "completeness index");
        allViewer.addIndex("tm", "tm histogram", 1000);
        allViewer.addIndex("pp", "pp histogram", 1000);
        allViewer.addIndex("cmdhist", "cmdhist histogram", 1000);
        dataViewers.add(allViewer);

        DataViewer tmViewer = new DataViewer(this, replayEnabled);
        tmViewer.addIndex("completeness", "completeness index");
        tmViewer.addIndex("tm", "tm histogram", 1000);
        tmViewer.addVerticalGlue();
        dataViewers.add(tmViewer);

        DataViewer ppViewer = new DataViewer(this, replayEnabled);
        ppViewer.addIndex("pp", "pp histogram", 1000);
        ppViewer.addVerticalGlue();
        dataViewers.add(ppViewer);

        DataViewer cmdViewer = new DataViewer(this, replayEnabled);
        cmdViewer.addIndex("cmdhist", "cmdhist histogram", 1000);
        cmdViewer.addVerticalGlue();
        dataViewers.add(cmdViewer);
        
        add(fixedTop, BorderLayout.NORTH);
        add(createStatusBar(), BorderLayout.SOUTH);

        SideNavigator sideNavigator = new SideNavigator(this);
        add(sideNavigator, BorderLayout.WEST);

        dataViewerPanel = new JPanel(new CardLayout());
        add(dataViewerPanel, BorderLayout.CENTER);

        sideNavigator.addItem("Archive", 0, allViewer, true);
        sideNavigator.addItem("Telemetry", 1, tmViewer);
        sideNavigator.addItem("Processed Parameters", 1, ppViewer);
        sideNavigator.addItem("Command History", 1, cmdViewer);
                
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported()) {
                heapMemoryPoolBean = pool;
                heapMemoryPoolBean.setCollectionUsageThreshold((int)Math.floor(heapMemoryPoolBean.getUsage().getMax()*0.95));
            }
        }

        // Catch mouse events globally, to deal more easily with events on child components
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) { // EDT
                DataView.IndexPanel indexPanel = (DataView.IndexPanel) getActiveDataViewer().dataView.indexPanel;
                if (SwingUtilities.isDescendingFrom((Component)event.getSource(), indexPanel)) {
                    MouseEvent me = SwingUtilities.convertMouseEvent((Component)event.getSource(), (MouseEvent) event, indexPanel);
                    if(event.getID()==MouseEvent.MOUSE_DRAGGED) {
                        indexPanel.doMouseDragged(me);
                    } else if(event.getID()==MouseEvent.MOUSE_PRESSED) {
                        indexPanel.doMousePressed(me);
                    } else if(event.getID()==MouseEvent.MOUSE_RELEASED) {
                        indexPanel.doMouseReleased(me);
                    }
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK + AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    private DataViewer getActiveDataViewer() {
        return activeDataViewer;
    }

    public void openItem(String name) {
        CardLayout cl = (CardLayout) dataViewerPanel.getLayout();
        cl.show(dataViewerPanel, name);
        for(Component component : dataViewerPanel.getComponents()) { // Why no API on cardlayout for this...
            if (component.isVisible()) {
                activeDataViewer = (DataViewer) component;
                break;
            }
        }
    }
    
    public void updateScaleFonts() {
        for (DataViewer dataViewer: dataViewers) {
            dataViewer.dataView.scale.updateFontSize();
        }
    }
    
    private Box createStatusBar() {
        Box bar = Box.createHorizontalBox();
        
        Border outsideBorder = BorderFactory.createMatteBorder(2, 0, 0, 0, UiColors.BORDER_COLOR);
        Border insideBorder = BorderFactory.createEmptyBorder(5, 10, 5, 10);
        bar.setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));
        
        bar.add(Box.createHorizontalGlue());
        
        bar.add(createLabelForStatusBar(" Instance: ")); // Front space serves as left padding
        instanceLabel = createLabelForStatusBar(null);
        bar.add(instanceLabel);
        
        bar.add(createLabelForStatusBar(", Loaded: "));
        totalRangeLabel = createLabelForStatusBar(null);
        bar.add(totalRangeLabel);

        bar.add(Box.createHorizontalGlue());
        
        statusInfoLabel = createLabelForStatusBar(null);
        bar.add(statusInfoLabel);
        return bar;
    }
    
    private JLabel createLabelForStatusBar(String text) {
        JLabel lbl = new JLabel();
        if(text != null) lbl.setText(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, lbl.getFont().getSize2D()-2));
        return lbl;
    }
    
    public void addActionListener(ActionListener al) {
        for(DataViewer dataViewer : dataViewers) {
            dataViewer.dataView.addActionListener(al);
        }
    }
    
    void updateStatusBar() {
        passiveUpdate = true;

        if (loadCount == 0) {
            statusInfoLabel.setText("(no data loaded) ");
        } else {
            statusInfoLabel.setText("Loading Data ... " + loadCount + " ");
            statusInfoLabel.repaint();
        }
        
        totalRangeLabel.setText(TimeEncoding.toString(dataStart) + " - "
                + TimeEncoding.toString(dataStop));
        totalRangeLabel.repaint();
        
   //     updateSelectionFields();

        passiveUpdate = false;
    }
    
    public void startReloading() {
        recCount=0;
        
        setBusyPointer();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                prefs.reloadButton.setEnabled(false);
                //debugLog("requestData() mark 5 "+new Date());
                for(DataViewer dataViewer: dataViewers) {
                    dataViewer.zoomInButton.setEnabled(false);
                    dataViewer.zoomOutButton.setEnabled(false);
                    dataViewer.showAllButton.setEnabled(false);
                    if (dataViewer.applyButton != null) {
                        dataViewer.applyButton.setEnabled(false);
                    }
                }
            }
        });
        final String inst = prefs.getInstance();
        instanceLabel.setText(inst);
        for(DataViewer dataViewer: dataViewers) {
            for(IndexBox ib:dataViewer.dataView.indexBoxes.values()) {
                ib.startReloading();
            }
            dataViewer.dataView.tagBox.tags.clear();
        }
        
        if(lowOnMemoryReported) {
            System.gc();
            lowOnMemoryReported=false;
        }
        dataStart = dataStop = INVALID_INSTANT;
    }

    public static ImageIcon getIcon(String imagename) {
        return new ImageIcon(ArchivePanel.class.getResource("/org/yamcs/images/" + imagename));
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

    void playOrStopPressed() {
        // to be reimplemented by subclass ArchiveReplay in YamcsMonitor
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        debugLog(e.getPropertyName()+"/"+e.getOldValue()+"/"+e.getNewValue());
    }

    public String getInstance() {
        return instanceLabel.getText();
    }
    
    /**
     * Called when the connection to yamcs is (re)established, (re)populates the list of hrdp instances
     * @param archiveInstances
     */
    public void setInstances(final List<String> archiveInstances) {
        prefs.setInstances(archiveInstances);
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

    public void enableCompletenessIndex(boolean enabled) {
        for(DataViewer dataViewer: dataViewers) {
            dataViewer.dataView.enableCompletenessIndex(enabled);
        }
        validate();
    }

    public void connected() {
        prefs.reloadButton.setEnabled(true);
    }

    public void disconnected() {
        prefs.reloadButton.setEnabled(false);
    }
    
    public void setBusyPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    public void setNormalPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
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

    public synchronized void receiveArchiveRecords(IndexResult ir) {
        if((heapMemoryPoolBean!=null) && (heapMemoryPoolBean.isCollectionUsageThresholdExceeded())) {
            if(!lowOnMemoryReported) {
                lowOnMemoryReported=true;
                receiveArchiveRecordsError("The memory is almost exhausted, ignoring received Archive Records. Consider increasing the maximum heap size -Xmx parameter");
            }
            return;
        }
        
        long start, stop;
        if("completeness".equals(ir.getType())) {
            for(DataViewer dataViewer: dataViewers) {
                if(dataViewer.dataView.indexBoxes.containsKey("completeness")) {
                    dataViewer.dataView.indexBoxes.get("completeness").receiveArchiveRecords(ir.getRecordsList());
                }
            }
        } else if("histogram".equals(ir.getType())) {
            String tableName=ir.getTableName();
            for(DataViewer dataViewer: dataViewers) {
                if(dataViewer.dataView.indexBoxes.containsKey(tableName)) {
                    dataViewer.dataView.indexBoxes.get(tableName).receiveArchiveRecords(ir.getRecordsList());
                }
            }
            /* TODO TODO else {
                debugLog("Received histogram records for unknown table '"+tableName+"': "+ir);
            }*/
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

    public void receiveArchiveRecordsError(final String errorMessage) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(ArchivePanel.this, "Error when receiving archive records: "+errorMessage, "error receiving archive records", JOptionPane.ERROR_MESSAGE);
                prefs.reloadButton.setEnabled(true);
                setNormalPointer();
            }
        });
    }
    
    void seekReplay(long newPosition) {
        replayPanel.seekReplay(newPosition);
    }

    public synchronized void dataLoadFinished() {
        for(DataViewer dataViewer: dataViewers) {
            for(IndexBox ib:dataViewer.dataView.indexBoxes.values()) {
                ib.dataLoadFinished();
            }
        }
   //     debugLog("loaded " + recCount + " records, " + histoBox.groups.size() + 
     //           " PLs, " + histoBox.allPackets.size() + " packets, start \"" + dataStart + "\" stop \"" + dataStop + "\"");
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statusInfoLabel.setText("");
                prefs.reloadButton.setEnabled(true);
            }
        });
        loadCount = 0;
        
        if ((dataStart == INVALID_INSTANT) || (dataStop == INVALID_INSTANT)) {
            setNormalPointer();
        } else {
            for(DataViewer dataViewer: dataViewers) {
                dataViewer.dataView.dataLoadFinished();
            }
            prefs.savePreferences();
        }
        
        prefs.reloadButton.setEnabled(true);
        setNormalPointer();
    }

    public void tagAdded(ArchiveTag ntag) {
        for(DataViewer dataViewer: dataViewers) {
            dataViewer.dataView.tagBox.addTag(ntag);
        }
    }
    
    public void tagRemoved(ArchiveTag ntag) {
        for(DataViewer dataViewer: dataViewers) {
            dataViewer.dataView.tagBox.removeTag(ntag);
        }
    }

    public void tagChanged(ArchiveTag oldTag, ArchiveTag newTag) {
        for(DataViewer dataViewer: dataViewers) {
            dataViewer.dataView.tagBox.updateTag(oldTag, newTag);
        }
    }

    public void tagsAdded(List<ArchiveTag> tagList) {
        for(DataViewer dataViewer: dataViewers) {
            dataViewer.dataView.tagBox.addTags(tagList);
        }
    }

    public void createNewTag(Selection sel) {
        for(DataViewer dataViewer: dataViewers) {
            dataViewer.dataView.tagBox.createNewTag(sel.getStartInstant(), sel.getStopInstant());
        }
    }

    public Selection getSelection() {
        for(DataViewer dataViewer: dataViewers) {
            if(dataViewer.dataView.getSelection()!=null) {
                return dataViewer.dataView.getSelection();
            }
        }
        return null;
    }

    public List<String> getSelectedPackets(String tableName) {
        for(DataViewer dataViewer: dataViewers) {
            if(dataViewer.dataView.indexBoxes.containsKey(tableName)) {
                return dataViewer.dataView.getSelectedPackets("tm");
            }
        }
        return Collections.emptyList();
    }
}
