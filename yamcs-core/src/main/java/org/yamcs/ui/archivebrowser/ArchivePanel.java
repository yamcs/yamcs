package org.yamcs.ui.archivebrowser;

import static org.yamcs.utils.TimeEncoding.INVALID_INSTANT;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import org.yamcs.TimeInterval;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.utils.TimeEncoding;

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
    JLabel totalRangeLabel, packetsLabel, instanceLabel;
    private List<DataView> dataViews = new ArrayList<DataView>();
    
    private List<ActionListener> actionListeners=new ArrayList<ActionListener>();
    
    public JToolBar archiveToolbar;
    protected PrefsToolbar prefs;
    
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
        
        prefs = new PrefsToolbar();
        prefs.setAlignmentX(Component.LEFT_ALIGNMENT);
        fixedTop.add(prefs);
        
        archiveToolbar = new JToolBar();
        archiveToolbar.setFloatable(false);
        archiveToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);

        fixedTop.add(archiveToolbar);
        
        packetsLabel = new JLabel();
        fixedTop.add(packetsLabel);
        
        //
        // transport control panel (only enabled when a HRDP data channel is selected)
        //
        if (replayEnabled) {
            replayPanel = new ReplayPanel(new GridBagLayout());
            replayPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Replay Control"));
            replayPanel.setToolTipText("Right-click between the start/stop locators to reposition the replay.");
            fixedTop.add(replayPanel);
        }
        
        add(fixedTop, BorderLayout.NORTH);
        
        /*
         * Status bar
         */
        add(createStatusBar(), BorderLayout.SOUTH);
        
        /*
         * VIEWS (TODO make configurable)
         */
        JTabbedPane tabPane = new JTabbedPane();
        
        DataView tmView = new DataView(this, replayEnabled);
        tmView.addIndex("completeness", "completeness index");
        tmView.addIndex("tm", "tm histogram", 1000);
        dataViews.add(tmView);
        tabPane.addTab("Telemetry", tmView);
        
        DataView ppView = new DataView(this, replayEnabled);
        ppView.addIndex("pp", "pp histogram", 1000);
        dataViews.add(ppView);
        tabPane.addTab("Processed Parameters", ppView);
        
        DataView cmdView = new DataView(this, replayEnabled);
        cmdView.addIndex("cmdhist", "cmdhist histogram", 1000);
        dataViews.add(cmdView);
        tabPane.addTab("Command History", cmdView);
        
        add(tabPane, BorderLayout.CENTER);
        
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported()) {
                heapMemoryPoolBean = pool;
                heapMemoryPoolBean.setCollectionUsageThreshold((int)Math.floor(heapMemoryPoolBean.getUsage().getMax()*0.95));
            }
        }
    }
    
    public void updateScaleFonts() {
        for (DataView dataView:dataViews) {
            dataView.scale.updateFontSize();
        }
    }
    
    private Box createStatusBar() {
        Box bar = Box.createHorizontalBox();
        
        bar.add(new JLabel(" Instance: ")); // Front space serves as left padding
        instanceLabel = new JLabel();
        bar.add(instanceLabel);
        
        bar.add(new JLabel(", Loaded: "));
        totalRangeLabel = new JLabel();
        bar.add(totalRangeLabel);

        bar.add(Box.createHorizontalGlue());
        return bar;
    }
    
    public void addActionListener(ActionListener al) {
        actionListeners.add(al);
    }
    
    void updateStatusBar() {
        passiveUpdate = true;

        /* TODO TODO use some label on DataView (?)
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
        */
        
        // TODO TODO use somehting on dataview
        StringBuilder sb = new StringBuilder();
        for(DataView dataView:dataViews) {
            for(IndexBox ib: dataView.indexBoxes.values()) {
                sb.append(ib.getPacketsStatus());
            }
        }
        packetsLabel.setText(sb.toString());
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
                for(DataView dataView:dataViews) {
                    dataView.zoomInButton.setEnabled(false);
                    dataView.zoomOutButton.setEnabled(false);
                    dataView.showAllButton.setEnabled(false);
                    if (dataView.applyButton != null) {
                        dataView.applyButton.setEnabled(false);
                    }
                }
            }
        });
        final String inst = prefs.getInstance();
        instanceLabel.setText(inst);
        for(DataView dataView:dataViews) {
            for(IndexBox ib:dataView.indexBoxes.values()) {
                ib.startReloading();
            }
            dataView.tagBox.tags.clear();
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

    void refreshTmDisplay()	{
        setBusyPointer();
        updateStatusBar();
        for(DataView dataView:dataViews) {
            dataView.refreshDisplay();
        }

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
        for(DataView dataView:dataViews) {
            dataView.enableCompletenessIndex(enabled);
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
            for(DataView dataView:dataViews) {
                if(dataView.indexBoxes.containsKey("completeness")) {
                    dataView.indexBoxes.get("completeness").receiveArchiveRecords(ir.getRecordsList());
                }
            }
        } else if("histogram".equals(ir.getType())) {
            String tableName=ir.getTableName();
            for(DataView dataView:dataViews) {
                if(dataView.indexBoxes.containsKey(tableName)) {
                    dataView.indexBoxes.get(tableName).receiveArchiveRecords(ir.getRecordsList());
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
        for(DataView dataView:dataViews) {
            for(IndexBox ib:dataView.indexBoxes.values()) {
                ib.dataLoadFinished();
            }
        }
   //     debugLog("loaded " + recCount + " records, " + histoBox.groups.size() + 
     //           " PLs, " + histoBox.allPackets.size() + " packets, start \"" + dataStart + "\" stop \"" + dataStop + "\"");
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                prefs.reloadButton.setEnabled(true);
            }
        });
        loadCount = 0;
        
        if ((dataStart == INVALID_INSTANT) || (dataStop == INVALID_INSTANT)) {
            setNormalPointer();
        } else {
            for(DataView dataView:dataViews) {
                dataView.dataLoadFinished();
            }
            prefs.savePreferences();
        }
        
        prefs.reloadButton.setEnabled(true);
        setNormalPointer();
    }

    public void tagAdded(ArchiveTag ntag) {
        for(DataView dataView:dataViews) {
            dataView.tagBox.addTag(ntag);
        }
    }
    
    public void tagRemoved(ArchiveTag ntag) {
        for(DataView dataView:dataViews) {
            dataView.tagBox.removeTag(ntag);
        }
    }

    public void tagChanged(ArchiveTag oldTag, ArchiveTag newTag) {
        for(DataView dataView:dataViews) {
            dataView.tagBox.updateTag(oldTag, newTag);
        }
    }

    public void tagsAdded(List<ArchiveTag> tagList) {
        for(DataView dataView:dataViews) {
            dataView.tagBox.addTags(tagList);
        }
    }

    public void createNewTag(Selection sel) {
        for(DataView dataView:dataViews) {
            dataView.tagBox.createNewTag(sel.getStartInstant(), sel.getStopInstant());
        }
    }

    public Selection getSelection() {
        for(DataView dataView:dataViews) {
            if(dataView.getSelection()!=null) {
                return dataView.getSelection();
            }
        }
        return null;
    }

    public List<String> getSelectedPackets(String tableName) {
        for(DataView dataView:dataViews) {
            if(dataView.indexBoxes.containsKey(tableName)) {
                return dataView.getSelectedPackets("tm");
            }
        }
        return Collections.emptyList();
    }
}
