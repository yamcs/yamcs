importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
importPackage(Packages.java.lang);


runnable = {
	run:function()
		{	
			for(var i=5; i>0; i--){
				if(!display.isActive())
					return;			
				Thread.sleep(1000);
			}
			pvs[1].setValue(PVUtil.getLong(pvs[0]))			
		}	
	};		

new Thread(new Runnable(runnable)).start();
