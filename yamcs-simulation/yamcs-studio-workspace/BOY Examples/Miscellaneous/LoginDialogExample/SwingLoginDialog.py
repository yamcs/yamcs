'''
    A login dialog implemented in Swing.
    
    @author: Xihui Chen    
'''

from javax.swing import JButton, JFrame, SwingUtilities, JOptionPane, JDialog,\
    JLabel, JTextField, JPasswordField
from java.awt.event import ActionListener
from org.csstudio.opibuilder.scriptUtil import ConsoleUtil
from java.lang import Runnable, Thread, String
from java.awt import GridLayout


class SwingLoginDialog(JDialog):
    
    def __init__(self):
        self.username=""
        self.password=""
        self.okPressed=False
        self.userField=JTextField(15)
        self.passField=JPasswordField(15)
        
        self.setTitle("Login")
        self.setModal(True)
        self.setAlwaysOnTop(True)
        self.setLocationRelativeTo(None)
        pane = self.getContentPane()
        pane.setLayout(GridLayout(4,2))
        pane.add(JLabel("Username:"))
        pane.add(self.userField)
        pane.add(JLabel("Password:"))
        pane.add(self.passField)
        pane.add(JLabel())
        pane.add(JLabel())
        okButton = JButton("OK", actionPerformed=self.okClick)
        pane.add(okButton)
        cancelButton=JButton("Cancel", actionPerformed=self.cancelClick)
        pane.add(cancelButton)
        self.pack() 
        
    def okClick(self, e):
        self.okPressed=True
        self.username=self.userField.getText()
        self.password=str(String(self.passField.getPassword()))
        self.setVisible(False)
    
    def cancelClick(self, e):
        self.okPressed=False
        self.setVisible(False)
        
        
    def getLoginInfo(self):
        if self.okPressed:            
            return [self.username, self.password]
        return None
