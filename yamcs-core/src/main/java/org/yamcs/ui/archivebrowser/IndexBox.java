/**
 * 
 */
package org.yamcs.ui.archivebrowser;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.prefs.Preferences;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;
import javax.swing.event.MouseInputListener;

import org.yamcs.ui.PrefsObject;
import org.yamcs.ui.archivebrowser.ArchivePanel.SelectionImpl;
import org.yamcs.ui.archivebrowser.ArchivePanel.IndexChunkSpec;
import org.yamcs.ui.archivebrowser.ArchivePanel.ZoomSpec;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

/**
 * Represents a collection of IndexLine shown vertically 
 * @author nm
 *
 */
public class IndexBox extends Box implements MouseInputListener {
    private static final long serialVersionUID = 1L;
    private final ArchivePanel archivePanel;
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
    boolean startColor = false;
    
    JLabel popupLabelItem;
    JPopupMenu packetPopup;
    JMenuItem removePacketMenuItem, removeExceptPacketMenuItem, removePayloadMenuItem, changeColorMenuItem, copyOpsnameMenuItem;
    IndexLineSpec selectedPacket;
    static final int tmRowHeight = 20;
    
    HashMap<String,IndexLineSpec> allPackets;
    HashMap<String,ArrayList<IndexLineSpec>> groups;
    HashMap<String,TreeSet<IndexChunkSpec>> tmData;
    private ZoomSpec zoom;
    private String name;
    /**
     * because the histogram contains regular splits each 3600 seconds, merge here the records 
     * that are close enough to each other.
     *-1 means no merging 
     */
    long mergeTime=-1;
    Preferences prefs;

    
    IndexBox(ArchivePanel archivePanel, String name) {
        super(BoxLayout.PAGE_AXIS);
        this.archivePanel = archivePanel;
        this.name=name;
        addMouseMotionListener(this);
        addMouseListener(this);
        resetSelection();

        allPackets = new HashMap<String,IndexLineSpec>();
        groups = new HashMap<String,ArrayList<IndexLineSpec>>();
        tmData = new HashMap<String,TreeSet<IndexChunkSpec>>();
        prefs = Preferences.userNodeForPackage(IndexBox.class).node(name);
        
        startLocator = stopLocator = currentLocator = DO_NOT_DRAW;
        drawPreviewLocator = false;

        ToolTipManager ttmgr = ToolTipManager.sharedInstance();
        ttmgr.setInitialDelay(0);
        ttmgr.setReshowDelay(0);
        ttmgr.setDismissDelay(Integer.MAX_VALUE);

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
    }

    void removeIndexLines() {
        for(Component c:getComponents()) {
            remove(c);
        }
    }

