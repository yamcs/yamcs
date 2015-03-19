importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var value = PVUtil.getLong(pvs[0]);

var mainContainer = widget.getChild("Main Container");

var opiArray=["1_1_Rectangle_Ellipse.opi","1_5_Polyline_Polygon.opi","2_3_Gauge_Meter.opi", "1_4_Arc.opi"];

//recover border style for all other widgets 
for(var i=0; i<4; i++){	
	if(i != value)
		widget.getChild("Sub Container " + i).setPropertyValue("border_style", 2);
}

//highlight this container by setting its border to line style
widget.getChild("Sub Container " + value).setPropertyValue("border_style", 1);

//load OPI to main container
mainContainer.setPropertyValue("opi_file", opiArray[value]);
