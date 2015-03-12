from org.csstudio.opibuilder.scriptUtil import PVUtil
from org.csstudio.opibuilder.scriptUtil import ColorFontUtil
from org.eclipse.jface.dialogs import MessageDialog
from java.lang import Math



if Math.random() > 0.5:
	color = ColorFontUtil.getColorFromRGB(0,160,0)
	colorName = "green"

else:
	color = ColorFontUtil.RED
	colorName = "red"

import WidgetUtil
WidgetUtil.setBackColor(display, "myIndicator", color)
WidgetUtil.setMyForeColor(widget, color)

MessageDialog.openInformation(
			None, "Dialog from Python Script", "Python says: my color is " + colorName);

