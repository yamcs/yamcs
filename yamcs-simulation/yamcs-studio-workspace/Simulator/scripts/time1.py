from org.csstudio.opibuilder.scriptUtil import PVUtil
from org.csstudio.opibuilder.scriptUtil import PVUtil
from org.csstudio.opibuilder.scriptUtil import ColorFontUtil
import time;  # This is required to include time module.

ticks = time.time()
RED = ColorFontUtil.RED
widget.setPropertyValue("start_angle", ticks)
widget.setPropertyValue("foreground_color", RED)
