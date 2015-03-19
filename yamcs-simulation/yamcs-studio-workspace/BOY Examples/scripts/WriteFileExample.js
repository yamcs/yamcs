importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
importPackage(Packages.java.io);

var filePath = display.getWidget("filePath").getPropertyValue("text");

var text = display.getWidget("textInput").getPropertyValue("text");

var isInWorkspace = display.getWidget("workspaceFile").getValue();

var isAppend = display.getWidget("append").getValue();


FileUtil.writeTextFile(filePath, isInWorkspace.booleanValue(), text, isAppend.booleanValue());