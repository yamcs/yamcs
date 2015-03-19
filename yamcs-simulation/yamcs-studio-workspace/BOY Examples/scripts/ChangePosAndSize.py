from org.csstudio.opibuilder.scriptUtil import PVUtil

value = PVUtil.getDouble(pvs[0])

width = 5*value;
oldY=widget.getPropertyValue("y")
oldHeight = widget.getPropertyValue("height");

#module in the same directory is visible to this script
import WidgetUtil
WidgetUtil.setMyBounds(widget, value*40, 500 - width/2, width, width)



