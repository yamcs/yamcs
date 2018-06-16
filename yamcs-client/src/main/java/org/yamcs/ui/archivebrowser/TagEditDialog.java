package org.yamcs.ui.archivebrowser;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;
import javax.swing.JFormattedTextField.AbstractFormatter;

import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.TimeEncoding;

public class TagEditDialog extends JDialog implements ActionListener {

    private static final long serialVersionUID = 1L;
    private JPanel jContentPane = null;
    private JPanel buttonPanel = null;
    private JButton okButton = null;
    private JButton cancelButton = null;
    private JPanel jPanel1 = null;
    private JLabel nameLabel = null;
    private JLabel startLabel = null;
    private JLabel stopLabel = null;
    private JLabel descriptionLabel = null;
    JTextField nameTextField = null;
    public JFormattedTextField startTextField = null;
    public JFormattedTextField stopTextField = null;
    private JEditorPane descriptionEditorPane = null;
    private JLabel jLabel = null;
    private InstantFormat iformat=new InstantFormat();

    public boolean ok=false;
    private JLabel jLabel1 = null;
    private JComboBox colorComboBox = null;

    /**
     * @param owner
     */
    public TagEditDialog(Frame owner) {
        super(owner);
        initialize();
    }

    /**
     * This method initializes this
     * 
     * @return void
     */
    private void initialize() {
        this.setSize(356, 332);
        this.setTitle("Edit Tag");
        this.setContentPane(getJContentPane());
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }


    public void actionPerformed( ActionEvent e ) {
        if ( e.getActionCommand().equalsIgnoreCase("ok") ) {
            //validate the start and stop
            try {
                long start,stop;
                start=(Long)startTextField.getValue();
                stop=(Long)stopTextField.getValue();
                if(start==TimeEncoding.INVALID_INSTANT && stop==TimeEncoding.INVALID_INSTANT) {
                    JOptionPane.showMessageDialog(this, "At least one of start or stop has to be specified", "Please specify the start or stop",  JOptionPane.ERROR_MESSAGE);
                    return; 
                
                }
                if(start!=TimeEncoding.INVALID_INSTANT && stop!=TimeEncoding.INVALID_INSTANT &&
                    start>stop) {
                        JOptionPane.showMessageDialog(this, "Stop has to be greater than start", "Invalid times",  JOptionPane.ERROR_MESSAGE);
                        return; 
                    }
                
            } catch (Exception e1) {
                e1.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error parsing time: "+e1.getMessage(), "Error parsing time",  JOptionPane.ERROR_MESSAGE);
                return;
            }
            ok=true;
            setVisible(false);
        } else if ( e.getActionCommand().equalsIgnoreCase("cancel") ) {
            ok=false;
            setVisible(false);
        } 
    }
    /**
     * This method initializes jContentPane
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJContentPane() {
        if (jContentPane == null) {
            jLabel = new JLabel();
            jLabel.setText("Edit Tag");
            jLabel.setHorizontalAlignment(JLabel.CENTER);
            BorderLayout borderLayout = new BorderLayout();
            borderLayout.setHgap(20);
            jContentPane = new JPanel();
            jContentPane.setLayout(borderLayout);
            jContentPane.add(getButtonPanel(), BorderLayout.SOUTH);
            jContentPane.add(getJPanel1(), BorderLayout.CENTER);
            jContentPane.add(jLabel, BorderLayout.NORTH);
        }
        return jContentPane;
    }

    /**
     * This method initializes buttonPanel	
     * 	
     * @return javax.swing.JPanel	
     */
    private JPanel getButtonPanel() {
        if (buttonPanel == null) {
            buttonPanel = new JPanel();
            buttonPanel.setLayout(new FlowLayout());
            buttonPanel.add(getOkButton(), null);
            buttonPanel.add(getCancelButton(), null);
        }
        return buttonPanel;
    }

    /**
     * This method initializes okButton	
     * 	
     * @return javax.swing.JButton	
     */
    private JButton getOkButton() {
        if (okButton == null) {
            okButton = new JButton();
            okButton.setText("  OK  ");
            okButton.setActionCommand("ok");
            okButton.addActionListener(this);
        }
        return okButton;
    }

    /**
     * This method initializes cancelButton	
     * 	
     * @return javax.swing.JButton	
     */
    private JButton getCancelButton() {
        if (cancelButton == null) {
            cancelButton = new JButton();
            cancelButton.setText("Cancel");
            cancelButton.setActionCommand("cancel");
            cancelButton.addActionListener(this);
            cancelButton.setVerifyInputWhenFocusTarget(false);
        }
        return cancelButton;
    }

