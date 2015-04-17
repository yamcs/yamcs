importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var dblArray = PVUtil.getDoubleArray(pvArray[0]);

var string = "";

for(var i=0; i<dblArray.length; i++){
	string = string.concat("element" + i +"=" + dblArray[i] + "\n");
}

widgetController.setValue(string);