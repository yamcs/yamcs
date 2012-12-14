/**
 * 
 */
package org.yamcs.ui.archivebrowser;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputListener;

import org.yamcs.ui.archivebrowser.ArchivePanel.ZoomSpec;


import org.yamcs.utils.TimeEncoding;
import org.yamcs.protobuf.Yamcs.ArchiveTag;

public class TagTimeline extends JComponent implements MouseInputListener {
    private static final long serialVersionUID = 1L;
    private final TagBox tagBox;
    List<ArchiveTag> tags;
    ZoomSpec zoom;
    int leftDelta;
    BufferedImage image=null;
    int row;
    
    TagTimeline(TagBox tagBox, List<ArchiveTag> tags, ZoomSpec zoom, int row, int leftDelta) {
        super();
        this.tagBox = tagBox;
        this.zoom=zoom;
        this.tags = tags;
        this.row=row;
        this.leftDelta=leftDelta;
        JLabel l=new JLabel("X");
        setMinimumSize(new Dimension(0,2+l.getPreferredSize().height));
        setPreferredSize(getMinimumSize());
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 2+l.getPreferredSize().height));
        addMouseMotionListener(this);
        addMouseListener(this);
        
    }

    @Override
    public Point getToolTipLocation(MouseEvent e) {
        return tagBox.getToolTipLocation(e);
    }

    private MouseEvent translateEvent(MouseEvent e, Component dest)	{
        return SwingUtilities.convertMouseEvent(e.getComponent(), e, dest);
    }

    public void mousePressed(MouseEvent e) {
        long t=zoom.convertPixelToInstant(e.getX());
        int index=time2Tag(tags,t);
        tagBox.doMousePressed(translateEvent(e, tagBox), row, index);
    }

    public void mouseReleased(MouseEvent e) {
        getParent().dispatchEvent(translateEvent(e, getParent()));
    }

    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}

    ArchiveTag lastMouseTag=null;
    public void mouseMoved(MouseEvent e) {
        long t=zoom.convertPixelToInstant(e.getX()+leftDelta);
        int index=time2Tag(tags, t);
        ArchiveTag at=null;
        if(index!=-1) at=tags.get(index);
        if(at==lastMouseTag)return;
        lastMouseTag=at;
        if(at!=null) {
            StringBuilder sb=new StringBuilder();
            sb.append("<html>").append(at.getName()).append("<hr>");
            if(at.hasStart()) {
                sb.append("Start: ").append(TimeEncoding.toString(at.getStart())).append("<br>");
            }
            if(at.hasStop()) {
                sb.append("Stop: ").append(TimeEncoding.toString(at.getStop())).append("<br>");
            }
            if(at.hasDescription()) {
                sb.append(at.getDescription());
            }
            sb.append("</html>");
            setToolTipText(sb.toString());
        } else {
            setToolTipText(null);
        }
    }

    static int time2Tag(List<ArchiveTag> tagList, long t) {
        int min=0;
        int max=tagList.size()-1;
        int mid;
        while(min<=max) {
            mid=(min+max)>>1;
            ArchiveTag atmid=tagList.get(mid);
            if(!atmid.hasStart() || atmid.getStart()<=t) {
                if(!atmid.hasStop() || atmid.getStop()>=t) {
                    return mid;
                } else {
                    min=mid+1;
                }
            } else {
                max=mid-1;
            }
        }
        return -1;
    }
    
    
    public void mouseDragged(MouseEvent e) {
        // TTM does not show the tooltip in mouseDragged() so we send a MOUSE_MOVED event
        dispatchEvent(new MouseEvent(e.getComponent(), MouseEvent.MOUSE_MOVED, e.getWhen(), e.getModifiers(),
                e.getX(), e.getY(), e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }
    
    @Override
    public void paint(Graphics g) {
        if(image==null) {
            image=(BufferedImage)createImage(getWidth(),getHeight());
            Graphics2D big=image.createGraphics();
            big.setBackground(getBackground());
            big.clearRect(0, 0, getWidth(),getHeight());
            for(ArchiveTag at:tags) {
                Color bgcolor,fgcolor;
                if(at.hasColor()){
                    bgcolor=ColorUtils.getColor(at.getColor());
                    fgcolor=ColorUtils.getOpposite(at.getColor());
                } else {
                    bgcolor=Color.white;
                    fgcolor=Color.black;
                }
                big.setColor(bgcolor);
                long start=(at.hasStart())?at.getStart():zoom.startInstant;
                int x1 = zoom.convertInstantToPixel(start);
                
                long stop=(at.hasStop())?at.getStop():zoom.stopInstant;
                int x2 = zoom.convertInstantToPixel(stop);
                if(x1<=0 && x2<0 )continue;
                
                if(x1<0) x1=0;
                
                int width=(x2 - x1 <= 1) ? 1 : x2 - x1 - 1;
                big.fillRect(x1-leftDelta, 0, width, getHeight());
                big.setColor(fgcolor);
                Font f=big.getFont();
                Rectangle2D bounds=f.getStringBounds(at.getName(), big.getFontRenderContext());
                if(width>bounds.getWidth()) {
                    LineMetrics lm=f.getLineMetrics(at.getName(), big.getFontRenderContext());
                    big.drawString(at.getName(), x1-leftDelta+1, (int)lm.getAscent()+1);
                }
                big.setColor(Color.black);
                big.drawRect(x1-leftDelta, 0, width-1, getHeight()-1);
            }
          //  border.paintBorder(this, big, 0, 0, getWidth(),getHeight() );
        } 
        
        g.drawImage(image,0,0,this);
        
    }

}