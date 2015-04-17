importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var src = PVUtil.getString(pvs[0]);
if(src == "Open OPI")
	widget.executeAction(0);
else if(src == "Play Sound")
	widget.executeAction(1);
	