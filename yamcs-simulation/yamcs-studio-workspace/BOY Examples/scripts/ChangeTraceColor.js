importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var RED = ColorFontUtil.RED;
var GREEN = ColorFontUtil.GREEN;

var graph1 = display.getWidget("Graph1");

var traceColor = graph1.getPropertyValue("trace_0_trace_color");
if(traceColor.getRGBValue().equals(RED))
	graph1.setPropertyValue("trace_0_trace_color", GREEN);
else
	graph1.setPropertyValue("trace_0_trace_color", RED);
