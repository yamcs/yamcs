importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var n = PVUtil.getDouble(pvs[2]);
var rawData = PVUtil.getDoubleArray(pvs[0]);
var cutData = DataUtil.createDoubleArray(n);

for(var i=0; i<n; i++){
	cutData[i] = rawData[i];
}

pvs[1].setValue(cutData);
