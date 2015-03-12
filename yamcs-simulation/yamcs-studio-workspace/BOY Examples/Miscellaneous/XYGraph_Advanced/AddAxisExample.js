importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
importPackage(Packages.org.csstudio.swt.xygraph.figures);
importPackage(Packages.org.csstudio.ui.util);

var xygraph = widget.getFigure().getXYGraph();

for(var i=0; i<5; i++){
	var axis = new Axis("Axis", true);
	axis.setPrimarySide(i%2==1);
	axis.setRange(-i*10-5, i*10+5);
	var trace =xygraph.getPlotArea().getTraceList().get(i);
	axis.setForegroundColor(trace.getTraceColor());
	xygraph.addAxis(axis);
	trace.setYAxis(axis);
}
