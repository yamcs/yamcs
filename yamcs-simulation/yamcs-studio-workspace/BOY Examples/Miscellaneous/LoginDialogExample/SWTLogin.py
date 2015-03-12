from org.eclipse.jface.dialogs import MessageDialog
from org.eclipse.jface.window import Window
from SWTLoginDialog import *    
        
    
dialog = LoginDialog(Display.getCurrent().getActiveShell())
a = dialog.open() 
if a == Window.OK:
    info = dialog.getLoginInfo()
    if info[0] == "admin" and info[1]=="123456":
        display.getWidget("SWTLogin").setVisible(True)
    else:
        MessageDialog.openError(None, "Error", "The user name or password you input is wrong!")
