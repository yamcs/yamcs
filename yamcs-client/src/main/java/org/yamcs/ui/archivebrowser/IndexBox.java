package org.yamcs.ui.archivebrowser;

import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.ui.PrefsObject;
import org.yamcs.ui.UiColors;
import org.yamcs.ui.archivebrowser.ArchivePanel.IndexChunkSpec;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Represents a collection of IndexLine shown vertically 
 * @author nm
 *
 */
public class IndexBox extends Box implements MouseListener {
    private static final long serialVersionUID = 1L;
    DataView dataView;
    
    JLabel popupLabelItem;
    JSeparator popupLabelSeparator;
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
    
    private JPanel topPanel;
    private JPanel centerPanel;
    private List<IndexLine> indexLines = new ArrayList<IndexLine>();
    
    private JLabel titleLabel;
    
    IndexBox(DataView dataView, String name) {
        super(BoxLayout.Y_AXIS);
        topPanel = new JPanel(new GridBagLayout()); // In panel, so that border can fill width
        Border outsideBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.BORDER_COLOR);
        Border insideBorder = BorderFactory.createEmptyBorder(10, 0, 2, 0);
        topPanel.setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));
        topPanel.setBackground(Color.WHITE);
        
        GridBagConstraints cons = new GridBagConstraints();
        cons.fill = GridBagConstraints.HORIZONTAL;
        cons.weightx = 1;
        cons.gridx = 0;
        titleLabel = new JLabel(name);
        titleLabel.setBackground(Color.red);
        titleLabel.setMaximumSize(new Dimension(titleLabel.getMaximumSize().width, titleLabel.getPreferredSize().height));
        titleLabel.setForeground(new Color(51, 51, 51));
        topPanel.setMaximumSize(new Dimension(topPanel.getMaximumSize().width, titleLabel.getPreferredSize().height+13));
        topPanel.add(titleLabel, cons);
        topPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(topPanel);
        
        centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder());
        centerPanel.setOpaque(false);
        centerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
       
        add(centerPanel);

        addMouseListener(this);

        this.dataView=dataView;
        this.name=name;

        allPackets = new HashMap<String,IndexLineSpec>();
        groups = new HashMap<String,ArrayList<IndexLineSpec>>();
        tmData = new HashMap<String,TreeSet<IndexChunkSpec>>();
        prefs = Preferences.userNodeForPackage(IndexBox.class).node(name);
    }

    void removeIndexLines() {
        centerPanel.removeAll();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        g2d.setPaint(new GradientPaint(0, topPanel.getHeight(), new Color(251,251,251), 0, panelHeight, Color.WHITE));
        g2d.fillRect(0, topPanel.getHeight(), panelWidth, panelHeight-topPanel.getHeight());
    }

    protected void buildPopup() {
        if (groups.isEmpty()) {
            packetPopup = null;
        } else {
            packetPopup = new JPopupMenu();

            popupLabelItem = new JLabel();
            popupLabelItem.setEnabled(false);
            Box hbox = Box.createHorizontalBox();
            hbox.add(Box.createHorizontalGlue());
            hbox.add(popupLabelItem);
            hbox.add(Box.createHorizontalGlue());
            packetPopup.insert(hbox, 0);

            popupLabelSeparator = new JSeparator();
            packetPopup.add(popupLabelSeparator);

            JMenu packetmenu = new JMenu("Add Packets");
            packetPopup.add(packetmenu);

            removePacketMenuItem = new JMenuItem(); // text is set when showing the popup menu
            removePacketMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    removeSelectedPacket();
                }
            });
            packetPopup.add(removePacketMenuItem);

            removeExceptPacketMenuItem = new JMenuItem("Hide Other Packets");
            removeExceptPacketMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    removeAllButThisLine();
                }
            });
            packetPopup.add(removeExceptPacketMenuItem);

            removePayloadMenuItem = new JMenuItem(); // text is set when showing the popup menu
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
                    dataView.setBusyPointer();
                    redrawTmPanel(selectedPacket);
                    dataView.setNormalPointer();
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
                    if (dataView.hideResponsePackets && pkt.lineName.contains("_Resp_")) continue;
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

    @Override
    public void mousePressed(MouseEvent e) {
        selectedPacket=null;
        if(e.isPopupTrigger()) {
            showPopup(e);
        }
    }

    public void doMouseReleased(MouseEvent e) {
        if(e.isPopupTrigger()) {
            showPopup(e);
        }
    }

    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {
        doMouseReleased(e);
    }

    void showPopup(final MouseEvent e) {
        if (packetPopup != null) {
            if(selectedPacket!=null) {
                popupLabelItem.setVisible(true);
                popupLabelSeparator.setVisible(true);
                removePayloadMenuItem.setVisible(true);
                removeExceptPacketMenuItem.setVisible(true);
                removePacketMenuItem.setVisible(true);
                copyOpsnameMenuItem.setVisible(true);
                changeColorMenuItem.setVisible(true);

                popupLabelItem.setText(selectedPacket.lineName);
                removePacketMenuItem.setText(String.format("Hide %s Packet", selectedPacket.lineName));
                removePayloadMenuItem.setText(String.format("Hide All %s Packets", selectedPacket.grpName));
            } else {
                popupLabelItem.setVisible(false);
                popupLabelSeparator.setVisible(false);
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
            dataView.refreshDisplay();
        }
    }

    void enableTMPacket(IndexLineSpec pkt) {
        // remove item from popup menu
        pkt.assocMenuItem.setVisible(false);

        // create entry in TM display
        pkt.enabled = true;
        dataView.refreshDisplay();
        updatePrefsVisiblePackets();
    }

    void removeSelectedPacket() {
        dataView.setBusyPointer();
        selectedPacket.assocMenuItem.setVisible(true);
        selectedPacket.enabled = false;
        remove(selectedPacket.assocIndexLine);
        selectedPacket.assocIndexLine = null;
        updatePrefsVisiblePackets();
        if(getComponents().length==0) {
            showEmptyLabel("Right click for "+name+" data");
        }
    
        dataView.refreshDisplay();
        dataView.setNormalPointer();
    }
    
    void removeGroupLines() {
        ArrayList<IndexLineSpec> pltm = groups.get(selectedPacket.grpName);
        if (pltm != null) {
            dataView.setBusyPointer();
            for (IndexLineSpec pkt:pltm) {
                if (pkt.assocMenuItem != null) {
                    pkt.assocMenuItem.setVisible(true);
                }
                pkt.enabled = false;
                if (pkt.assocIndexLine != null) {
                    remove(pkt.assocIndexLine);
                    pkt.assocIndexLine = null;
                }
            }
            if(getComponents().length==0) {
                showEmptyLabel("Right click for "+name+" data");
            }
            updatePrefsVisiblePackets();
            dataView.refreshDisplay();
            dataView.setNormalPointer();
        }
    }

    void removeAllButThisLine() {
        dataView.setBusyPointer();
        for (ArrayList<IndexLineSpec> plvec:groups.values()) {
            for (IndexLineSpec pkt:plvec) {
                if (selectedPacket != pkt) {
                    if (pkt.assocMenuItem != null) {
                        pkt.assocMenuItem.setVisible(true);
                    }
                    pkt.enabled = false;
                    if (pkt.assocIndexLine != null) {
                        remove(pkt.assocIndexLine);
                        pkt.assocIndexLine = null;
                    }
                }
            }
        }
        updatePrefsVisiblePackets();
        dataView.refreshDisplay();
        dataView.setNormalPointer();
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
                        // create panel that contains the index blocks
                        IndexLine line = new IndexLine(this, pkt);
                        
                        centerPanel.add(line);
                        indexLines.add(line);
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
        nodata.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, nodata.getFont().getSize()));
        nodata.setForeground(Color.lightGray);
        nodata.addMouseListener(this);
        Box b=Box.createHorizontalBox();
        b.setBorder(BorderFactory.createEmptyBorder());
        b.add(nodata);
        b.add(Box.createHorizontalGlue());
        b.setMaximumSize(new Dimension(b.getMaximumSize().width, b.getPreferredSize().height));
        centerPanel.add(b);
    }

    public void receiveArchiveRecords(List<ArchiveRecord> records) {
        String[] nameparts;
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
            titleLabel.setText(name+" "+getPacketsStatus());
        }
    }

    public void startReloading() {
        allPackets.clear();
        groups.clear();
        tmData.clear();
    }
    
    public List<String> getPacketsForSelection(Selection selection) {
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
        IndexLine indexLine = pkt.assocIndexLine;
        indexLine.setOpaque(false);
        final int stopx = zoom.getPixels();
        final Insets in = indexLine.getInsets();
        final int panelw = zoom.getPixels();
        JLabel pktlab;
        Font font = null;
        int x1, y = 0;//, count = 0;
        
        indexLine.removeAll();

        //debugLog("redrawTmPanel() "+pkt.name+" mark 1");
        // set labels
        x1 = 10;
        indexLine.setBackground(Color.RED);
        do {            
            pktlab = new JLabel(pkt.lineName);
            pktlab.setForeground(pkt.color);
            if (font == null) {
                font = pktlab.getFont();
                font = font.deriveFont((float)(font.getSize() - 3));
            }
            pktlab.setFont(font);
            pktlab.setBounds(x1 + in.left, in.top, pktlab.getPreferredSize().width, pktlab.getPreferredSize().height);
            indexLine.add(pktlab);

            if (y == 0) {
                y = in.top + pktlab.getSize().height;
                indexLine.setPreferredSize(new Dimension(panelw, y + tmRowHeight + in.bottom));
                indexLine.setMinimumSize(indexLine.getPreferredSize());
                indexLine.setMaximumSize(indexLine.getPreferredSize());
            }
            x1 += 600;
        } while (x1 < panelw - pktlab.getSize().width);

        TreeSet<IndexChunkSpec> ts=tmData.get(pkt.lineName);
        if(ts!=null) {
            Timeline tmt=new Timeline(this, pkt, ts,zoom, in.left);
            tmt.setBounds(in.left,y,stopx, tmRowHeight);
            indexLine.add(tmt);
        }

     //   centerPanel.setPreferredSize(new Dimension(panelw, centerPanel.getPreferredSize().height));
       // centerPanel.setMaximumSize(centerPanel.getPreferredSize());

        indexLine.revalidate();
        indexLine.repaint();

      //  System.out.println("indexLine.preferred size: "+indexLine.getPreferredSize());
        //pkt.assocLabel.setToolTipText(pkt.opsname + ", " + count + " Chunks");
    }

    class IndexLineSpec implements Comparable<IndexLineSpec>, ActionListener {
        String shortName, lineName;
        String grpName;
        boolean enabled;
        Color color;
        JMenuItem assocMenuItem;
        JComponent assocLabel;
        IndexLine assocIndexLine;

        public IndexLineSpec(String lineName, String grpName, String shortName) {
            this.lineName = lineName;
            this.grpName = grpName;
            this.shortName = shortName;
            enabled = false;
            assocMenuItem = null;
            assocIndexLine = null;
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