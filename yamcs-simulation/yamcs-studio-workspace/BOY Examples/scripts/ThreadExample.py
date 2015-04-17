from org.csstudio.opibuilder.scriptUtil import PVUtil, ConsoleUtil
from java.lang import Thread, Runnable


class MyTask(Runnable):
    def run(self):        
        display.getWidget("Start_Button_Py").setPropertyValue("visible", False)
        display.getWidget("Start_Button_JS").setPropertyValue("visible", False)
        display.getWidget("Progress_Bar").setPropertyValue("visible", True)
        
        for i in range(100, 0, -1):
            if not display.isActive():
                return
            if i%10==0:
                widget.setPropertyValue("text", "I'm going to finish in %s seconds..." % (i/10))
            pvs[1].setValue(100 - i)
            Thread.sleep(100)
        pvs[1].setValue(100)
        widget.setPropertyValue("text", "I'm done! Hit the button again to start me.")
        display.getWidget("Progress_Bar").setPropertyValue("visible", False)
        display.getWidget("Start_Button_Py").setPropertyValue("visible", True)
        display.getWidget("Start_Button_JS").setPropertyValue("visible", True)

thread =Thread(MyTask());
thread.start()
