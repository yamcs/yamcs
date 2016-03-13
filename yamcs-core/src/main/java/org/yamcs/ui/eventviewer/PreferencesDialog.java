package org.yamcs.ui.eventviewer;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * Preferences dialog under Edit-&gt;Preferences menu. Dialog is modal.
 * 
 * @author mu
 */
public class PreferencesDialog extends JDialog implements ActionListener {

    private static final long  serialVersionUID   = 1L;
    private JPanel             jContentPane       = null; // @jve:decl-index=0:visual-constraint="10,10"
    private JTabbedPane        jTabbedPane        = null; // tabbed pane with
                                                          // alert and rule
                                                          // configuration
    private JPanel             jPanelRules        = null; // panel with event
                                                          // filtering rules
                                                          // configuration
    private JPanel             jPanelAlerts       = null; // panel with alert
                                                          // configuration
    private JPanel             jPanelLeft         = null;
    private JPanel             jPanelRight        = null;
    private JScrollPane        jScrollPane        = null;
    private JPanel             jPanelButtons      = null;
    private JButton            jButtonClose       = null;
    private JButton            jButtonCancel      = null;
    private JPanel             jPanelWest         = null;
    private JPanel             jPanelSouth        = null;
    private EventViewer        eventViewer        = null;
    private JTable             filteredTable      = null;
    private JButton            jButtonAdd         = null; // button to add the
                                                          // rule
    private JButton            jButtonRemove      = null; // button to remove
                                                          // the rule
    private JButton            jButtonMoveUp      = null; // button to move the
                                                          // rule up
    private JButton            jButtonMoveDown    = null; // button to move the
                                                          // rule down

    /**
     * @param owner
     * @param modal
     */
    public PreferencesDialog(Frame owner, boolean modal)
    {
        super(owner, modal);

        initialize();
    }

    /**
     * @param owner
     * @param title
     */
    public PreferencesDialog(Frame owner, String title)
    {
        super(owner, title);

        initialize();
    }

    /**
     * @param owner
     * @param modalityType
     */
    public PreferencesDialog(Window owner, ModalityType modalityType)
    {
        super(owner, modalityType);

        initialize();
    }

    /**
     * @param owner
     * @param title
     * @param modalityType
     */
    public PreferencesDialog(Window owner, String title, ModalityType modalityType)
    {
        super(owner, title, modalityType);

        initialize();
    }

    /**
     * This method initializes jTabbedPane
     * 
     * @return javax.swing.JTabbedPane
     */
    private JTabbedPane getJTabbedPane()
    {
        if (jTabbedPane == null)
        {
            jTabbedPane = new JTabbedPane();
            jTabbedPane.setPreferredSize(new Dimension(260, 270));
            jTabbedPane.setName("");
            jTabbedPane.addTab("Filtering table", null, getJPanelRules(), "Configuration of event filtering rules");
            // jTabbedPane.addTab("Alerts", null, getJPanelAlerts(),
            // "Configuration of alerts");
        }
        return jTabbedPane;
    }

    /**
     * This method initializes jPanel
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelRules()
    {
        if (jPanelRules == null)
        {
            jPanelRules = new JPanel();
            jPanelRules.setLayout(new BorderLayout());
            jPanelRules.add(getJPanelLeft(), BorderLayout.CENTER);
            jPanelRules.add(getJPanelRight(), BorderLayout.EAST);
        }
        return jPanelRules;
    }

    /**
     * This method initializes jPanel
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelAlerts()
    {
        if (jPanelAlerts == null)
        {
            jPanelAlerts = new JPanel();
            jPanelAlerts.setLayout(new BorderLayout());
        }
        return jPanelAlerts;
    }

    /**
     * This method initializes jPanelLeft
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelLeft()
    {
        if (jPanelLeft == null)
        {
            GridBagConstraints gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.fill = GridBagConstraints.BOTH;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.weightx = 1.0;
            gridBagConstraints.weighty = 1.0;
            gridBagConstraints.gridx = 0;
            jPanelLeft = new JPanel();
            jPanelLeft.setLayout(new BoxLayout(jPanelLeft, BoxLayout.Y_AXIS));
            // jPanelLeft.add(getJComboBox());
            jPanelLeft.add(getJScrollPane());
        }
        return jPanelLeft;
    }

    /**
     * This method initializes jPanelRight
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelRight()
    {
        if (jPanelRight == null)
        {
            jPanelRight = new JPanel();
            jPanelRight.setLayout(new BorderLayout());
            jPanelRight.add(getJPanelWest(), BorderLayout.NORTH);
            jPanelRight.add(getJPanelSouth(), BorderLayout.SOUTH);
        }
        return jPanelRight;
    }

    /**
     * This method initializes jScrollPane
     * 
     * @return javax.swing.JScrollPane
     */
    private JScrollPane getJScrollPane()
    {
        if (jScrollPane == null)
        {
            jScrollPane = new JScrollPane();
            jScrollPane.setViewportView(getJFilteredTable());
        }
        return jScrollPane;
    }

