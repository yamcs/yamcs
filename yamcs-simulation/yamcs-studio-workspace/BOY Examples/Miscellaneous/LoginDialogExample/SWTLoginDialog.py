'''
    A login dialog implemented in SWT
    
    @author: Xihui Chen    
'''

from org.eclipse.jface.dialogs import Dialog, MessageDialog
from org.eclipse.swt.widgets import Display, Label, Text
from org.eclipse.swt import SWT
from org.eclipse.swt.layout import GridData
from org.eclipse.jface.window import Window

class LoginDialog(Dialog):
    
    def __init__(self, parentShell):
        Dialog.__init__(self, parentShell)
    
    def createDialogArea(self, parent):
        self.getShell().setText("Login")
        container=self.super__createDialogArea(parent)
        gridLayout= container.getLayout()
        gridLayout.numColumns=2;  
        
        label = Label(container, SWT.None)
        label.setLayoutData(GridData(SWT.RIGHT, SWT.CENTER, False, False))
        label.setText("User Name: ")
        
        self.text = Text(container, SWT.BORDER)
        self.text.setLayoutData(GridData(SWT.FILL, SWT.CENTER, True, False))       
                
        label = Label(container, SWT.None)
        label.setLayoutData(GridData(SWT.RIGHT, SWT.CENTER, False, False))
        label.setText("Password: ")
        
        self.passwordText = Text(container, SWT.BORDER|SWT.PASSWORD)
        self.passwordText.setLayoutData(GridData(SWT.FILL, SWT.CENTER, True, False))
        return container
    def okPressed(self):
        self.userName=self.text.getText()
        self.passWord=self.passwordText.getText()
        self.super__okPressed()
    
    def getLoginInfo(self):
        return [self.userName, self.passWord]        
        
    