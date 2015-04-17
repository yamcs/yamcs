importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

var value = PVUtil.getDouble(pvs[0]);
var x = Math.round(390 + value);
var angle = value;
if (angle < 0)
	angle = 360 + angle;

widget.setPropertyValue("rotation_angle", angle);

	