importPackage(Packages.org.csstudio.opibuilder.scriptUtil);


//Create a new Macro Input
var macroInput = DataUtil.createMacrosInput(true);

//Put a macro in the new Macro Input
macroInput.put("pv", PVUtil.getString(pvArray[0]));

//Set the macro input of the linking container to this new macro input.
widgetController.setPropertyValue("macros", macroInput);

//Reload the OPI file in the linking container again 
//by setting the property value with forcing fire option in true.
widgetController.setPropertyValue("opi_file", 
	widgetController.getPropertyValue("opi_file"), true);

