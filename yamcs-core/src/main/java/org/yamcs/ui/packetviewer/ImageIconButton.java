package org.yamcs.ui.packetviewer;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;

/**
 * Image that looks and acts like a button, but only when mouse events are
 * performed.
 */
public class ImageIconButton extends JLabel implements MouseListener {

    private static final long serialVersionUID = 1L;
    private static final Border EMPTY_BORDER = BorderFactory.createEmptyBorder(2, 2, 2, 2);
    private static final Border HOVER_BORDER = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
    private static final Border PRESSED_BORDER = BorderFactory.createEtchedBorder(EtchedBorder.RAISED);
    private Set<ActionListener> actionListeners = new HashSet<ActionListener>();

    public ImageIconButton(Icon image) {
        super(image);
        setBorder(EMPTY_BORDER);
        addMouseListener(this);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        setBorder(HOVER_BORDER);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        setBorder(EMPTY_BORDER);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        setBorder(EMPTY_BORDER);
        if (contains(e.getPoint())) {
            for (ActionListener al : actionListeners) {
                al.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        setBorder(PRESSED_BORDER);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // Ignore, because this does not register clicks when pressing and
        // releasing in the same component, but on a different location
    }

    public void addActionListener(ActionListener al) {
        actionListeners.add(al);
    }

    public void removeActionListener(ActionListener al) {
        actionListeners.remove(al);
    }
}
