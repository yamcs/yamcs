package org.yamcs.ui.archivebrowser;

import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MouseInputListener;

import org.yamcs.ui.archivebrowser.IndexBox.IndexLineSpec;


/**
 * Represents a horizontal TM line composed of a label and a timeline
 * 
 *
 */
class IndexLine extends JPanel implements MouseInputListener {
    private final IndexBox tmBox;
    private static final long serialVersionUID = 1L;
    IndexLineSpec pkt;

    IndexLine(IndexBox tmBox, IndexLineSpec pkt) {
        super(null, false);
        this.tmBox = tmBox;
        this.pkt = pkt;
        pkt.assocTmPanel = this;
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(new EmptyBorder(new Insets(0, 0, 0, 0)));
        setBackground(new Color(237, 237, 237)); // TODO should not be needed?
        addMouseMotionListener(this);
        addMouseListener(this);
    }

    @Override
    public Point getToolTipLocation(MouseEvent e) {
        return tmBox.getToolTipLocation(e);
    }

    private MouseEvent translateEvent(MouseEvent e) {
        // workaround for this bug
        //http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7181403
        MouseEvent me=SwingUtilities.convertMouseEvent(e.getComponent(), e, tmBox);
        return new MouseEvent(me.getComponent(), me.getID(), me.getWhen(), me.getModifiers(), me.getX(), me.getY(), me.getXOnScreen(), me.getYOnScreen(), me.getClickCount(), me.isPopupTrigger(), e.getButton());
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            tmBox.selectedPacket = pkt;
            tmBox.showPopup(translateEvent(e));    
        }        
        tmBox.doMousePressed(translateEvent(e));
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
            tmBox.selectedPacket = pkt;
            tmBox.showPopup(translateEvent(e));    
        }
        tmBox.doMouseReleased(translateEvent(e));
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        tmBox.dispatchEvent(translateEvent(e));
    }

    @Override
    public void mouseExited(MouseEvent e) {}
    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseMoved(MouseEvent e) {
        MouseEvent transEvent = translateEvent(e);
        setToolTipText(tmBox.getMouseText(transEvent));
        tmBox.setPointer(transEvent);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        tmBox.doMouseDragged(translateEvent(e));

        // TTM does not show the tooltip in mouseDragged() so we send a MOUSE_MOVED event
        dispatchEvent(new MouseEvent(e.getComponent(), MouseEvent.MOUSE_MOVED, e.getWhen(), e.getModifiers(),
                e.getX(), e.getY(), e.getClickCount(), e.isPopupTrigger(), e.getButton()));
    }
}