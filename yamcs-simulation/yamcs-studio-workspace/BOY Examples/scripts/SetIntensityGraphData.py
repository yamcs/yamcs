from org.csstudio.opibuilder.scriptUtil import PVUtil
from org.csstudio.ui.util.thread import UIBundlingThread
from org.eclipse.swt.widgets import Display
from java.lang import Thread, Runnable

import array
import math

currentDisplay = Display.getCurrent()

class MyTask(Runnable):
    def run(self):    
		simuData = array.array('d', range(65536))
		value = PVUtil.getDouble(pvs[0])
		dataSrc = PVUtil.getString(pvs[1])
		
		if dataSrc == "Linear Sine Wave":
			for i in range(256):
				for j in range(256):
					simuData[i*256 + j] = math.sin(j*6*math.pi/256 + i*6*math.pi/256 + value)
				
		else:
			for i in range(256):
				for j in range(256):
					x = j-128
					y = i-128
					p = math.sqrt(x*x + y*y)
					simuData[i*256 + j] = math.sin(p*2*math.pi/256 + value)
		class UITask(Runnable):
			def run(self):
					widget.setValue(simuData)
		UIBundlingThread.getInstance().addRunnable(currentDisplay, UITask())
thread =Thread(MyTask())
thread.start()

