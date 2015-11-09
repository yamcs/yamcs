package org.yamcs.ui.eventviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;

/**
 * @author Martin Ursik
 */
public class EventDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final KeyStroke KEY_ESC = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    
    public EventDialog(Frame owner) {
        super(owner);
        initComponents();
        
        // Close on escape
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KEY_ESC, "close");
        getRootPane().getActionMap().put("close", new AbstractAction() { 
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent event) { 
                dispatchEvent(new WindowEvent(EventDialog.this, WindowEvent.WINDOW_CLOSING)); 
            }
        });
    }

    public void setEvent(Event event)
    {   
        textFieldSource.setText(event.getSource());
        textFieldGenerationTime.setText(TimeEncoding.toCombinedFormat(event.getGenerationTime()));
        textFieldReceptionTime.setText(TimeEncoding.toCombinedFormat(event.getReceptionTime()));
        textAreaMessage.setText(event.getMessage());
        textAreaMessage.setCaretPosition(0);
        textFieldSequenceNo.setText(Integer.toString(event.getSeqNumber()));
        textFieldSeverity.setText(event.getSeverity().toString());
        textFieldType.setText(event.getType());
    }
    
    private void initComponents() {
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        labelSource = new JLabel();
        textFieldSource = new JTextField();
        labelType = new JLabel();
        textFieldType = new JTextField();
        labelSequenceNum = new JLabel();
        textFieldSequenceNo = new JTextField();
        labelGenerationTime = new JLabel();
        textFieldGenerationTime = new JTextField();
        labelReceptionTime = new JLabel();
        textFieldReceptionTime = new JTextField();
        labelSeverity = new JLabel();
        textFieldSeverity = new JTextField();
        labelMessage = new JLabel();
        scrollPane1 = new JScrollPane();
        textAreaMessage = new JTextArea();
        buttonBar = new JPanel();
        okButton = new JButton();

        //======== this ========
        setTitle("Event - detailed view");
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        //======== dialogPane ========
        {
            dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));

            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {
                contentPanel.setLayout(new GridBagLayout());
                ((GridBagLayout)contentPanel.getLayout()).columnWidths = new int[] {0, 0, 0};
                ((GridBagLayout)contentPanel.getLayout()).rowHeights = new int[] {0, 0, 0, 0, 0, 0, 0, 0};
                ((GridBagLayout)contentPanel.getLayout()).columnWeights = new double[] {0.0, 0.0, 1.0E-4};
                ((GridBagLayout)contentPanel.getLayout()).rowWeights = new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0E-4};

                //---- labelSource ----
                labelSource.setText("Source");
                labelSource.setBackground(Color.white);
                contentPanel.add(labelSource, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                    GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
                    new Insets(0, 0, 5, 5), 0, 0));

                //---- textFieldSource ----
                textFieldSource.setEditable(false);
                textFieldSource.setColumns(20);
                contentPanel.add(textFieldSource, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 5, 0), 0, 0));

                //---- labelType ----
                labelType.setText("Type");
                contentPanel.add(labelType, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
                    GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
                    new Insets(0, 0, 5, 5), 0, 0));

                //---- textFieldType ----
                textFieldType.setEditable(false);
                textFieldType.setColumns(20);
                contentPanel.add(textFieldType, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 5, 0), 0, 0));

                //---- labelSequenceNum ----
                labelSequenceNum.setText("Sequence number");
                contentPanel.add(labelSequenceNum, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0,
                    GridBagConstraints.NORTHEAST, GridBagConstraints.NONE,
                    new Insets(0, 0, 5, 5), 0, 0));

                //---- textFieldSequenceNo ----
                textFieldSequenceNo.setEditable(false);
                contentPanel.add(textFieldSequenceNo, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 5, 0), 0, 0));

                //---- labelGenerationTime ----
                labelGenerationTime.setText("Generation time");
                contentPanel.add(labelGenerationTime, new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0,
                    GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
                    new Insets(0, 0, 5, 5), 0, 0));

                //---- textFieldGenerationTime ----
                textFieldGenerationTime.setEditable(false);
                textFieldGenerationTime.setColumns(20);
                contentPanel.add(textFieldGenerationTime, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 5, 0), 0, 0));

                //---- labelReceptionTime ----
                labelReceptionTime.setText("Reception time");
                contentPanel.add(labelReceptionTime, new GridBagConstraints(0, 4, 1, 1, 0.0, 0.0,
                    GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
                    new Insets(0, 0, 5, 5), 0, 0));

                //---- textFieldReceptionTime ----
                textFieldReceptionTime.setEditable(false);
                textFieldReceptionTime.setColumns(20);
                contentPanel.add(textFieldReceptionTime, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 5, 0), 0, 0));

                //---- labelSeverity ----
                labelSeverity.setText("Severity");
                contentPanel.add(labelSeverity, new GridBagConstraints(0, 5, 1, 1, 0.0, 0.0,
                    GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
                    new Insets(0, 0, 5, 5), 0, 0));

                //---- textFieldSeverity ----
                textFieldSeverity.setEditable(false);
                textFieldSeverity.setColumns(20);
                contentPanel.add(textFieldSeverity, new GridBagConstraints(1, 5, 1, 1, 0.0, 0.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 5, 0), 0, 0));

                //---- labelMessage ----
                labelMessage.setText("Message");
                contentPanel.add(labelMessage, new GridBagConstraints(0, 6, 1, 1, 0.0, 0.0,
                    GridBagConstraints.NORTHEAST, GridBagConstraints.NONE,
                    new Insets(0, 0, 0, 5), 0, 0));

                //======== scrollPane1 ========
                {

                    //---- textAreaMessage ----
                    textAreaMessage.setLineWrap(true);
                    textAreaMessage.setRows(10);
                    textAreaMessage.setWrapStyleWord(true);
                    textAreaMessage.setEditable(false);
                    textAreaMessage.setColumns(40);
                    scrollPane1.setViewportView(textAreaMessage);
                }
                contentPanel.add(scrollPane1, new GridBagConstraints(1, 6, 1, 1, 1.0, 1.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 0, 0), 0, 0));
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(new EmptyBorder(12, 0, 0, 0));
                buttonBar.setLayout(new GridBagLayout());
                ((GridBagLayout)buttonBar.getLayout()).columnWidths = new int[] {0, 80};
                ((GridBagLayout)buttonBar.getLayout()).columnWeights = new double[] {1.0, 0.0};

                //---- okButton ----
                okButton.setText("OK");
                buttonBar.add(okButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(0, 0, 0, 0), 0, 0));
                okButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        EventDialog.this.dispose();
                    }
                });

            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());
    }

    private JPanel dialogPane;
    private JPanel contentPanel;
    private JLabel labelSource;
    private JTextField textFieldSource;
    private JLabel labelType;
    private JTextField textFieldType;
    private JLabel labelSequenceNum;
    private JTextField textFieldSequenceNo;
    private JLabel labelGenerationTime;
    private JTextField textFieldGenerationTime;
    private JLabel labelReceptionTime;
    private JTextField textFieldReceptionTime;
    private JLabel labelSeverity;
    private JTextField textFieldSeverity;
    private JLabel labelMessage;
    private JScrollPane scrollPane1;
    private JTextArea textAreaMessage;
    private JPanel buttonBar;
    private JButton okButton;
}