    /**
     * This method initializes jPanelButtons
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelButtons()
    {
        if (jPanelButtons == null)
        {
            jPanelButtons = new JPanel();
            jPanelButtons.setLayout(new GridLayout(2, 1, 5, 3));
            jPanelButtons.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            jPanelButtons.add(getJButtonClose());
        }
        return jPanelButtons;
    }

    /**
     * This method initializes jButtonOk
     * 
     * @return javax.swing.JButton
     */
    private JButton getJButtonClose()
    {
        if (jButtonClose == null)
        {
            jButtonClose = new JButton();
            jButtonClose.setText(" Close ");
            jButtonClose.setActionCommand("close");
            jButtonClose.addActionListener(this);
        }
        return jButtonClose;
    }

    private JButton getJButtonMoveUp()
    {
        if (jButtonMoveUp == null)
        {
            jButtonMoveUp = new JButton();
            jButtonMoveUp.setText("Up");
            jButtonMoveUp.setActionCommand("move_up_rule");
            jButtonMoveUp.addActionListener(this);
        }
        return jButtonMoveUp;
    }

    private JButton getJButtonMoveDown()
    {
        if (jButtonMoveDown == null)
        {
            jButtonMoveDown = new JButton();
            jButtonMoveDown.setText("Down");
            jButtonMoveDown.setActionCommand("move_down_rule");
            jButtonMoveDown.addActionListener(this);
        }
        return jButtonMoveDown;
    }

    /**
     * This method initializes jButtonCancel
     * 
     * @return javax.swing.JButton
     */
    private JButton getJButtonCancel()
    {
        if (jButtonCancel == null)
        {
            jButtonCancel = new JButton();
            jButtonCancel.setText("Cancel");
            jButtonCancel.setActionCommand("cancel");
            jButtonCancel.addActionListener(this);
        }
        return jButtonCancel;
    }

    /**
     * This method initializes jPanelWest
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelWest()
    {
        if (jPanelWest == null)
        {
            jPanelWest = new JPanel();
            jPanelWest.setLayout(new GridLayout(5, 1, 5, 3));
            jPanelWest.add(getJButtonAdd());
            jPanelWest.add(getJButtonRemove());
            jPanelWest.add(getJButtonMoveUp());
            jPanelWest.add(getJButtonMoveDown());
        }
        return jPanelWest;
    }

    /**
     * Access to Enable button
     * 
     * @return
     */
    private JButton getJButtonAdd()
    {
        if (jButtonAdd == null)
        {
            jButtonAdd = new JButton("Add");
            jButtonAdd.setActionCommand("add_rule");
            jButtonAdd.addActionListener(this);
        }
        return jButtonAdd;
    }

    /**
     * Access to Disable button
     * 
     * @return
     */
    private JButton getJButtonRemove()
    {
        if (jButtonRemove == null)
        {
            jButtonRemove = new JButton("Delete");
            jButtonRemove.setActionCommand("delete_rule");
            jButtonRemove.addActionListener(this);
        }
        return jButtonRemove;
    }

    /**
     * This method initializes jPanelSouth
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJPanelSouth()
    {
        if (jPanelSouth == null)
        {
            jPanelSouth = new JPanel();
            jPanelSouth.setLayout(new BorderLayout());
            jPanelSouth.add(getJPanelButtons(), BorderLayout.EAST);
        }
        return jPanelSouth;
    }

    /**
     * @param args
     */
    public static void main(String[] args)
    {

        PreferencesDialog diag = new PreferencesDialog(null, ModalityType.MODELESS);
        diag.setVisible(true);
    }