    @Override
    public void paint(Graphics g) {
        // move the scale component always to the start of the viewport,
        // so it is always visible, even when scrolling vertically

        super.paint(g);
        // draw the selection rectangle over all the TM panels 
        if ( (getComponentCount() > 0) && zoom!=null ) {

            final int h = getHeight();
            int x, y2;

            if (currentSelection != null) {
                final int offset = antsOffset - antsLength;
                final int x1 = currentSelection.getStartX(), x2 = currentSelection.getStopX();
                int y1, yend = h;
                boolean color = startColor;

                g.setColor(new Color(255, 255, 255, 64));
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

    

    protected void buildPopup() {
        if (groups.isEmpty()) {
            packetPopup = null;
        } else {
            packetPopup = new JPopupMenu();

            popupLabelItem = new JLabel();
            packetPopup.insert(popupLabelItem, 0);

            packetPopup.addSeparator();

            JMenu packetmenu = new JMenu("Add Packets");
            packetPopup.add(packetmenu);

            removePacketMenuItem = new JMenuItem("Remove This Packet");
            removePacketMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    removeSelectedPacket();
                }
            });
            packetPopup.add(removePacketMenuItem);

            removeExceptPacketMenuItem = new JMenuItem("Remove All But This Packet");
            removeExceptPacketMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    removeAllButThisLine();
                }
            });
            packetPopup.add(removeExceptPacketMenuItem);

            removePayloadMenuItem = new JMenuItem();
            // text is set when showing the popup menu
            removePayloadMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    removeGroupLines();
                }
            });
            packetPopup.add(removePayloadMenuItem);

            copyOpsnameMenuItem = new JMenuItem("Copy Ops-Name to Clipboard");
            copyOpsnameMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if ( selectedPacket != null ) {
                        StringSelection strsel = new StringSelection(selectedPacket.lineName);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(strsel, strsel);
                    }
                }
            });
            packetPopup.add(copyOpsnameMenuItem);

            changeColorMenuItem = new JMenuItem("Change Color");
            changeColorMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    selectedPacket.newColor();
                    archivePanel.setBusyPointer();
                    redrawTmPanel(selectedPacket);
                    archivePanel.setNormalPointer();
                }
            });
            packetPopup.add(changeColorMenuItem);

            //populateMenuItem = new JMenuItem("Populate From Current Channel");
            //populateMenuItem.addActionListener(this);
            //populateMenuItem.setActionCommand("populate-from-current-channel");
            //packetPopup.add(populateMenuItem);

            JMenuItem menuItem;
            final String[] plkeys = groups.keySet().toArray(new String[0]);
            Arrays.sort(plkeys);

            for (final String key:plkeys) {
                ArrayList<IndexLineSpec> tm = groups.get(key);
                JMenu submenu = new JMenu(key);
                packetmenu.add(submenu);

                final IndexLineSpec[] tmarray = tm.toArray(new IndexLineSpec[0]);
                Arrays.sort(tmarray);

                for (final IndexLineSpec pkt:tmarray) {
                    if (archivePanel.hideResponsePackets && pkt.lineName.contains("_Resp_")) continue;
                    menuItem = new JMenuItem(pkt.toString());
                    pkt.assocMenuItem = menuItem;
                    if (pkt.enabled) {
                        menuItem.setVisible(false);
                    }
                    menuItem.addActionListener(pkt);
                    submenu.add(menuItem);
                }

                // "All Packets" item
                submenu.addSeparator();
                menuItem = new JMenuItem("All Packets");
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        enableAllPackets(key);
                    }
                });
                submenu.add(menuItem);
            }
        }
    }

    void updatePrefsVisiblePackets()    {
        ArrayList<String> visiblePackets = new ArrayList<String>();
        for ( ArrayList<IndexLineSpec> plvec:groups.values()) {
            for (IndexLineSpec pkt:plvec) {
                if (pkt.enabled) {
                    visiblePackets.add(pkt.lineName);
                }
            }
        }
        PrefsObject.putObject(prefs, "indexLines", visiblePackets);
    }
    
    void resetSelection() {
        startX = -1;
        currentSelection = null;
        repaint();
        this.archivePanel.resetSelection();
    }

    public SelectionImpl getSelection() {
        return currentSelection;
    }

    void updateSelection(long start, long stop) {
        if (currentSelection == null) {
            currentSelection = this.archivePanel.new SelectionImpl(start, stop);
        } else {
            currentSelection.set(start, stop);
        }
        // set last mouse x/y coords
        startX = currentSelection.getStartX();
        stopX = currentSelection.getStopX();
        repaint();
    }

    //this happens when the mouse is pressed outside the telemetry bars
    @Override
    public void mousePressed(MouseEvent e) {
        selectedPacket=null;
        doMousePressed(e);
    }

    public void doMousePressed(MouseEvent e) {
        if (zoom!=null) {
            dragButton = e.getButton();
            if (dragButton == MouseEvent.BUTTON1) {
                if (this.archivePanel.replayEnabled && (e.getClickCount() == 2)) {
                    drawPreviewLocator = true;
                    previewLocatorAlpha = 0.8f;
                    previewLocatorX = e.getX();
                    this.archivePanel.seekReplay(previewLocator);
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
        if (e.isPopupTrigger()) {
            showPopup(e);    
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
  //      hidePopup(e);
        doMouseReleased(e);
    }

    public void doMouseReleased(MouseEvent e) {
        if (zoom!=null) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                if (currentSelection != null) {
                    // if only one line was selected, the user wants to deselect
                    if (startX == stopX) {
                        resetSelection();
                        archivePanel.updateSelectionFields(this);
                    } else {
                        // selection finished
                        archivePanel.selectionFinished(this);
                    }
                }
            }
        }
        if (e.isPopupTrigger()) {
            showPopup(e);    
        }
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
        doMouseDragged(e);

        // TTM does not show the tooltip in mouseDragged() so we send a MOUSE_MOVED event
        dispatchEvent(new MouseEvent(e.getComponent(), MouseEvent.MOUSE_MOVED, e.getWhen(), e.getModifiers(),
                e.getX(), e.getY(), e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }

    void doMouseDragged(MouseEvent e) {
        if (zoom!=null) {
            if (dragButton == MouseEvent.BUTTON1) {
                //final JViewport vp = tmscrollpane.getViewport();
                //stopX = Math.max(e.getX(), vp.getViewPosition().x);
                //stopX = Math.min(stopX, vp.getViewPosition().x + vp.getExtentSize().width - 1);
                stopX = e.getX() - deltaX;
                if (startX == -1) startX = stopX;
                if (currentSelection == null) {
                    currentSelection = this.archivePanel.new SelectionImpl(startX, stopX);
                } else {
                    currentSelection.set(startX, stopX);
                }
                setMouseLabel(e);
                setPointer(e);
                repaint();

                // this will trigger an update of selection start/stop text fields
                archivePanel.updateSelectionFields(this);
            }
        }
    }
    
    void showPopup(final MouseEvent e) {
        if (packetPopup != null) {
            if(selectedPacket!=null) {
                popupLabelItem.setVisible(true);
                removePayloadMenuItem.setVisible(true);
                removeExceptPacketMenuItem.setVisible(true);
                removePacketMenuItem.setVisible(true);
                copyOpsnameMenuItem.setVisible(true);
                changeColorMenuItem.setVisible(true);

                popupLabelItem.setText(selectedPacket.lineName);
                removePayloadMenuItem.setText(String.format("Remove All %s Packets", selectedPacket.grpName));
            } else {
                popupLabelItem.setVisible(false);
                removePayloadMenuItem.setVisible(false);
                removePacketMenuItem.setVisible(false);
                removeExceptPacketMenuItem.setVisible(false);
                copyOpsnameMenuItem.setVisible(false);
                changeColorMenuItem.setVisible(false);
            }
            packetPopup.validate();
            packetPopup.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    void setPointer(MouseEvent e) {
        if (this.archivePanel.reloadButton.isEnabled()) {
            this.archivePanel.setNormalPointer();
            if (currentSelection != null) {
                if (Math.abs(e.getX() - currentSelection.getStartX()) <= cursorSnap) {
                    this.archivePanel.setMoveLeftPointer();
                }
                if (Math.abs(e.getX() - currentSelection.getStopX()) <= cursorSnap) {
                    this.archivePanel.setMoveRightPointer();
                }
            }
        }
    }


    void enableAllPackets(String plname) {
        ArrayList<IndexLineSpec> pltm = groups.get(plname);
        if (pltm != null) {
            for (IndexLineSpec pkt:pltm) {
                if ( pkt.assocMenuItem != null ) {
                    // response packets might be hidden from the popup.
                    // in this case, assocMenuItem is not set.

                    // remove item from popup menu
                    pkt.assocMenuItem.setVisible(false);

                    // create entry in TM display
                    pkt.enabled = true;
                }
            }
            updatePrefsVisiblePackets();
            archivePanel.refreshTmDisplay();
        }
    }

    void enableTMPacket(IndexLineSpec pkt) {
        // remove item from popup menu
        pkt.assocMenuItem.setVisible(false);

        // create entry in TM display
        pkt.enabled = true;
        archivePanel.refreshTmDisplay();
        updatePrefsVisiblePackets();
    }

    void removeSelectedPacket() {
        archivePanel.setBusyPointer();
        selectedPacket.assocMenuItem.setVisible(true);
        selectedPacket.enabled = false;
        remove(selectedPacket.assocTmPanel);
        selectedPacket.assocTmPanel = null;
        updatePrefsVisiblePackets();
        if(getComponents().length==0) {
            showEmptyLabel("Right click for "+name+" data");
        }
    
        revalidate();
        repaint();
        archivePanel.setNormalPointer();
    }
    
    void removeGroupLines() {
        ArrayList<IndexLineSpec> pltm = groups.get(selectedPacket.grpName);
        if (pltm != null) {
            archivePanel.setBusyPointer();
            for (IndexLineSpec pkt:pltm) {
                if (pkt.assocMenuItem != null) {
                    pkt.assocMenuItem.setVisible(true);
                }
                pkt.enabled = false;
                if (pkt.assocTmPanel != null) {
                    remove(pkt.assocTmPanel);
                    pkt.assocTmPanel = null;
                }
            }
            if(getComponents().length==0) {
                showEmptyLabel("Right click for "+name+" data");
            }
            updatePrefsVisiblePackets();
            revalidate();
            repaint();
            archivePanel.setNormalPointer();
        }
    }

    void removeAllButThisLine() {
        archivePanel.setBusyPointer();
        for (ArrayList<IndexLineSpec> plvec:groups.values()) {
            for (IndexLineSpec pkt:plvec) {
                if (selectedPacket != pkt) {
                    if (pkt.assocMenuItem != null) {
                        pkt.assocMenuItem.setVisible(true);
                    }
                    pkt.enabled = false;
                    if (pkt.assocTmPanel != null) {
                        remove(pkt.assocTmPanel);
                        pkt.assocTmPanel = null;
                    }
                }
            }
        }
        updatePrefsVisiblePackets();
        revalidate();
        repaint();
        archivePanel.setNormalPointer();
    }


    @Override
    public Point getToolTipLocation(MouseEvent event) {
        return new Point(event.getX() - 94, event.getY() + 20);
    }

    String getMouseText(MouseEvent e) {
        if (zoom==null) {
            return null;
        }
        previewLocator = zoom.convertPixelToInstant(e.getX());
        return TimeEncoding.toCombinedFormat(previewLocator);
    }

    void setMouseLabel(MouseEvent e) {
        setToolTipText(getMouseText(e));
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

    public String getPacketsStatus() {
        // this appears in the "packets" status label

        StringBuffer txt = new StringBuffer();
        String tmp;
        for (String plname:groups.keySet()) {
            final ArrayList<IndexLineSpec> plvec = groups.get(plname);
            int count = 0;
            for (IndexLineSpec pkt:plvec) {
                if ( pkt.enabled ) ++count;
            }
            tmp = plname + " (" + count + "/" + plvec.size() + ")";
            if ( txt.length() > 0 ) txt.append(", ");
            txt.append(tmp);
        }
        if (txt.length() == 0) txt.append("(none)");
        return txt.toString();
    }

    public void setToZoom(ZoomSpec zoom) {
        this.zoom=zoom;
        removeIndexLines();
        packetPopup = null;
        if(groups.isEmpty()) {
            showEmptyLabel("No "+name+" data loaded");
        } else {
            boolean empty=true;
            for ( ArrayList<IndexLineSpec> plvec:groups.values()) {
                for (IndexLineSpec pkt: plvec) {
                    if (pkt.enabled) {
                        empty=false;
                        // create panel that contains the TM blocks
                        IndexLine panel = new IndexLine(this, pkt);
                        add(panel);
                        redrawTmPanel(pkt);
                    }
                }
            }
            if(empty) {
                showEmptyLabel("Right click for "+name+" data");
            }
            buildPopup();
        }
    }

    private void showEmptyLabel(String msg) {
        JLabel nodata=new JLabel(msg);
        Font f=new Font("SansSerif", Font.ITALIC, 20);
        nodata.setFont(f);
        nodata.setForeground(Color.lightGray);
        Box b=Box.createHorizontalBox();
        b.add(nodata);
        b.add(Box.createHorizontalGlue());
        add(b);
        nodata.addMouseListener(this);
    
    }
    public void receiveArchiveRecords(List<ArchiveRecord> records) {
        String[] nameparts;
       // System.out.println("received records: "+records);
        synchronized (tmData) {
            //progressMonitor.setProgress(30);
            //progressMonitor.setNote("Receiving data");

            for (ArchiveRecord r:records) {
                //debugLog(r.packet+"\t"+r.num+"\t"+new Date(r.first)+"\t"+new Date(r.last));
                NamedObjectId id=r.getId();
                String grpName=null;
                String shortName=null;
                //split the id into group->name
                if(!id.hasNamespace()) {
                    int idx=id.getName().lastIndexOf("/");
                    if(idx!=-1) {
                        grpName=id.getName().substring(0, idx+1);
                        shortName=id.getName().substring(idx+1);
                    }
                }
                if(grpName==null) {
                    nameparts = id.getName().split("[_\\.]", 2);
                    if(nameparts.length>1) {
                        grpName = nameparts[0];
                        shortName = nameparts[1].replaceFirst("INST_", "").replaceFirst("Tlm_Pkt_", "");
                    } else { 
                        grpName="";
                        shortName=id.getName();
                    }
                }
                if (!tmData.containsKey(id.getName())) {
                    tmData.put(id.getName(), new TreeSet<IndexChunkSpec>());
                }
                TreeSet<IndexChunkSpec> al=tmData.get(id.getName());
                String info=r.hasInfo()?r.getInfo():null;
                IndexChunkSpec tnew=new IndexChunkSpec(r.getFirst(), r.getLast(), r.getNum(), info);
                IndexChunkSpec told=al.floor(tnew);
                if((told==null) || (mergeTime==-1) || (!told.merge(tnew, mergeTime))) {
                    al.add(tnew);
                }
                if (!allPackets.containsKey(id.getName()) ) {
                    IndexLineSpec pkt = new IndexLineSpec(id.getName(), grpName, shortName);
                    allPackets.put(id.getName(), pkt);
                    ArrayList<IndexLineSpec> plvec;
                    if ( (plvec = groups.get(grpName)) == null ) {
                        plvec = new ArrayList<IndexLineSpec>();
                        groups.put(grpName, plvec);
                    }
                    plvec.add(pkt);
                }
            }
        }
    }
    
    
    
    public void startReloading() {
        allPackets.clear();
        groups.clear();
        tmData.clear();
    }
    
    public List<String> getSelectedPackets() {
        ArrayList<String> packets = new ArrayList<String>();
        for (ArrayList<IndexLineSpec> plvec: groups.values()) {
            for (IndexLineSpec pkt:plvec) {
                if ( pkt.enabled ) {
                    packets.add(pkt.lineName);
                }
            }
        }
        return packets;
    }

    public void dataLoadFinished() {
        Object o=PrefsObject.getObject(prefs, "indexLines");
        if(o==null) return;

        ArrayList<String> visibleLines=(ArrayList<String>)o;
        for (String linename:visibleLines) {
            IndexLineSpec pkt = allPackets.get(linename);
            if (pkt != null) {
                pkt.enabled = true;
            } else {
                ArchivePanel.debugLog("could not enable packet '" + linename + "', removing line from view");
            }
        }
   }
    
    void redrawTmPanel(IndexLineSpec pkt) {
        JComponent pktpanel = pkt.assocTmPanel;
        final int stopx = zoom.getPixels();
        final Insets in = pktpanel.getInsets();
        final int panelw = zoom.getPixels();
        JLabel pktlab;
        Font font = null;
        int x1, y = 0;//, count = 0;
        
        pktpanel.removeAll();

        //debugLog("redrawTmPanel() "+pkt.name+" mark 1");
        // set labels

        x1 = 10;
        do {
            pktlab = new JLabel(pkt.lineName);
            pktlab.setForeground(pkt.color);
            if (font == null) {
                font = pktlab.getFont();
                font = font.deriveFont((float)(font.getSize() - 3));
            }
            pktlab.setFont(font);
            pktlab.setBounds(x1 + in.left, in.top, pktlab.getPreferredSize().width, pktlab.getPreferredSize().height);
            pktpanel.add(pktlab);

            if (y == 0) {
                y = in.top + pktlab.getSize().height;
                pktpanel.setPreferredSize(new Dimension(panelw, y + tmRowHeight + in.bottom)); 
                pktpanel.setMinimumSize(pktpanel.getPreferredSize());
                pktpanel.setMaximumSize(pktpanel.getPreferredSize());
            }
            x1 += 600;
        } while (x1 < panelw - pktlab.getSize().width);

        TreeSet<IndexChunkSpec> ts=tmData.get(pkt.lineName);
        if(ts!=null) {
            Timeline tmt=new Timeline(this, pkt.color, ts,zoom, in.left);
            tmt.setBounds(in.left,y,stopx, tmRowHeight);
            pktpanel.add(tmt);
        }
     
      //  pktpanel.setPreferredSize(new Dimension(zoom.getPixels(), pktpanel.getPreferredSize().height));
        pktpanel.revalidate();
        pktpanel.repaint();

        //pkt.assocLabel.setToolTipText(pkt.opsname + ", " + count + " Chunks");
    }

    class IndexLineSpec implements Comparable<IndexLineSpec>, ActionListener {
        String shortName, lineName;
        String grpName;
        boolean enabled;
        Color color;
        JMenuItem assocMenuItem;
        JComponent assocLabel;
        JComponent assocTmPanel;

        public IndexLineSpec(String lineName, String grpName, String shortName) {
            this.lineName = lineName;
            this.grpName = grpName;
            this.shortName = shortName;
            enabled = false;
            assocMenuItem = null;
            assocTmPanel = null;
            assocLabel = null;
            newColor();
        }

        public void newColor() {
            color = new Color((float)(Math.random() * 0.4 + 0.2), (float)(Math.random() * 0.4 + 0.2), (float)(Math.random() * 0.4 + 0.2));
        }

        @Override
        public String toString() {
            return shortName;
        }

        @Override
        public int compareTo(IndexLineSpec o) {
            return shortName.compareTo(o.shortName);
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            enableTMPacket(this);
        }
    }

    public void setMergeTime(long mt) {
        this.mergeTime=mt;
    }
}