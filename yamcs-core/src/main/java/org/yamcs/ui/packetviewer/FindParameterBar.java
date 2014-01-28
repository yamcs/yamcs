package org.yamcs.ui.packetviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.Border;

import org.yamcs.ui.packetviewer.ParametersTable.SearchStats;

public class FindParameterBar extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final ImageIcon ICON_DOWN = new ImageIcon(PacketViewer.class.getResource("/org/yamcs/images/down.png"));
    private static final ImageIcon ICON_UP = new ImageIcon(PacketViewer.class.getResource("/org/yamcs/images/up.png"));
    private static final ImageIcon ICON_CLOSE = new ImageIcon(PacketViewer.class.getResource("/org/yamcs/images/close.png"));

    private static final Color INVALID_COLOR = new Color(255, 150, 150);
    private static final Border INVALID_BORDER = BorderFactory.createLineBorder(new Color(205, 87, 40));

    public static final String OPEN_ACTION = "open-find-bar";
    public static final String CLOSE_ACTION = "close-find-bar";

    private JTextField searchField;
    private JLabel statsLabel;
    private ParametersTable parametersTable;

    public FindParameterBar(final ParametersTable parametersTable) {
        super(new BorderLayout());
        this.parametersTable = parametersTable;
        searchField = new JTextField(25);
        ImageIconButton downButton = new ImageIconButton(ICON_DOWN);

        ActionListener searchListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String searchTerm = searchField.getText();
                if (searchTerm != null && !searchTerm.trim().equals("")) {
                    SearchStats stats = parametersTable.nextSearchResult(searchTerm.toLowerCase());
                    processStats(stats);
                }
            }
        };

        searchField.addActionListener(searchListener);
        downButton.addActionListener(searchListener);

        ImageIconButton upButton = new ImageIconButton(ICON_UP);
        upButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String searchTerm = searchField.getText();
                if (searchTerm != null && !searchTerm.trim().equals("")) {
                    SearchStats stats = parametersTable.previousSearchResult(searchTerm.toLowerCase());
                    processStats(stats);
                }
            }
        });

        searchField.setPreferredSize(downButton.getPreferredSize());

        JPanel findBar_left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        findBar_left.add(new JLabel("Find:"));
        findBar_left.add(searchField);
        findBar_left.add(downButton);
        findBar_left.add(upButton);

        statsLabel = new JLabel("");
        statsLabel.setFont(statsLabel.getFont().deriveFont(~Font.BOLD));

        ImageIconButton closeFindBarButton = new ImageIconButton(ICON_CLOSE);
        closeFindBarButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                parametersTable.clearSearchResults();
                parametersTable.requestFocusInWindow();
            }
        });

        // GridBag, just for the vertical alignment..
        JPanel findBar_right = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.ipadx = 5;
        findBar_right.add(statsLabel, gbc);

        gbc.gridx = 2;
        findBar_right.add(closeFindBarButton, gbc);

        add(findBar_left, BorderLayout.CENTER);
        add(findBar_right, BorderLayout.EAST);

        installActions();
        setVisible(false);
    }

    private void installActions() {
        //
        // Close find bar by pressing escape
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CLOSE_ACTION);
        searchField.getActionMap().put(CLOSE_ACTION, new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                revertToDefaults();
                parametersTable.clearSearchResults();
                parametersTable.requestFocusInWindow();
            }
        });

        //
        // Open the find bar
        Action openAction = new AbstractAction("Find Parameter...") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(true);
                searchField.selectAll();
                searchField.requestFocusInWindow();
            }
        };
        openAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_F);
        openAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        getActionMap().put(OPEN_ACTION, openAction);
    }

    private void processStats(SearchStats stats) {
        if (stats != null) {
            revertToDefaults();
            statsLabel.setText(String.format("%s of %s", stats.selectedMatch, stats.totalMatching));
        } else {
            statsLabel.setText("Parameter not found");
            searchField.setBackground(INVALID_COLOR);
            searchField.setBorder(INVALID_BORDER);
        }
    }

    /**
     * Reverts the search field and the label to its defaults
     */
    private void revertToDefaults() {
        if (searchField.getBackground().equals(INVALID_COLOR)) {
            JTextField dummy = new JTextField();
            searchField.setBackground(dummy.getBackground());
            searchField.setBorder(dummy.getBorder());
        }
        statsLabel.setText("");
    }
}
