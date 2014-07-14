package org.yamcs.ui.archivebrowser;

import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

import static org.yamcs.utils.TimeEncoding.INVALID_INSTANT;

/**
 * Shows a number of IndexBoxes together in a scrollable component. A timeline
 * and a tag bar is shared among all included IndexBoxes
 */
public class DataView extends JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;
    private Box indexPanel;
    Map<String,IndexBox> indexBoxes = new HashMap<String,IndexBox>();
    TagBox tagBox;
    TMScale scale;
    private boolean showTagBox = true;
    Stack<ZoomSpec> zoomStack = new Stack<ZoomSpec>();
    private List<ActionListener> actionListeners=new ArrayList<ActionListener>();
    
    public JToolBar buttonToolbar;
    ArchivePanel archivePanel;
    
    JButton zoomInButton, zoomOutButton, showAllButton, applyButton;
    JFormattedTextField selectionStart, selectionStop;
    boolean replayEnabled;
    boolean hideResponsePackets=true;
    
    long lastStartTimestamp = -1;
    long lastEndTimestamp = -1;
    
    private JScrollPane scrollableContent;
    
    public DataView(ArchivePanel archivePanel, boolean replayEnabled) {
        super(new BorderLayout());
        this.archivePanel = archivePanel;
        this.replayEnabled = replayEnabled;
        setBorder(BorderFactory.createEmptyBorder());
        setBackground(Color.WHITE);
        add(createFixedContent(), BorderLayout.NORTH);
        add(createScrollableContent(), BorderLayout.CENTER);
    }
    
    public void addIndex(String tableName, String name) {
        addIndex(tableName, name, -1);
    }
    
    public void addIndex(String tableName, String name, long mergeTime) {
        IndexBox indexBox = new IndexBox(this, name);
        indexBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        indexBox.setMergeTime(mergeTime);
        
        indexBoxes.put(tableName, indexBox);
        indexPanel.add(indexBox);
        if (replayEnabled && "tm".equals(tableName)) {
            archivePanel.replayPanel.setTmBox(indexBox);
        }
    }
    
    public void addVerticalGlue() {
        indexPanel.add(Box.createVerticalGlue());
    }
    
    private Box createFixedContent() {
        Box fixedTop = Box.createVerticalBox();
        fixedTop.setOpaque(false);
        fixedTop.add(createButtonToolbar());

        Box top=getTopBox();
        top.setAlignmentX(0);
        fixedTop.add(top);
        return fixedTop;
    }
    
    private JToolBar createButtonToolbar() {
        buttonToolbar = new JToolBar("Button Toolbar");
        buttonToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonToolbar.setOpaque(false);
        buttonToolbar.setFloatable(false);

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
        return buttonToolbar;
    }
    
    private JScrollPane createScrollableContent() {
        indexPanel = Box.createVerticalBox();
        indexPanel.setBackground(Color.BLUE);
        scrollableContent = new JScrollPane(indexPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollableContent.setBorder(BorderFactory.createEmptyBorder());
        scrollableContent.getViewport().setOpaque(false);
        scrollableContent.setOpaque(false); // TODO maybe not
        scrollableContent.setPreferredSize(new Dimension(850, 400));
        indexPanel.setOpaque(false);
        
        Box scalebox = Box.createVerticalBox();
        scalebox.setOpaque(false);
        
        tagBox=new TagBox(this);
        tagBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        scalebox.add(tagBox);
        
        scale = new TMScale();
        scale.setAlignmentX(Component.LEFT_ALIGNMENT);
        scalebox.add(scale);
        
        scrollableContent.setColumnHeaderView(scalebox);
        scrollableContent.getColumnHeader().setOpaque(false);
        return scrollableContent;
    }
    
    public void enableCompletenessIndex(boolean enabled) {
        if(!enabled) {
            indexPanel.remove(indexBoxes.get("completeness"));
        } else {
            indexPanel.add(indexBoxes.get("completeness"), 1);
        }
    }

    public void refreshDisplay() {
        int panelw = scrollableContent.getViewport().getExtentSize().width;
        
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
    }
    
    private void zoomIn(Selection sel)  {
        ZoomSpec zoom = zoomStack.peek();
        final JViewport vp = scrollableContent.getViewport();

        // save current location in current zoom spec
        zoom.viewLocation = zoom.convertPixelToInstant(vp.getViewPosition().x);

        // create new zoom spec and add it to the stack
        long startInstant = sel.getStartInstant();
        long stopInstant = sel.getStopInstant();
        long range = stopInstant - startInstant;
        
        long reqStart=archivePanel.getRequestedDataStart();
        long reqStop=archivePanel.getRequestedDataStop();
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
        refreshDisplay();

        // set the view to the previously selected region
        setViewLocationFromZoomstack();
    }
    
    Box getTopBox() {
        // status bars on the northern part of the window

        Box top = Box.createHorizontalBox();

        GridBagLayout lay = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel archiveinfo = new JPanel(lay);
        archiveinfo.setOpaque(false);
        top.add(archiveinfo);
        gbc.insets = new Insets(4, 4, 0, 1);

        JLabel lab = new JLabel("Selection:");
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

        return top;
    }
    
    private void setViewLocationFromZoomstack() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final ZoomSpec currentZoom = zoomStack.peek();
                final JViewport vp = scrollableContent.getViewport();
                int x = (int)((currentZoom.viewLocation - currentZoom.startInstant) / currentZoom.pixelRatio);
                vp.setViewPosition(new Point(x, vp.getViewPosition().y));
                //debugLog("zoom out, view width " + vp.getView().getSize().width + " location " + x + " = " + currentZoom.viewLocation);
            }
        });
    }
    
    public void dataLoadFinished() {
        if (zoomStack.isEmpty() ||
                ((archivePanel.prefs.getStartTimestamp() != lastStartTimestamp) ||
                        (archivePanel.prefs.getEndTimestamp() != lastEndTimestamp)
                )) {

            int w = scrollableContent.getViewport().getExtentSize().width;
            zoomStack.clear();
            long reqStart=archivePanel.getRequestedDataStart();
            long zstart=archivePanel.dataStart;
            if(reqStart!=TimeEncoding.INVALID_INSTANT) {
                zstart=Math.min(reqStart, zstart);
            }
            
            long reqStop=archivePanel.getRequestedDataStop();
            long zstop=archivePanel.dataStop;
          
            if(reqStop!=TimeEncoding.INVALID_INSTANT) {
                zstop=Math.max(reqStop, zstop);
            }
            long range=zstop - zstart;
            zstart-=range/100;
            zstop+=range/100;
            zoomStack.push(new ZoomSpec(zstart, zstop, w, zstop - zstart));
        }

        lastStartTimestamp = archivePanel.prefs.getStartTimestamp();
        lastEndTimestamp = archivePanel.prefs.getEndTimestamp();


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
        refreshDisplay();
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
            refreshDisplay();
            setViewLocationFromZoomstack();
        } else if (cmd.equals("zoomout")) {

            if (zoomStack.size() > 1) {
                zoomStack.pop();
                refreshDisplay();

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
    
    public void updateSelection() {
        if(!archivePanel.passiveUpdate) {
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
    
    /**
     * Called after the mouse dragging selection is updated on the boxes to update the selectionStart/Stop fields
     * We use the passiveUpdate to avoid a ping pong effect
     * 
     * @param ibox
     */
    void updateSelectionFields(IndexBox ibox) {
        archivePanel.passiveUpdate=true;
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
        archivePanel.passiveUpdate=false;
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
         archivePanel.passiveUpdate=true;
         if(tag.hasStart())  {
             selectionStart.setValue(tag.getStart());
         } else {
             selectionStart.setValue(archivePanel.dataStart);
         }
         archivePanel.passiveUpdate=false;
         
         if(tag.hasStop()) {
             selectionStop.setValue(tag.getStop());
         } else {
             selectionStop.setValue(archivePanel.dataStop);
         }
     }

    public void setMoveLeftPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    }

    public void setMoveRightPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    }
    
    public void setBusyPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    public void setNormalPointer() {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
    
    public void resetSelection() {
        emitActionEvent("selection_reset");
    }
    
    public void addActionListener(ActionListener al) {
        actionListeners.add(al);
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
}
