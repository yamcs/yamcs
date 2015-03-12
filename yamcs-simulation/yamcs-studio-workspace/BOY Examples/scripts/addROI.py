from org.csstudio.opibuilder.scriptUtil import PVUtil
from org.csstudio.swt.widgets.figures.IntensityGraphFigure import IROIListener, IROIInfoProvider
from org.csstudio.simplepv import IPVListener
from java.lang import Thread, Runnable
from org.eclipse.swt.widgets import Display


roiXPV = pvs[1]
roiYPV = pvs[2]
roiWPV = pvs[3]
roiHPV = pvs[4]

intensityGraph = widget.getFigure()
name = PVUtil.getString(pvs[0])

class MyROIInfoProvider(IROIInfoProvider):
    '''Provide custom information for ROI.
    '''
    def getROIInfo(self, xIndex, yIndex, width, height):
        return name + "(" + str(xIndex) + ", " + str(yIndex) + " )"
        
class MyROIListener(IROIListener):
    '''Listener on ROI updates.
    '''
    def roiUpdated(self, xIndex, yIndex, width, height):
        roiXPV.setValue(xIndex)
        roiYPV.setValue(yIndex)
        roiWPV.setValue(width)
        roiHPV.setValue(height)

currentDisplay = Display.getCurrent()
class UpdateROIUITask(Runnable):
	def run(self):
		#this method must be called in UI thread
		intensityGraph.setROIDataBounds(name, PVUtil.getLong(roiXPV), PVUtil.getLong(roiYPV), PVUtil.getLong(roiWPV),PVUtil.getLong(roiHPV))
		
class UpdateROIFromPVListener(IPVListener):
	'''Update the ROI while ROI PV value updated'''
	def valueChanged(self, pv):
		currentDisplay.asyncExec(UpdateROIUITask())

intensityGraph.addROI(name, MyROIListener(), MyROIInfoProvider())

roiXPV.addListener(UpdateROIFromPVListener())
roiYPV.addListener(UpdateROIFromPVListener())
roiWPV.addListener(UpdateROIFromPVListener())
roiHPV.addListener(UpdateROIFromPVListener())



