importPackage(Packages.org.eclipse.jface.dialogs);
importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var flagName = "popped";

if(widget.getExternalObject(flagName) == null){
	widget.setExternalObject(flagName, false);	
}

var b = widget.getExternalObject(flagName);

if(PVUtil.getDouble(pvs[0]) > 80){		
		if( b == false){
			widget.setExternalObject(flagName, true);
			MessageDialog.openWarning(
				null, "Warning", "The temperature you set is too high!");
		}
		
}else if (b == true){
	widget.setExternalObject(flagName, false);
}
	

