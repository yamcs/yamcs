importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var filePath = PVUtil.getString(pvs[1]);

//root is a JDOM Element
var root = FileUtil.loadXMLFile(filePath, widget);

var groups = root.getChildren();

widget.removeAllChildren();

for(var i=0; i<groups.size(); i++){
	
	//create linking container
	var linkingContainer = WidgetUtil.createWidgetModel("org.csstudio.opibuilder.widgets.linkingContainer");	
	linkingContainer.setPropertyValue("opi_file", "SubPanel.opi");
	linkingContainer.setPropertyValue("auto_size", true);
	linkingContainer.setPropertyValue("zoom_to_fit", false);
	linkingContainer.setPropertyValue("border_style", 1);
	
	//add macros
	linkingContainer.addMacro("index", i);
	var macros = groups.get(i).getChildren();
	for(var j=0; j<macros.size(); j++){
		var macro = macros.get(j);
		linkingContainer.addMacro(macro.getName(), macro.getValue());
	}	
	
	//add linking container to widget
	widget.addChildToBottom(linkingContainer);
}

widget.performAutosize();

