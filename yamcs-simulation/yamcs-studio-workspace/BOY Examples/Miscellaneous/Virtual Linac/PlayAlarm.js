importPackage(Packages.org.csstudio.opibuilder.scriptUtil);




var pv0 = PVUtil.getDouble(pvs[0]);
var flagName = "played";

if(widget.getExternalObject(flagName) == null){
	widget.setExternalObject(flagName, false);	
}

var b = widget.getExternalObject(flagName);


if(pv0 >185){
		if(b == false){
			widget.executeAction(0);
			widget.setExternalObject(flagName, true);				
		}			
}else{
	widgetController.setExternalObject(flagName, false);
}
	