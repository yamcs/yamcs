package org.yamcs.ui.archivebrowser;

import org.yamcs.ui.archivebrowser.ArchivePanel.IndexChunkSpec;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.ui.archivebrowser.IndexBox.IndexLineSpec;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.TreeSet;

class Timeline extends JPanel implements MouseListener {
    private static final long serialVersionUID = 1L;
    private final IndexBox tmBox;
    TreeSet<IndexChunkSpec> tmspec;
    IndexLineSpec pkt;
    Color color;
    ZoomSpec zoom;
    int leftDelta; //we have to move everything to the left with this amount (because this component is in a bordered parent)
    BufferedImage image=null;
    
    Timeline(IndexBox tmBox, IndexLineSpec pkt, TreeSet<IndexChunkSpec> tmspec, ZoomSpec zoom, int leftDelta) {
        super();
        setBorder(BorderFactory.createEmptyBorder());
        this.tmBox = tmBox;
        this.pkt = pkt;
        this.color=pkt.color;
        this.zoom=zoom;
        this.leftDelta=leftDelta;
        addMouseListener(this);
        setOpaque(false);

        this.tmspec = tmspec;
    }

    @Override
    public Point getToolTipLocation(MouseEvent e) {
        return tmBox.getToolTipLocation(e);
    }

    private MouseEvent translateEvent(MouseEvent e, Component dest)	 {
        // workaround for this bug
        //http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7181403
        MouseEvent me=SwingUtilities.convertMouseEvent(e.getComponent(), e, dest);
        return new MouseEvent(me.getComponent(), me.getID(), me.getWhen(), me.getModifiers(), me.getX(), me.getY(), me.getXOnScreen(), me.getYOnScreen(), me.getClickCount(), me.isPopupTrigger(), e.getButton());
    }

    @Override public void mousePressed(MouseEvent e) {
        if(e.isPopupTrigger()) {
            tmBox.selectedPacket = pkt;
            tmBox.showPopup(translateEvent(e, tmBox));
        }
    }

    @Override public void mouseReleased(MouseEvent e) {
        if(e.isPopupTrigger()) {
            tmBox.selectedPacket = pkt;
            tmBox.showPopup(translateEvent(e, tmBox));
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {
        MouseEvent transEvent = translateEvent(e, tmBox);
        setToolTipText(TimeEncoding.toCombinedFormat(tmBox.dataView.getMouseInstant(transEvent)));
    }

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public String getToolTipText(MouseEvent e) {
        String tt=TimeEncoding.toCombinedFormat(tmBox.dataView.getMouseInstant(translateEvent(e, tmBox)));

        IndexChunkSpec c1=zoom.convertPixelToChunk(translateEvent(e, tmBox).getX());
        IndexChunkSpec chunk=tmspec.floor(c1);
        if((chunk==null)||chunk.stopInstant<c1.startInstant) {
            chunk=tmspec.ceiling(c1);
            if((chunk==null) || (chunk.startInstant>c1.stopInstant)) return tt;
        }


        DateTimeComponents dtcStart=TimeEncoding.toUtc(chunk.startInstant);
        DateTimeComponents dtcStop=TimeEncoding.toUtc(chunk.stopInstant);


        String timestring = (dtcStart.year == dtcStop.year) &&
        (dtcStart.doy == dtcStop.doy) ?
                String.format("%s - %s", TimeEncoding.toCombinedFormat(chunk.startInstant), dtcStop.toIso8860String()) :
                    String.format("%s - %s", TimeEncoding.toCombinedFormat(chunk.startInstant), TimeEncoding.toCombinedFormat(chunk.stopInstant));
    
        StringBuilder sb=new StringBuilder();
        sb.append("<html>").append(tt).append("<hr>Index Record: ").append(chunk.tmcount).append(" Packets");
        if(chunk.tmcount>1) sb.append(" @ ").append(chunk.getFrequency()).append("Hz");
        sb.append("<br>").append(timestring);
        if(chunk.info!=null) sb.append("<br>").append(chunk.info);
        sb.append("</html>");
        return sb.toString();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(image==null) {
            image=new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D big=image.createGraphics();
            big.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            big.fillRect(0,0,getWidth(),getHeight());

            big.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
            //big.clearRect(0, 0, getWidth(),getHeight());
            big.setColor(color);
            for(IndexChunkSpec pkt:tmspec) {
                int x1 = zoom.convertInstantToPixel(pkt.startInstant);
                int x2 = zoom.convertInstantToPixel(pkt.stopInstant);
                int width=(x2 - x1 <= 1) ? 1 : x2 - x1 - 1;
                big.fillRect(x1-leftDelta, 0, width, getHeight());
            }		
        } 
        g.drawImage(image,0,0,this);
    }

}