from org.csstudio.opibuilder.scriptUtil import PVUtil
from java.lang import System
from org.eclipse.jface.dialogs import MessageDialog

ok = PVUtil.getDouble(pvs[0])
if ok ==1:
    userName = System.getProperty("UserName")
    password = System.getProperty("Password")
    if userName=="admin" and password == "123456":
        widget.setPropertyValue("visible", True)
    else:
        MessageDialog.openError(None, "Error", 
                                "The user name or password is wrong!")
        pvs[0].setValue(0)