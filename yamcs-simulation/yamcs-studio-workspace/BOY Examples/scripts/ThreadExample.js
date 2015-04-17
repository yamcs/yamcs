importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
importPackage(Packages.java.lang);



runnable = {
	run:function()
		{	display.getWidget("Start_Button_JS").setPropertyValue("visible", false);
			display.getWidget("Start_Button_Py").setPropertyValue("visible", false);
			display.getWidget("Progress_Bar").setPropertyValue("visible", true);
			for(var i=100; i>0; i--){
				if(!display.isActive())
					return;
				if(i%10==0)
					widget.setPropertyValue("text", "I'm going to finish in " + i/10 + " seconds...");
				pvs[1].setValue(100-i);
				Thread.sleep(100);
			}
			pvs[1].setValue(100)
			widget.setPropertyValue("text", "I'm done! Hit the button again to start me.");
			display.getWidget("Start_Button_JS").setPropertyValue("visible", true);
			display.getWidget("Start_Button_Py").setPropertyValue("visible", true);
			display.getWidget("Progress_Bar").setPropertyValue("visible", false);
		}	
	};		

new Thread(new Runnable(runnable)).start();