    /**
     * This method initializes this
     * 
     * @return void
     */
    private void initialize()
    {
        this.eventViewer = (EventViewer) getOwner();
        this.setLocationByPlatform(true);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setSize(screenSize.width / 2, screenSize.height / 3);
        this.setContentPane(getJContentPane());
        this.setTitle("Preferences");
        
        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE) ;
    }

    /**
     * Table with events and alert status.
     * 
     * @return
     */
    public JTable getJFilteredTable()
    {
        if (filteredTable == null)
        {
            filteredTable = new JTable(eventViewer.getFilteringRulesTable()) {
                @Override
                public boolean isCellEditable(int row, int column)
                {
                    return true;
                }
            };

            filteredTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            final TableColumnModel tcm = filteredTable.getColumnModel();
            tcm.getColumn(FilteringRulesTable.ACTIVE_COL).setMaxWidth(80);
            tcm.getColumn(FilteringRulesTable.SEVERITY_COL).setMaxWidth(140);
            tcm.getColumn(FilteringRulesTable.ALERT_COL).setMaxWidth(80);
            tcm.getColumn(FilteringRulesTable.SHOW_COL).setMaxWidth(80);

            tcm.getColumn(FilteringRulesTable.ACTIVE_COL).setPreferredWidth(80);
            tcm.getColumn(FilteringRulesTable.RULE_NAME_COL).setPreferredWidth(200);
            tcm.getColumn(FilteringRulesTable.SOURCE_COL).setPreferredWidth(300);
            tcm.getColumn(FilteringRulesTable.EVENT_TYPE_COL).setPreferredWidth(300);
            tcm.getColumn(FilteringRulesTable.EVENT_MESSAGE_COL).setPreferredWidth(300);
            tcm.getColumn(FilteringRulesTable.SEVERITY_COL).setPreferredWidth(120);
            tcm.getColumn(FilteringRulesTable.ALERT_COL).setPreferredWidth(120);
            tcm.getColumn(FilteringRulesTable.SHOW_COL).setPreferredWidth(120);

            // Set up the editor for the active cells
            JCheckBox checkBox = new JCheckBox();
            TableColumn activeColumn = tcm.getColumn(FilteringRulesTable.ACTIVE_COL);
            activeColumn.setCellEditor(new DefaultCellEditor(checkBox));

            // Set up the editor for the severity cells
            JComboBox comboBox = new JComboBox();
            comboBox.addItem("Info");
            comboBox.addItem("Warning");
            comboBox.addItem("Error");
            comboBox.addItem("Warning & Error");
            comboBox.addItem("All");

            TableColumn severityColumn = tcm.getColumn(FilteringRulesTable.SEVERITY_COL);
            severityColumn.setCellEditor(new DefaultCellEditor(comboBox));         
            
            // Set up the editor for the alert cells
            comboBox = new JComboBox();
            comboBox.addItem("Sound");
            comboBox.addItem("PopUp");
            comboBox.addItem("None");

            TableColumn alertColumn = tcm.getColumn(FilteringRulesTable.ALERT_COL);
            alertColumn.setCellEditor(new DefaultCellEditor(comboBox));

            // Set up tool tips for the sport cells.
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
            renderer.setToolTipText("Click to edit");
            alertColumn.setCellRenderer(renderer);

            // Set up the editor for the alert cells
            comboBox = new JComboBox();
            comboBox.addItem("Yes");
            comboBox.addItem("No");

            TableColumn showColumn = tcm.getColumn(FilteringRulesTable.SHOW_COL);
            showColumn.setCellEditor(new DefaultCellEditor(comboBox));

            // Set up tool tips for the show cells.
            renderer = new DefaultTableCellRenderer();
            renderer.setToolTipText("Click to edit");
            showColumn.setCellRenderer(renderer);
        }
        return filteredTable;
    }

    /**
     * This method initializes jContentPane
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJContentPane()
    {
        if (jContentPane == null)
        {
            jContentPane = new JPanel();
            jContentPane.setLayout(new BorderLayout());
            jContentPane.setSize(new Dimension(479, 259));
            jContentPane.add(getJTabbedPane(), BorderLayout.CENTER);
        }
        return jContentPane;
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        String action = e.getActionCommand();
        if (action.equals("close"))
        {
            this.setVisible(false);
            eventViewer.getFilteringRulesTable().doNotifyAllObservers();
        }
        else if (action.equals("add_rule"))
        {
            addNewRule();
        }
        else if (action.equals("delete_rule"))
        {
            deleteRule();
        }
        else if (action.equals("move_up_rule"))
        {
            moveUpRule();
        }
        else if (action.equals("move_down_rule"))
        {
            moveDownRule();
        }
        else
        {
            assert (false);
        }
    }

    /**
     * Move the rule up.
     */
    private void moveUpRule()
    {
        moveRule(-1);
    }

    /**
     * Move the rule down
     */
    private void moveDownRule()
    {
        moveRule(+1);
    }

    /**
     * Moves the rule in the specified direction (up or down)
     * 
     * @param direction Up (-1) or down (+1) direction
     */
    private void moveRule(int direction)
    {
        // currently enable only one up/down step
        if (direction != -1 && direction != 1)
        {
            return;
        }

        int selected = filteredTable.getSelectedRow();
        if (selected == -1)
        {
            return;
        }

        int current = selected;
        int future = selected + direction;

        if (future < 0 || future >= filteredTable.getRowCount())
        {
            return;
        }

        eventViewer.getFilteringRulesTable().switchRules(current, future);
        filteredTable.getSelectionModel().setSelectionInterval(future, future);
    }

    /**
     * Add new filtering rule.
     */
    private void addNewRule()
    {
        int selected = filteredTable.getSelectedRow();
        if (selected == -1)
        {
            selected = 0;
        }

        FilteringRule rule = new FilteringRule();
        
        rule.setName("Filtering rule");
        
        eventViewer.getFilteringRulesTable().addRule(rule, selected);
        filteredTable.getSelectionModel().setSelectionInterval(selected, selected);
    }

    /**
     * Add new filtering rule. New rule is always added on the start of the
     * list.
     */
    public void addNewRule(FilteringRule rule)
    {
        rule.setName( rule.getSource() + " - " + rule.getEventType() );
        eventViewer.getFilteringRulesTable().addRule(rule, 0);
        filteredTable.getSelectionModel().setSelectionInterval(0, 0);
    }

    /**
     * Delete existing filtering rule
     */
    private void deleteRule()
    {
        int selected = filteredTable.getSelectedRow();
        if (selected == -1)
        {
            return;
        }

        eventViewer.getFilteringRulesTable().removeRule(selected);
    }
}

/**
 * Alert setting representation in the filtering rule.
 */
enum AlertSetting {
    AlertSound, AlertPopUp, AlertNone
}
