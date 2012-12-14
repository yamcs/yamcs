/**
 * 
 */
package org.yamcs.ui.archivebrowser;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.TreeSet;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputListener;

import org.orekit.time.DateTimeComponents;
import org.yamcs.ui.archivebrowser.ArchivePanel.IndexChunkSpec;
import org.yamcs.ui.archivebrowser.ArchivePanel.ZoomSpec;

import org.yamcs.utils.TimeEncoding;

class Timeline extends JComponent implements MouseInputListener {
    private static final long serialVersionUID = 1L;
    private final IndexBox tmBox;
    TreeSet<IndexChunkSpec> tmspec;
    Color color;
    ZoomSpec zoom;
    int leftDelta; //we have to move everything to the left with this amount (because this component is in a bordered parent)
    BufferedImage image=null;

    Timeline(IndexBox tmBox, Color color, TreeSet<IndexChunkSpec> tmspec, ZoomSpec zoom, int leftDelta) {
        super();
        this.tmBox = tmBox;
        this.color=color;
        this.zoom=zoom;
        this.leftDelta=leftDelta;
        //	super(null, false);
        //setBackground(color);
        //setOpaque(true);
        addMouseMotionListener(this);
        addMouseListener(this);

        this.tmspec = tmspec;
    }

    @Override
    public Point getToolTipLocation(MouseEvent e) {
        return tmBox.getToolTipLocation(e);
    }

    private MouseEvent translateEvent(MouseEvent e, Component dest)	{
        return SwingUtilities.convertMouseEvent(e.getComponent(), e, dest);
    }

    public void mousePressed(MouseEvent e) {
        getParent().dispatchEvent(translateEvent(e, getParent()));
    }

    public void mouseReleased(MouseEvent e) {
        getParent().dispatchEvent(translateEvent(e, getParent()));
    }

    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}

    public void mouseMoved(MouseEvent e) {
        MouseEvent transEvent = translateEvent(e, tmBox);
        setToolTipText(tmBox.getMouseText(transEvent));
        tmBox.setPointer(transEvent);
    }

    public void mouseDragged(MouseEvent e) {
        tmBox.doMouseDragged(translateEvent(e, tmBox));

        // TTM does not show the tooltip in mouseDragged() so we send a MOUSE_MOVED event
        dispatchEvent(new MouseEvent(e.getComponent(), MouseEvent.MOUSE_MOVED, e.getWhen(), e.getModifiers(),
                e.getX(), e.getY(), e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }
    
    @Override
    public String getToolTipText(MouseEvent e) {
        String tt=tmBox.getMouseText(translateEvent(e, tmBox));

        IndexChunkSpec c1=zoom.convertPixelToChunk(translateEvent(e, tmBox).getX());
        IndexChunkSpec chunk=tmspec.floor(c1);
        if((chunk==null)||chunk.stopInstant<c1.startInstant) {
            chunk=tmspec.ceiling(c1);
            if((chunk==null) || (chunk.startInstant>c1.stopInstant)) return tt;
        }


        DateTimeComponents dtcStart=TimeEncoding.getComponents(chunk.startInstant);
        DateTimeComponents dtcStop=TimeEncoding.getComponents(chunk.stopInstant);


        String timestring = (dtcStart.getDate().getYear() == dtcStop.getDate().getYear()) &&
        (dtcStart.getDate().getDayOfYear() == dtcStop.getDate().getDayOfYear()) ?
                String.format("%s - %s", TimeEncoding.toCombinedFormat(chunk.startInstant), dtcStop.getTime().toString()) :
                    String.format("%s - %s", TimeEncoding.toCombinedFormat(chunk.startInstant), TimeEncoding.toCombinedFormat(chunk.stopInstant));
    
        StringBuilder sb=new StringBuilder();
        sb.append("<html>").append(tt).append("<hr>Index Record: ").append(chunk.tmcount).append(" Packets");
        if(chunk.tmcount>1) sb.append(" @ ").append(chunk.getFrequency());
        sb.append("<br>").append(timestring);
        if(chunk.info!=null) sb.append("<br>").append(chunk.info);
        sb.append("</html>");
        return sb.toString();
    }

    @Override
    public void paint(Graphics g) {

        if(image==null) {
            image=(BufferedImage)createImage(getWidth(),getHeight());
            Graphics2D big=image.createGraphics();
            big.setBackground(getBackground());
            big.clearRect(0, 0, getWidth(),getHeight());
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