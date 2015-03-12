from org.csstudio.opibuilder.scriptUtil import PVUtil, FileUtil
from java.lang import System


sysPath = FileUtil.workspacePathToSysPath("/BOY Examples/Miscellaneous/ExecuteShellScript")
System.setProperty("shellScript.dir", sysPath)
