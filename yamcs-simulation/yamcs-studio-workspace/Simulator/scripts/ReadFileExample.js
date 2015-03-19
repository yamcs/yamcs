importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var filePath = display.getWidget("filePath").getPropertyValue("text");

var text = FileUtil.readTextFile(filePath);

display.getWidget("readLabel").setPropertyValue("text", text);
