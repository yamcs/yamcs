package org.yamcs.ui.archivebrowser;

import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Shows a number of IndexBoxes together. A timeline and a tag bar is shared
 * among all included IndexBoxes.
 * Range selections can be made which visually span all included IndexBoxes
 */
public class DataView extends JScrollPane implements MouseInputListener {

    private static final long serialVersionUID = 1L;
    IndexPanel indexPanel;
    Map<String,IndexBox> indexBoxes = new HashMap<String,IndexBox>();
    TagBox tagBox;
    TMScale scale;
    private boolean showTagBox = true;
    Stack<ZoomSpec> zoomStack = new Stack<ZoomSpec>();
    private List<ActionListener> actionListeners=new ArrayList<ActionListener>();
    
    private DataViewer dataViewer;
    ArchivePanel archivePanel;

    boolean hideResponsePackets=true;
    
    long lastStartTimestamp = -1;
    long lastEndTimestamp = -1;

    boolean startColor = false;
    int startX, stopX, deltaX;
    boolean drawPreviewLocator;
    float previewLocatorAlpha;
    int dragButton, previewLocatorX;
    SelectionImpl currentSelection;
    long startLocator, stopLocator, currentLocator, previewLocator, seekLocator;

    final long DO_NOT_DRAW = Long.MIN_VALUE;
    final int handleWidth = 6;
    final int handleHeight = 12;
    final int handleWidth2 = 9; // current position locator
    final int cursorSnap = 2; // epsilon between mouse position and handle line to detect cursor change
    final Color handleFill = Color.YELLOW; // start/stop locator colour
    final Color currentFill = Color.GREEN; // current replay position locator colour

    int antsOffset = 0;
    final int antsLength = 8;

