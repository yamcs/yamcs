importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var x0 = PVUtil.getDouble(pvArray[0]);
var y0 = PVUtil.getDouble(pvArray[1]);

if(x0==0 && y0==0) //if no beam
	widgetController.setPropertyValue("visible",false);
else
	widgetController.setPropertyValue("visible",true);


//calculate beam position
var x = x0*25/4 + 25;
var y = 25-y0*25/4;

widgetController.setPropertyValue("x",x);
widgetController.setPropertyValue("y",y);