    /**
     * This method initializes jPanel1	
     * 	
     * @return javax.swing.JPanel	
     */
    private JPanel getJPanel1() {
        if (jPanel1 == null) {
            GridBagConstraints gridBagConstraints7 = new GridBagConstraints();
            gridBagConstraints7.fill = GridBagConstraints.NONE;
            gridBagConstraints7.gridy = 3;
            gridBagConstraints7.weightx = 1.0;
            gridBagConstraints7.anchor = GridBagConstraints.WEST;
            gridBagConstraints7.gridx = 1;
            GridBagConstraints gridBagConstraints6 = new GridBagConstraints();
            gridBagConstraints6.gridx = 0;
            gridBagConstraints6.ipadx = 2;
            gridBagConstraints6.anchor = GridBagConstraints.EAST;
            gridBagConstraints6.gridy = 3;
            jLabel1 = new JLabel();
            jLabel1.setText("Color: ");
            GridBagConstraints gridBagConstraints5 = new GridBagConstraints();
            gridBagConstraints5.fill = GridBagConstraints.BOTH;
            gridBagConstraints5.gridy = 4;
            gridBagConstraints5.weightx = 1.0;
            gridBagConstraints5.weighty = 1.0;
            gridBagConstraints5.gridheight = 2;
            gridBagConstraints5.gridx = 1;
            GridBagConstraints gridBagConstraints31 = new GridBagConstraints();
            gridBagConstraints31.fill = GridBagConstraints.NONE;
            gridBagConstraints31.gridy = 2;
            gridBagConstraints31.weightx = 1.0;
            gridBagConstraints31.anchor = GridBagConstraints.WEST;
            gridBagConstraints31.gridx = 1;
            GridBagConstraints gridBagConstraints21 = new GridBagConstraints();
            gridBagConstraints21.fill = GridBagConstraints.NONE;
            gridBagConstraints21.gridy = 1;
            gridBagConstraints21.weightx = 1.0;
            gridBagConstraints21.gridwidth = 1;
            gridBagConstraints21.anchor = GridBagConstraints.WEST;
            gridBagConstraints21.gridx = 1;
            GridBagConstraints gridBagConstraints11 = new GridBagConstraints();
            gridBagConstraints11.fill = GridBagConstraints.HORIZONTAL;
            gridBagConstraints11.gridy = 0;
            gridBagConstraints11.weightx = 1.0;
            gridBagConstraints11.gridx = 1;
            GridBagConstraints gridBagConstraints3 = new GridBagConstraints();
            gridBagConstraints3.gridx = 0;
            gridBagConstraints3.anchor = GridBagConstraints.EAST;
            gridBagConstraints3.ipadx = 2;
            gridBagConstraints3.gridy = 4;
            descriptionLabel = new JLabel();
            descriptionLabel.setText("Description: ");
            GridBagConstraints gridBagConstraints2 = new GridBagConstraints();
            gridBagConstraints2.gridx = 0;
            gridBagConstraints2.anchor = GridBagConstraints.EAST;
            gridBagConstraints2.ipadx = 2;
            gridBagConstraints2.gridy = 2;
            stopLabel = new JLabel();
            stopLabel.setText("Stop: ");
            GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
            gridBagConstraints1.gridx = 0;
            gridBagConstraints1.ipadx = 2;
            gridBagConstraints1.anchor = GridBagConstraints.EAST;
            gridBagConstraints1.gridy = 1;
            startLabel = new JLabel();
            startLabel.setText("Start: ");
            GridBagConstraints gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.fill = GridBagConstraints.NONE;
            gridBagConstraints.anchor = GridBagConstraints.EAST;
            gridBagConstraints.ipadx = 2;
            gridBagConstraints.gridy = 0;
            nameLabel = new JLabel();
            nameLabel.setText("Name: ");
            jPanel1 = new JPanel();
            jPanel1.setLayout(new GridBagLayout());
            jPanel1.add(nameLabel, gridBagConstraints);
            jPanel1.add(startLabel, gridBagConstraints1);
            jPanel1.add(stopLabel, gridBagConstraints2);
            jPanel1.add(descriptionLabel, gridBagConstraints3);
            jPanel1.add(getNameTextField(), gridBagConstraints11);
            jPanel1.add(getStartTextField(), gridBagConstraints21);
            jPanel1.add(getStopTextField(), gridBagConstraints31);
            jPanel1.add(getDescriptionEditorPane(), gridBagConstraints5);
            jPanel1.add(jLabel1, gridBagConstraints6);
            jPanel1.add(getColorComboBox(), gridBagConstraints7);
        }
        return jPanel1;
    }

    /**
     * This method initializes nameTextField	
     * 	
     * @return javax.swing.JTextField	
     */
    private JTextField getNameTextField() {
        if (nameTextField == null) {
            nameTextField = new JTextField();

        }
        return nameTextField;
    }

    /**
     * This method initializes startTextField	
     * 	
     * @return javax.swing.JTextField	
     */
    private JTextField getStartTextField() {
        if (startTextField == null) {
            startTextField = new JFormattedTextField(iformat);
            startTextField.setMinimumSize(new Dimension(180, startTextField.getPreferredSize().height));
            startTextField.setInputVerifier(new TimeFieldVerifier());
        }
        return startTextField;
    }

