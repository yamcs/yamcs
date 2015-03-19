importPackage(Packages.org.eclipse.jface.dialogs);
importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var flagName = "popped";

if(widgetController.getExternalObject(flagName) == null){
	widgetController.setExternalObject(flagName, false);	
}

var b = widgetController.getExternalObject(flagName);

if(PVUtil.getDouble(pvs[0]) > 80)
{		
		if( b == false)
		{
			widgetController.setExternalObject(flagName, true);
			MessageDialog.openWarning(
				null, "Warning", "The temperature you set is too high!");
		}
		
}
else if (b == true)
{
	widgetController.setExternalObject(flagName, false);
}
	

