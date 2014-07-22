package org.yamcs.ui.archivebrowser;

import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
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
public class DataView extends JScrollPane {

    private static final long serialVersionUID = 1L;
    HeaderPanel headerPanel;
    IndexPanel indexPanel;
    Map<String,IndexBox> indexBoxes = new HashMap<String,IndexBox>();
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
    int dragButton, previewLocatorX, mouseLocatorX;
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
        setPreferredSize(new Dimension(850, 400));

        headerPanel = new HeaderPanel();
        indexPanel = new IndexPanel();

        setColumnHeaderView(headerPanel);
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

    public void refreshDisplay() {
        refreshDisplay(false);
    }

    /**
     * @param force whether it should also adapt its width when it's shrinking
     */
    public void refreshDisplay(boolean force) {
        int panelw = getViewport().getExtentSize().width;
        
        if ( !zoomStack.empty() ) {
            ZoomSpec zoom = zoomStack.peek();
            if (panelw > zoom.getPixels() || force) {
                zoom.setPixels(panelw);
            }
            panelw = zoom.getPixels();
            headerPanel.scale.setToZoom(zoom);
            if(showTagBox) {
                headerPanel.tagBox.setToZoom(zoom);
            } else {
                headerPanel.tagBox.removeAll();
            }
            
            for(IndexBox ib:indexBoxes.values()) {
                ib.setToZoom(zoom);
            }
        }
        headerPanel.scale.setMaximumSize(new Dimension(panelw, headerPanel.scale.getPreferredSize().height));
        headerPanel.scale.setMinimumSize(headerPanel.scale.getMaximumSize());
        headerPanel.scale.setPreferredSize(headerPanel.scale.getMaximumSize());
        headerPanel.scale.setSize(headerPanel.scale.getMaximumSize());
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

    long getMouseInstant(MouseEvent e) {
        if (zoomStack.isEmpty()) {
            return TimeEncoding.INVALID_INSTANT;
        }
        previewLocator = zoomStack.peek().convertPixelToInstant(e.getX());
        return previewLocator;
    }

    void setMouseLabel(MouseEvent e) {
        long instant = getMouseInstant(e);
        if (instant==TimeEncoding.INVALID_INSTANT) {
            setToolTipText(null);
        } else {
            setToolTipText(TimeEncoding.toCombinedFormat(instant));
        }
        dataViewer.signalMousePosition(instant);
    }
    
    public void zoomIn()  {
        ZoomSpec zoom = zoomStack.peek();
        final JViewport vp = getViewport();

        // save current location in current zoom spec
        zoom.viewLocation = zoom.convertPixelToInstant(vp.getViewPosition().x);

        // create new zoom spec and add it to the stack
        long startInstant;
        long stopInstant;
        if(currentSelection==null) {
            // Zoom in on center 3rd of current view extent
            int extentWidth=vp.getExtentSize().width;
            int nextStartX = vp.getViewPosition().x + (extentWidth/3);
            int nextStopX = nextStartX + (extentWidth/3);
            startInstant=zoom.convertPixelToInstant(nextStartX);
            stopInstant=zoom.convertPixelToInstant(nextStopX);
            zoom.setSelectedRange(TimeEncoding.INVALID_INSTANT, TimeEncoding.INVALID_INSTANT);
        } else {
            startInstant = currentSelection.getStartInstant();
            stopInstant = currentSelection.getStopInstant();
            zoom.setSelectedRange(startInstant, stopInstant);
        }

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
        zoom.viewLocation = startInstant;
        zoomStack.push(zoom);

        resetSelection();
        refreshDisplay();

        // set the view to the previously selected region
        setViewLocationFromZoomstack();
    }

    void setViewLocationFromZoomstack() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if(zoomStack.isEmpty()) return;
                final ZoomSpec currentZoom = zoomStack.peek();
                final JViewport vp = getViewport();
                int x = (int)((currentZoom.viewLocation - currentZoom.startInstant) / currentZoom.pixelRatio);
                vp.setViewPosition(new Point(x, vp.getViewPosition().y));
                //debugLog("zoom out, view width " + vp.getView().getSize().width + " location " + x + " = " + currentZoom.viewLocation);
            }
        });
    }
    
    public void archiveLoadFinished() {
        for(IndexBox ib:indexBoxes.values()) {
            ib.dataLoadFinished();
        }
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
                dataViewer.zoomOutButton.setEnabled(false);
                dataViewer.showAllButton.setEnabled(true);
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
        refreshDisplay(true);
        setViewLocationFromZoomstack();
    }

    void zoomOut() {
        if (zoomStack.size() > 1) {
            zoomStack.pop();
            ZoomSpec zoom = zoomStack.peek();
            if(zoom.selectionStart!=TimeEncoding.INVALID_INSTANT && zoom.selectionStop!=TimeEncoding.INVALID_INSTANT) {
                // Restore selection as it was made before zoom in (to make it easier to go back and forth between zoom in/out)
                updateSelection(zoom.selectionStart, zoom.selectionStop);
                dataViewer.signalSelectionChange(currentSelection);
            }
            refreshDisplay();

            // place the view where it was
            setViewLocationFromZoomstack();
        }
    }

    public void doMouseDragged(MouseEvent e) {
        indexPanel.doMouseDragged(e);
        // TTM does not show the tooltip in mouseDragged() so we send a MOUSE_MOVED event
        dispatchEvent(new MouseEvent(e.getComponent(), MouseEvent.MOUSE_MOVED, e.getWhen(), e.getModifiers(),
                e.getX(), e.getY(), e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }

    public void doMouseMoved(MouseEvent e) {
        headerPanel.doMouseMoved(e);
        setMouseLabel(e);
        setPointer(e);
    }

    public void doMousePressed(MouseEvent e) {
        indexPanel.doMousePressed(e);
    }

    public void doMouseReleased(MouseEvent e) {
        indexPanel.doMouseReleased(e);
    }

    public void doMouseExited(MouseEvent e) {
        dataViewer.signalMousePosition(TimeEncoding.INVALID_INSTANT);
        mouseLocatorX = -1;
        repaint(); // Force removal of needle in paint()
    }

    public void updateSelection(Long selectionStart, Long selectionStop) {
        if(!archivePanel.passiveUpdate) {
            if ((selectionStart!=null) && (selectionStop!=null)) {
                long start = selectionStart;
                long stop = selectionStop;
                if ((start != TimeEncoding.INVALID_INSTANT) && (stop != TimeEncoding.INVALID_INSTANT)) {
                    updateSelection(start, stop);
                    emitActionEvent("histo_selection_finished");
                }
            }
        }
    }

    public void selectionFinished() {
        for(String name : indexBoxes.keySet()) {
            emitActionEvent(name+"_selection_finished");
        }
    }
    
    /**
     * Called after the mouse dragging selection is updated on the boxes to update the selectionStart/Stop fields
     * We use the passiveUpdate to avoid a ping pong effect
     */
    void updateSelectionFields() {
        archivePanel.passiveUpdate=true;
        dataViewer.signalSelectionChange(currentSelection);
        archivePanel.passiveUpdate=false;
    }
    
    public List<String> getSelectedPackets(String type) {
        return indexBoxes.get(type).getPacketsForSelection(getSelection());
    }

    /**called from the tagBox when a tag is selected. Update tmBox selection to this*/
    public void selectedTag(ArchiveTag tag) {
        archivePanel.passiveUpdate=true;
        if(tag.hasStart())  {
            dataViewer.signalSelectionStartChange(tag.getStart());
        } else {
            dataViewer.signalSelectionStartChange(archivePanel.dataStart);
        }
        archivePanel.passiveUpdate=false;
         
        if(tag.hasStop()) {
            dataViewer.signalSelectionStopChange(tag.getStop());
        } else {
            dataViewer.signalSelectionStopChange(archivePanel.dataStop);
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
        dataViewer.signalSelectionChange(null);
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

    public class HeaderPanel extends Box {
        TagBox tagBox;
        TMScale scale;
        public HeaderPanel() {
            super(BoxLayout.Y_AXIS);
            setBorder(BorderFactory.createEmptyBorder());
            setOpaque(false);

            tagBox=new TagBox(DataView.this);
            tagBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(tagBox);

            scale = new TMScale();
            scale.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(scale);
        }

        public void doMouseMoved(MouseEvent e) {
            if(zoomStack.isEmpty()) return;
            mouseLocatorX = e.getX();
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if(mouseLocatorX > 0) {
                // follow mouse for better timeline positioning
                g.setColor(Color.DARK_GRAY);
                int tagBoxHeight = tagBox.getHeight();
                g.drawLine(mouseLocatorX, tagBoxHeight, mouseLocatorX, getHeight());
            }
        }
    }

    public class IndexPanel extends Box {

        public IndexPanel() {
            super(BoxLayout.Y_AXIS);
            setBorder(BorderFactory.createEmptyBorder());
            setOpaque(false);
        }

        public void doMousePressed(MouseEvent e) {
            if(zoomStack.isEmpty()) return;
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

        public void doMouseReleased(MouseEvent e) {
            if (!zoomStack.isEmpty()) {
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
            if (!zoomStack.isEmpty()) {
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
