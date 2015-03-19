importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

// Simple animation, meant to be used with an input PV
// like sim://sine(-10,10,10,0.1)
// that runs -10...10 in 10 steps, updating every 0.1 seconds
var sine = PVUtil.getDouble(pvs[0]);

// Update widget position
var x = 440 + sine;
var y = 390 + 0.2*sine;
widget.setPropertyValue("x", x);
widget.setPropertyValue("y", y);
