importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var dblArray = PVUtil.getLongArray(pvArray[0]);

var string = "";

for(var i=0; i<dblArray.length; i++){
	string = string.concat(String.fromCharCode(dblArray[i]));
}

widgetController.setValue(string);