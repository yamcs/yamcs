importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
importPackage(Packages.java.lang);
importPackage(Packages.org.eclipse.swt.widgets);
importPackage(Packages.org.csstudio.platform.ui.util);

var disArray = PVUtil.getDoubleArray(pvArray[0]);
var yPosArray = PVUtil.getDoubleArray(pvArray[1]);
var xPosPV = pvArray[2];
var yPosPV = pvArray[3];
var density = PVUtil.getLong(pvArray[4]);
		
runnable = {
	run:function()
		{	
			for(var i=0; i<disArray.length-1; i++){
				if(disArray[i+1]!=10){
					for(var j=0; j<density; j++){
						if(!display.isActive())
							return;
						var x0 = disArray[i];
						var x1 = disArray[i+1]
						var y0 = yPosArray[i];
						var y1 = yPosArray[i+1];
						var x = x0+(x1-x0)*j/density;
						var y = (x-x0)*(y1-y0)/(x1-x0) + y0;
						xPosPV.setValue(x);
						yPosPV.setValue(y);
						Thread.sleep(20);
					}
				};
			}
		}	
	};		
	//This task must be executed in a new thread so that the UI thread won't block
	new Thread(new Runnable(runnable)).start();
		
