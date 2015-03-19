from org.csstudio.opibuilder.scriptUtil import PVUtil
from org.csstudio.swt.widgets.figures.IntensityGraphFigure import IROIListener, IROIInfoProvider
from org.csstudio.simplepv import IPVListener
from java.lang import Thread, Runnable
from org.eclipse.swt.widgets import Display
from org.csstudio.swt.xygraph.figures import Annotation, IAnnotationListener

leftPV = pvs[1]
rightPV = pvs[2]


xyGraph = widget.getFigure().getXYGraph()

leftAnnotation = Annotation("Left", xyGraph.primaryXAxis, xyGraph.primaryYAxis)
leftAnnotation.setCursorLineStyle(Annotation.CursorLineStyle.UP_DOWN)
xyGraph.addAnnotation(leftAnnotation);

class LeftAnnotationListener(IAnnotationListener):
	'''Listener on left annotation
	'''
	def annotationMoved(self, oldX, oldY, newX, newY):
		leftPV.setValue(newX)
		
leftAnnotation.addAnnotationListener(LeftAnnotationListener())
leftAnnotation.setValues(2, 5)

rightAnnotation = Annotation("Right", xyGraph.primaryXAxis, xyGraph.primaryYAxis)
rightAnnotation.setCursorLineStyle(Annotation.CursorLineStyle.UP_DOWN)
rightAnnotation.setValues(7,5)
xyGraph.addAnnotation(rightAnnotation);

class RightAnnotationListener(IAnnotationListener):
	'''Listener on right annotation
	'''
	def annotationMoved(self, oldX, oldY, newX, newY):
		rightPV.setValue(newX)

rightAnnotation.addAnnotationListener(RightAnnotationListener())

currentDisplay = Display.getCurrent()
class UpdateAnnotationUITask(Runnable):
	def run(self):
		#this method must be called in UI thread
		leftAnnotation.setValues(PVUtil.getDouble(leftPV), leftAnnotation.getYValue())
		rightAnnotation.setValues(PVUtil.getDouble(rightPV), rightAnnotation.getYValue())
				
class UpdateAnnotationPVListener(IPVListener):
	'''Update the ROI while ROI PV value updated'''
	def valueChanged(self, pv):
		currentDisplay.asyncExec(UpdateAnnotationUITask())


leftPV.addListener(UpdateAnnotationPVListener())
rightPV.addListener(UpdateAnnotationPVListener())



