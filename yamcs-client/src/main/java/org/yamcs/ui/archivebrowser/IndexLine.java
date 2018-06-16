package org.yamcs.ui.archivebrowser;

import org.yamcs.ui.archivebrowser.IndexBox.IndexLineSpec;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;


/**
 * Represents a horizontal TM line composed of a label and a timeline
 */
class IndexLine extends JPanel implements MouseInputListener {
    private final IndexBox indexBox;
    private static final long serialVersionUID = 1L;
    IndexLineSpec pkt;

    IndexLine(IndexBox tmBox, IndexLineSpec pkt) {
        super(null, false);
        this.indexBox = tmBox;
        this.pkt = pkt;
        pkt.assocIndexLine = this;
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder());
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    @Override
    public Point getToolTipLocation(MouseEvent e) {
        return indexBox.getToolTipLocation(e);
    }

    private MouseEvent translateEvent(MouseEvent e) {
        // workaround for this bug
        //http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7181403
        MouseEvent me=SwingUtilities.convertMouseEvent(e.getComponent(), e, indexBox);
        return new MouseEvent(me.getComponent(), me.getID(), me.getWhen(), me.getModifiers(), me.getX(), me.getY(), me.getXOnScreen(), me.getYOnScreen(), me.getClickCount(), me.isPopupTrigger(), e.getButton());
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            indexBox.selectedPacket = pkt;
            indexBox.showPopup(translateEvent(e));    
        }        
        ///tmBox.dataView.doMousePressed(translateEvent(e));
        /*
            Action postTip = getActionMap().get("postTip");
debugLog("tmpanel postTip src "+e.getSource()+" this "+this+" actionmap "+getActionMap().size()+" inputmap "+getInputMap(0).size());
            if (postTip != null) {
debugLog("tmpanel postTip");
                postTip.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, ""));
            }
         */
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    	// Cross-platform popups
    	if (e.isPopupTrigger()) {
            indexBox.selectedPacket = pkt;
            indexBox.showPopup(translateEvent(e));    
        }
        indexBox.doMouseReleased(translateEvent(e));
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        indexBox.dispatchEvent(translateEvent(e));
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
    
    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseMoved(MouseEvent e) {
        MouseEvent transEvent = translateEvent(e);
        setToolTipText(TimeEncoding.toCombinedFormat(indexBox.dataView.getMouseInstant(transEvent)));
        indexBox.dataView.setPointer(transEvent);
    }

    @Override
    public void mouseDragged(MouseEvent e) {}
}