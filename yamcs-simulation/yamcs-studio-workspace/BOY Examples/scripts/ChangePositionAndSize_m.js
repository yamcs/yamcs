importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var RED = ColorFontUtil.RED;
var GREEN = ColorFontUtil.GREEN;

var value = PVUtil.getDouble(pvs[0]);


var width = 5*value;
var oldY = widget.getPropertyValue("y");
var oldHeight = widget.getPropertyValue("height");

widget.setPropertyValue("x", value*10);
widget.setPropertyValue("y",  200 - width/2);
widget.setPropertyValue("width",width);
widget.setPropertyValue("height",width);	
		