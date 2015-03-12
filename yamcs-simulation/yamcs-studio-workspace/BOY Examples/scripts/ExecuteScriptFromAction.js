importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
importPackage(Packages.org.eclipse.jface.dialogs);
importPackage(Packages.java.lang);

var color;
var colorName;
if(Math.random()>0.5){
	color = ColorFontUtil.getColorFromRGB(0,160,0);
	colorName = "green";
}
else{
	color = ColorFontUtil.RED;
	colorName = "red";
}
display.getWidget("myIndicator").setPropertyValue("background_color", color);
widget.setPropertyValue("foreground_color", color);
	
MessageDialog.openInformation(
			null, "Dialog from JavaScript", "JavaScript says: My color is " + colorName);
