from org.csstudio.opibuilder.scriptUtil import PVUtil
from org.csstudio.swt.widgets.figures.IntensityGraphFigure import IPixelInfoProvider

class MyPixelInfoProvider(IPixelInfoProvider):
	'''Provide custom information for each pixel. 
	   For example the related geometry information on a pixel.
	'''
	def getPixelInfo(self, xIndex, yIndex, xCoordinate, yCoordinate, pixelValue):
		return "\nMy index is (" + str(xIndex) + ", " + str(yIndex) + " )" 

widget.getFigure().addPixelInfoProvider(MyPixelInfoProvider())