    public DataView(ArchivePanel archivePanel, DataViewer dataViewer) {
        super(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        this.dataViewer = dataViewer;
        this.archivePanel = archivePanel;

        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setOpaque(false);
        setOpaque(false); // TODO maybe not
        setPreferredSize(new Dimension(850, 400));

        indexPanel = new IndexPanel();

        Box scalebox = Box.createVerticalBox();
        scalebox.setOpaque(false);

        tagBox=new TagBox(this);
        tagBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        scalebox.add(tagBox);

        scale = new TMScale();
        scale.setAlignmentX(Component.LEFT_ALIGNMENT);
        scalebox.add(scale);

        setColumnHeaderView(scalebox);
        setViewportView(indexPanel);

        getColumnHeader().setOpaque(false);

        startLocator = stopLocator = currentLocator = DO_NOT_DRAW;
        drawPreviewLocator = false;

        resetSelection();
        new java.util.Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (this) {
                    if (getComponentCount() > 0) {
                        final int y = getComponent(0).getLocation().y;
                        final int h = getSize().height - y;
                        int x, y2;
                        if (currentSelection != null) {
                            if ( ++antsOffset >= antsLength ) {
                                antsOffset = 0;
                                startColor = !startColor;
                            }
                            final int x1 = currentSelection.getStartX(), x2 = currentSelection.getStopX();
                            repaint(0, x1, y, x2 - x1 + 1, h);
                        }
                    }
                    if (drawPreviewLocator) {
                        repaint();
                        previewLocatorAlpha -= 0.05f;
                        drawPreviewLocator = previewLocatorAlpha > 0.0f;
                    }
                }
            }
        }, 1000, 150);

        ToolTipManager ttmgr = ToolTipManager.sharedInstance();
        ttmgr.setInitialDelay(0);
        ttmgr.setReshowDelay(0);
        ttmgr.setDismissDelay(Integer.MAX_VALUE);
        setOpaque(false);
    }
    
    public void addIndex(String tableName, String name, long mergeTime) {
        IndexBox indexBox = new IndexBox(this, name);
        indexBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        indexBox.setMergeTime(mergeTime);
        
        indexBoxes.put(tableName, indexBox);
        indexPanel.add(indexBox);
    }
    
    public void addVerticalGlue() {
        indexPanel.add(Box.createVerticalGlue());
    }
    
    public void enableCompletenessIndex(boolean enabled) {
        if(!enabled) {
            indexPanel.remove(indexBoxes.get("completeness"));
        } else {
            indexPanel.add(indexBoxes.get("completeness"), 1);
        }
    }

    public void refreshDisplay() {
        int panelw = getViewport().getExtentSize().width;
        
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

    void setPointer(MouseEvent e) {
        if (archivePanel.prefs.reloadButton.isEnabled()) {
            setNormalPointer();
            if (currentSelection != null) {
                if (Math.abs(e.getX() - currentSelection.getStartX()) <= cursorSnap) {
                    setMoveLeftPointer();
                }
                if (Math.abs(e.getX() - currentSelection.getStopX()) <= cursorSnap) {
                    setMoveRightPointer();
                }
            }
        }
    }

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        return new Point(event.getX() - 94, event.getY() + 20);
    }

    String getMouseText(MouseEvent e) {
        if (zoomStack.isEmpty()) {
            return null;
        }
        previewLocator = zoomStack.peek().convertPixelToInstant(e.getX());
        return TimeEncoding.toCombinedFormat(previewLocator);
    }

    void setMouseLabel(MouseEvent e) {
        setToolTipText(getMouseText(e));
    }
    
    private void zoomIn(Selection sel)  {
        ZoomSpec zoom = zoomStack.peek();
        final JViewport vp = getViewport();

        // save current location in current zoom spec
        zoom.viewLocation = zoom.convertPixelToInstant(vp.getViewPosition().x);

        // create new zoom spec and add it to the stack
        long startInstant = sel.getStartInstant();
        long stopInstant = sel.getStopInstant();
        long range = stopInstant - startInstant;
        
        long reqStart=archivePanel.getRequestedDataStart();
        long reqStop=archivePanel.getRequestedDataStop();
        long zstart=startInstant - range * 2;
        if(reqStart!= TimeEncoding.INVALID_INSTANT) {
            zstart=Math.max(zstart, reqStart);
        }
        long zstop=stopInstant + range * 2;
        if(reqStop!=TimeEncoding.INVALID_INSTANT) {
            zstop=Math.min(zstop, reqStop);
        }
        zoom = new ZoomSpec(zstart, zstop, vp.getExtentSize().width, range);
        zoom.viewLocation = sel.getStartInstant();
        zoomStack.push(zoom);

        resetSelection();
        refreshDisplay();

        // set the view to the previously selected region
        setViewLocationFromZoomstack();
    }

    private void setViewLocationFromZoomstack() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final ZoomSpec currentZoom = zoomStack.peek();
                final JViewport vp = getViewport();
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

            int w = getViewport().getExtentSize().width;
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
                dataViewer.zoomInButton.setEnabled(true);
                dataViewer.zoomOutButton.setEnabled(true);
                dataViewer.showAllButton.setEnabled(true);
                if (dataViewer.applyButton != null) dataViewer.applyButton.setEnabled(true);
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

    void showAll() {
        while (zoomStack.size() > 1) {
            zoomStack.pop();
        }
        resetSelection();
        refreshDisplay();
        setViewLocationFromZoomstack();
    }

    void zoomOut() {
        if (zoomStack.size() > 1) {
            zoomStack.pop();
            refreshDisplay();

            // place the view where it was
            setViewLocationFromZoomstack();
        }
    }

    void zoomIn() {
        Selection s = getSelection();
        if (s != null) {
            zoomIn(s);
        }
    }

    //this happens when the mouse is pressed outside the telemetry bars
    @Override
    public void mousePressed(MouseEvent e) {
        indexPanel.doMousePressed(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        indexPanel.doMouseReleased(e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        setMouseLabel(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {}
    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseMoved(MouseEvent e) {
        setMouseLabel(e);
        setPointer(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        indexPanel.doMouseDragged(e);

        // TTM does not show the tooltip in mouseDragged() so we send a MOUSE_MOVED event
        dispatchEvent(new MouseEvent(e.getComponent(), MouseEvent.MOUSE_MOVED, e.getWhen(), e.getModifiers(),
                e.getX(), e.getY(), e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }
    
    public void updateSelection() {
        if(!archivePanel.passiveUpdate) {
            Long sstart=(Long) dataViewer.selectionStart.getValue();
            Long sstop=(Long) dataViewer.selectionStop.getValue();
            if ((sstart!=null) && (sstop!=null)) {
                long start =sstart;
                long stop =sstop;
                if ((start != TimeEncoding.INVALID_INSTANT) && (stop != TimeEncoding.INVALID_INSTANT)) {
                    updateSelection(start, stop);
                    emitActionEvent("histo_selection_finished");
                }
            }
        }
    }

    public void selectionFinished() {
        System.out.println("do sel finish");
        for(Map.Entry<String, IndexBox>e: indexBoxes.entrySet()) {
            IndexBox ib=e.getValue();
            String name=e.getKey();
            emitActionEvent(name+"_selection_finished");
        }
    }
    
    /**
     * Called after the mouse dragging selection is updated on the boxes to update the selectionStart/Stop fields
     * We use the passiveUpdate to avoid a ping pong effect
     */
    void updateSelectionFields() {
        archivePanel.passiveUpdate=true;
        Selection s = getSelection();
        if (s != null) {
            dataViewer.selectionStart.setValue(s.getStartInstant());
            dataViewer.selectionStop.setValue(s.getStopInstant());
        } else {
            dataViewer.selectionStart.setValue(TimeEncoding.INVALID_INSTANT);
            dataViewer.selectionStop.setValue(TimeEncoding.INVALID_INSTANT);
        }
        archivePanel.passiveUpdate=false;
    }
    
    public List<String> getSelectedPackets(String type) {
        return indexBoxes.get(type).getPacketsForSelection(getSelection());
     }

     /**caled from the tagBox when a tag is selected. Update tmBox selection to this*/
     public void selectedTag(ArchiveTag tag) {
         archivePanel.passiveUpdate=true;
         if(tag.hasStart())  {
             dataViewer.selectionStart.setValue(tag.getStart());
         } else {
             dataViewer.selectionStart.setValue(archivePanel.dataStart);
         }
         archivePanel.passiveUpdate=false;
         
         if(tag.hasStop()) {
             dataViewer.selectionStop.setValue(tag.getStop());
         } else {
             dataViewer.selectionStop.setValue(archivePanel.dataStop);
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

    void setStartLocator(long position) {
        startLocator = position;
    }

    void setStopLocator(long position) {
        stopLocator = position;
    }

    void setCurrentLocator(long position) {
        currentLocator = position;
        repaint();
    }
    
    public void addActionListener(ActionListener al) {
        actionListeners.add(al);
    }

    public void resetSelection() {
        startX = -1;
        currentSelection = null;
        repaint();
        emitActionEvent("selection_reset");
    }

    public SelectionImpl getSelection() {
        return currentSelection;
    }

    void updateSelection(long start, long stop) {
        if (currentSelection == null) {
            currentSelection = new SelectionImpl(start, stop);
        } else {
            currentSelection.set(start, stop);
        }
        // set last mouse x/y coords
        startX = currentSelection.getStartX();
        stopX = currentSelection.getStopX();
        repaint();
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

    public class IndexPanel extends Box {

        public IndexPanel() {
            super(BoxLayout.Y_AXIS);
            setBorder(BorderFactory.createEmptyBorder());
            setOpaque(false);
        }

        public void doMousePressed(MouseEvent e) {
            if (zoomStack.peek()!=null) {
                dragButton = e.getButton();
                if (dragButton == MouseEvent.BUTTON1) {
                    if (dataViewer.replayEnabled && (e.getClickCount() == 2)) {
                        drawPreviewLocator = true;
                        previewLocatorAlpha = 0.8f;
                        previewLocatorX = e.getX();
                        archivePanel.seekReplay(previewLocator);
                        repaint();
                    } else {
                        if ((currentSelection != null) && (Math.abs(e.getX() - currentSelection.getStartX()) <= cursorSnap)) {
                            deltaX = e.getX() - currentSelection.getStartX();
                            startX = currentSelection.getStopX();
                            doMouseDragged(e);
                        } else if ((currentSelection != null) && (Math.abs(e.getX() - currentSelection.getStopX()) <= cursorSnap)) {
                            deltaX = e.getX() - currentSelection.getStopX();
                            startX = currentSelection.getStartX();
                            doMouseDragged(e);
                        } else {
                            resetSelection();
                            doMouseDragged(e);
                        }
                    }
                }
            }
        }

        public void doMouseReleased(MouseEvent e) {
            if (zoomStack.peek()!=null) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (currentSelection != null) {
                        // if only one line was selected, the user wants to deselect
                        if (startX == stopX) {
                            resetSelection();
                            updateSelectionFields();
                        } else {
                            // selection finished
                            selectionFinished();
                        }
                    }
                }
            }
        }

        void doMouseDragged(MouseEvent e) {
            if (zoomStack.peek()!=null) {
                if (dragButton == MouseEvent.BUTTON1) {
                    //final JViewport vp = tmscrollpane.getViewport();
                    //stopX = Math.max(e.getX(), vp.getViewPosition().x);
                    //stopX = Math.min(stopX, vp.getViewPosition().x + vp.getExtentSize().width - 1);
                    stopX = e.getX() - deltaX;
                    if (startX == -1) startX = stopX;
                    if (currentSelection == null) {
                        currentSelection = new SelectionImpl(startX, stopX);
                    } else {
                        currentSelection.set(startX, stopX);
                    }
                    setMouseLabel(e);
                    setPointer(e);
                    repaint();

                    // this will trigger an update of selection start/stop text fields
                    updateSelectionFields();
                }
            }
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            // draw the selection rectangle over all the TM panels

            if ( (getComponentCount() > 0) && !zoomStack.isEmpty() ) {
                ZoomSpec zoom = zoomStack.peek();

                final int h = getHeight(); ///
                int x, y2;

                if (currentSelection != null) {
                    final int offset = antsOffset - antsLength;
                    final int x1 = currentSelection.getStartX(), x2 = currentSelection.getStopX();
                    int y1, yend = h;
                    boolean color = startColor;

                    g.setColor(new Color(224, 234, 242, 64));
                    g.fillRect(x1, 0, x2 - x1 + 1, h);

                    for ( y1 = offset; y1 < yend; y1 += antsLength ) {
                        y2 = y1 + (antsLength - 1);
                        if ( y2 >= yend ) y2 = yend - 1;
                        g.setColor((color = !color) ? Color.BLACK : Color.WHITE);
                        g.drawLine(x1, y1, x1, y2);
                        g.drawLine(x2, y1, x2, y2);
                    }
                }

                if ((startLocator != DO_NOT_DRAW) || (stopLocator != DO_NOT_DRAW) || (currentLocator != DO_NOT_DRAW) ||
                        drawPreviewLocator) {

                    int xmax = getSize().width - handleWidth;

                    if ( startLocator != DO_NOT_DRAW ) {
                        x = zoom.convertInstantToPixel(startLocator);
                        //debugLog("startLocator (" + x + "," + y + ") box width " + getSize().width);
                        if ( (x >= 0) && (x < xmax) ) {
                            final int[] px = { x, x + handleWidth, x };
                            final int[] py = { handleHeight, handleHeight / 2, 0 };
                            g.setColor(handleFill);
                            g.fillPolygon(px, py, px.length);
                            g.setColor(Color.BLACK);
                            g.drawPolygon(px, py, px.length);
                            g.drawLine(x, 0, x, h - 1);
                        }
                    }

                    if ( stopLocator != DO_NOT_DRAW ) {
                        x = zoom.convertInstantToPixel(stopLocator);
                        //debugLog("stopLocator (" + x + "," + y + ") box width " + getSize().width);
                        if ( (x >= 0) && (x < xmax) ) {
                            final int[] px = { x, x - handleWidth, x };
                            final int[] py = { handleHeight, handleHeight / 2, 0 };
                            g.setColor(handleFill);
                            g.fillPolygon(px, py, px.length);
                            g.setColor(Color.BLACK);
                            g.drawPolygon(px, py, px.length);
                            g.drawLine(x, 0, x, 0 + h - 1);
                        }
                    }

                    // draw the current position
                    if ( currentLocator != DO_NOT_DRAW ) {
                        x = zoom.convertInstantToPixel(currentLocator);
                        //debugLog("currentLocator (" + x + "," + y + ") box width " + getSize().width);
                        if ( (x >= 0) && (x < xmax) ) {
                            final int[] px = { x - handleWidth2 / 2, x, x + handleWidth2 / 2 };
                            final int[] py = { handleHeight, 0, handleHeight };
                            g.setColor(currentFill);
                            g.fillPolygon(px, py, px.length);
                            g.setColor(Color.BLACK);
                            g.drawPolygon(px, py, px.length);
                            g.drawLine(x, handleHeight, x, h - 1);
                        }
                    }

                    // draw the preview replay position line
                    if ( drawPreviewLocator ) {
                        final int[] px = { previewLocatorX - handleWidth2 / 2, previewLocatorX, previewLocatorX + handleWidth2 / 2 };
                        final int[] py = { handleHeight, 0, handleHeight };

                        float[] c = currentFill.getRGBColorComponents(null);
                        g.setColor(new Color(c[0], c[1], c[2], previewLocatorAlpha));
                        g.fillPolygon(px, py, px.length);

                        c = Color.BLACK.getRGBColorComponents(c);
                        g.setColor(new Color(c[0], c[1], c[2], previewLocatorAlpha));
                        g.drawPolygon(px, py, px.length);
                        g.drawLine(previewLocatorX, handleHeight, previewLocatorX, h - 1);
                    }
                }
            }
        }
    }
}
