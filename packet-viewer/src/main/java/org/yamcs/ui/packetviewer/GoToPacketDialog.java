package org.yamcs.ui.packetviewer;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;


/**
 * Dialog for jumping to a specific packet in the packet table.
 */
public class GoToPacketDialog extends JDialog implements ActionListener {
    private static final long serialVersionUID = 1L;
    private static final KeyStroke KEY_ESC = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);

    /**
     * Return value if cancel is chosen.
     */
    public static final int CANCEL_OPTION = 1;

    /**
     * Return value if approve (yes, ok) is chosen.
     */
    public static final int APPROVE_OPTION = 0;

    private JTextField lineNumberField;
    private int returnValue;
    private int lineNumber;

    private PacketsTable packetsTable; 

    public GoToPacketDialog(PacketsTable packetsTable) {
        super(getJFrameContainer(packetsTable), "Go to Packet", true);

        this.packetsTable = packetsTable;

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        inputPanel.add(new JLabel("Packet number: "), gbc);

        lineNumberField = new JTextField(10);
        inputPanel.add(lineNumberField, gbc);

        JPanel buttonPanel = new JPanel();

        JButton button = new JButton("Go");
        button.setActionCommand("go");
        button.addActionListener(this);
        getRootPane().setDefaultButton(button);
        buttonPanel.add(button, gbc);

        button = new JButton("Cancel");
        button.setActionCommand("cancel");
        button.addActionListener(this);
        buttonPanel.add(button, gbc);

        getContentPane().add(inputPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        setMinimumSize(new Dimension(350, 150));
        installActions();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private static JFrame getJFrameContainer(JComponent component) {
        Container parent = component.getParent();
        if (parent instanceof JFrame)
            return (JFrame) parent;
        else
            return getJFrameContainer((JComponent) parent);
    }

    private void installActions() {
        // Close dialog with escape
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KEY_ESC, "close-dialog");

        getRootPane().getActionMap().put("close-dialog", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                dispatchEvent(new WindowEvent(GoToPacketDialog.this,
                        WindowEvent.WINDOW_CLOSING));
            }
        });
    }

    public int showDialog() {
        // Prevent caching of last returnValue
        returnValue = CANCEL_OPTION;
        lineNumberField.selectAll();
        setLocationRelativeTo(getParent());
        setVisible(true);
        return returnValue;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if ("go".equals(cmd)) { 
            try {
                lineNumber = Integer.parseInt(lineNumberField.getText());
            } catch (NumberFormatException x) {
                JOptionPane.showMessageDialog(this, "Cannot parse packet number", "Invalid number", JOptionPane.ERROR_MESSAGE);
                lineNumberField.selectAll();
                return; // do not close the dialogue
            }

            int[] packetRange = packetsTable.getPacketNumberRange();
            if (lineNumber < packetRange[0] || lineNumber > packetRange[1]) {
                JOptionPane.showMessageDialog(this, "There is no packet number " + lineNumber, "Out of Range", JOptionPane.ERROR_MESSAGE);
                lineNumberField.selectAll();
                return; // do not close the dialogue
            }
            returnValue = APPROVE_OPTION;
            setVisible(false);
        } else if ("cancel".equals(cmd)) {
            returnValue = CANCEL_OPTION;
            setVisible(false);
        }
    }
}