    /**
     * This method initializes stopTextField	
     * 	
     * @return javax.swing.JTextField	
     */
    private JTextField getStopTextField() {
        if (stopTextField == null) {
            stopTextField = new JFormattedTextField(iformat);
            stopTextField.setMinimumSize(new Dimension(180, stopTextField.getPreferredSize().height));
            stopTextField.setInputVerifier(new TimeFieldVerifier());
        }
        return stopTextField;
    }

    /**
     * This method initializes descriptionEditorPane	
     * 	
     * @return javax.swing.JEditorPane	
     */
    private JEditorPane getDescriptionEditorPane() {
        if (descriptionEditorPane == null) {
            descriptionEditorPane = new JEditorPane();
        }
        return descriptionEditorPane;
    }

    public void fillFrom(ArchiveTag tag) {
        nameTextField.setText(tag.getName());
        if(tag.hasStart()) {
            startTextField.setValue(tag.getStart());
        } else {
            startTextField.setValue(TimeEncoding.INVALID_INSTANT);
        }
        if(tag.hasStop()) {
            stopTextField.setValue(tag.getStop());
        } else {
            stopTextField.setValue(TimeEncoding.INVALID_INSTANT);
        }

        if(tag.hasDescription()) {
            descriptionEditorPane.setText(tag.getDescription());
        } else {
            descriptionEditorPane.setText("");
        }
        
        if(tag.hasColor()) {
            colorComboBox.setSelectedItem(tag.getColor());
        }
    }

    /**
     * This method initializes colorComboBox	
     * 	
     * @return javax.swing.JComboBox	
     */
    private JComboBox getColorComboBox() {
        if (colorComboBox == null) {
            colorComboBox = new JComboBox(ColorUtils.colors.keySet().toArray());
            colorComboBox.setPreferredSize(new Dimension(180, colorComboBox.getPreferredSize().height));
            colorComboBox.setRenderer(new ColorRenderer(colorComboBox.getPreferredSize().height-2));
        }
        return colorComboBox;
    }

    public ArchiveTag getTag() {
        ArchiveTag.Builder atb=ArchiveTag.newBuilder();
        atb.setName(nameTextField.getText());
        atb.setColor((String)colorComboBox.getSelectedItem());
        long start=(Long)startTextField.getValue();
        if(start!=TimeEncoding.INVALID_INSTANT) {
            atb.setStart(start);
        }
        long stop=(Long)stopTextField.getValue();
        if(stop!=TimeEncoding.INVALID_INSTANT) {
            atb.setStop(stop);
        }
        if(!descriptionEditorPane.getText().isEmpty()){
            atb.setDescription(descriptionEditorPane.getText());
        }
        return atb.build();
    }
    
    
   

}  //  @jve:decl-index=0:visual-constraint="0,0"



class ColorRenderer extends JLabel implements ListCellRenderer {
    public ColorRenderer(int size) {
        ColorIcon.size=size;
        setOpaque(true);
        setHorizontalAlignment(CENTER);
        setVerticalAlignment(CENTER);
    }
   
    /*
     * This method finds the image and text corresponding
     * to the selected value and returns the label, set up
     * to display the text and image.
     */
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        //Get the selected index. (The index param isn't
        //always valid, so just use the value.)
        String selectedColor = (String)value;

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }
        setIcon(ColorIcon.getIcon(selectedColor));
        setText(selectedColor);
        setHorizontalAlignment(SwingConstants.LEFT);
        return this;
    }


}

class ColorIcon implements Icon {  
    private Color color;  
    static int size;
    private ColorIcon(Color color)   {  
        this.color = color;  
    }  

    public int getIconHeight() {  
        return size;  
    }  

    public int getIconWidth() {  
        return size;
    }  

    public void paintIcon(Component c, Graphics g, int x, int y) {  
        g.setColor(color);  
        g.fillRect(x, y, size - 1, size - 1);  

        g.setColor(Color.black);  
        g.drawRect(x, y, size - 1, size - 1);  
    }
    
    static Map<String, ColorIcon> icons=new HashMap<>();
    
    
    public static ColorIcon getIcon(String colorName) {
        ColorIcon icon=icons.get(colorName);
        if(icon==null) {
            icon=new ColorIcon(ColorUtils.getColor(colorName));
            icons.put(colorName, icon);
        }
        return icon;
    }
    
    
    @Override
    public String toString() {
        return "ColorIcon(color: "+color+", size:"+size+")";
    }
}

class TimeFieldVerifier extends InputVerifier {
    @Override
    public boolean verify(JComponent input) {
        if (input instanceof JFormattedTextField) {
            JFormattedTextField ftf = (JFormattedTextField)input;
            AbstractFormatter formatter = ftf.getFormatter();
            if (formatter != null) {
                String text = ftf.getText();
                try {
                     formatter.stringToValue(text);
                     return true;
                 } catch (ParseException pe) {
                     pe.printStackTrace();
                     JOptionPane.showMessageDialog(input, "Error parsing time: "+pe.getMessage(), "Error parsing time",  JOptionPane.ERROR_MESSAGE);
                     return false;
                 }
             }
         }
         return true;
     }
     @Override
    public boolean shouldYieldFocus(JComponent input) {
         return verify(input);
     }
 }
