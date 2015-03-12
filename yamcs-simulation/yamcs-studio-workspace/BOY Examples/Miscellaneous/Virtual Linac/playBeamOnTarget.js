importPackage(Packages.org.csstudio.opibuilder.scriptUtil);




var pv0 = PVUtil.getDouble(pvArray[0]);
var pvSev0 = PVUtil.getSeverity(pvArray[0]);
var pv1 = PVUtil.getDouble(pvArray[1]);
var pvSev1 = PVUtil.getSeverity(pvArray[1]);
var flagName = "popped";

if(widgetController.getExternalObject(flagName) == null){
	widgetController.setExternalObject(flagName, false);	
}

var b = widgetController.getExternalObject(flagName);


if((pvSev0 ==0 || pvSev0 ==2)&&(pvSev1 ==0 || pvSev1==2)&& (pv0 !=0 && pv1!=0)){
	if(b == false){
			widgetController.executeAction(0);
			widgetController.setExternalObject(flagName, true);
	}
}else
	widgetController.setExternalObject(flagName, false);
	