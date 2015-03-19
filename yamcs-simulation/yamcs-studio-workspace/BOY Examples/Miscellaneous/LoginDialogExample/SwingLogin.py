from SwingLoginDialog import *

dialog = SwingLoginDialog()
dialog.setVisible(True)
info=dialog.getLoginInfo()

if info != None:
    if info[0]=="admin" and info[1]=="123456":
        display.getWidget("SwingLogin").setVisible(True)
    else:
        JOptionPane.showMessageDialog(None, "The user name or password is wrong!", 
                                      "Error", JOptionPane.ERROR_MESSAGE)
            
