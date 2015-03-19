from org.csstudio.opibuilder.scriptUtil import PVUtil, FileUtil
from org.csstudio.opibuilder.util import ResourceUtil
from java.lang import System

opiPath = display.getModel().getOpiFilePath()

isWorkspaceFile = ResourceUtil.isExistingWorkspaceFile(opiPath)

opiFolderPath = ResourceUtil.buildAbsolutePath(display.getModel(), ResourceUtil.getPathFromString("./")).toString()

if isWorkspaceFile:
	opiFolderPath = FileUtil.workspacePathToSysPath(opiFolderPath)
	
System.setProperty("shellScript2.dir", opiFolderPath)
