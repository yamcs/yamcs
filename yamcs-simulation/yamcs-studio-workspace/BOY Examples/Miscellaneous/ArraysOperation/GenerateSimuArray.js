importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var xArray = DataUtil.createDoubleArray(100);
var yArray = DataUtil.createDoubleArray(100);

var xPV = pvArray[0];
var yPV = pvArray[1];
var sPVValue = PVUtil.getDouble(pvs[2]);


for(var i=0; i<100; i++){
	xArray[i] = i*0.25+sPVValue;
	yArray[i] = Math.sin(i*0.25+sPVValue);	
}



xPV.setValue(xArray);
yPV.setValue(yArray);
