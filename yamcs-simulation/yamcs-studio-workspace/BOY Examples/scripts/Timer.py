from org.csstudio.opibuilder.scriptUtil import PVUtil, ConsoleUtil, ColorFontUtil
from java.lang import Thread, Runnable

startButton = display.getWidget("Start_Button")
stopButton = display.getWidget("Stop_Button")
resetButton = display.getWidget("Reset_Button")
bar = display.getWidget("Progress_Bar")

hourText = display.getWidget("hourText")
hourPV=hourText.getPV()
minText = display.getWidget("minText")
minPV=minText.getPV()
secText = display.getWidget("secText")
secPV=secText.getPV()

timerLabel = display.getWidget("timerLabel")

class Blink(Runnable):
    def run(self):
        i=0
        while PVUtil.getLong(pvs[2]) ==1:
            Thread.sleep(500)
            timerLabel.setPropertyValue("foreground_color", 
                        ColorFontUtil.YELLOW if i%2==0 else ColorFontUtil.RED)
            i=i+1
        timerLabel.setPropertyValue("foreground_color", ColorFontUtil.BLACK)



class Timer(Runnable):
    def run(self):        
        startButton.setEnabled(False)
        stopButton.setEnabled(True)
        resetButton.setEnabled(False)
        bar.setVisible(True)
        hourText.setEnabled(False)
        minText.setEnabled(False)
        secText.setEnabled(False)
        hour = PVUtil.getLong(hourPV)
        min = PVUtil.getLong(minPV)
        sec = PVUtil.getLong(secPV)
        #remember the values to be reset
        resetButton.setVar("hour", hour)
        resetButton.setVar("min",min)
        resetButton.setVar("sec",sec)
        timerLabel.setPropertyValue("foreground_color", ColorFontUtil.BLACK)
        timerLabel.setPropertyValue("text", "Time Left:")
        stopped=False
        total = hour*3600+min*60+sec
        for i in range(total,-1,-1):
            if not display.isActive():
                return
            if PVUtil.getLong(pvs[0])==0:
                stopped = True
                break            
            pvs[1].setValue(100-100*i/total)
            hourPV.setValue(int(i/3600))
            minPV.setValue(int(i%3600/60))
            secPV.setValue(int(i%60))            
            Thread.sleep(1000)
            
        timerLabel.setPropertyValue("foreground_color", ColorFontUtil.RED)
        if stopped:
            timerLabel.setPropertyValue("text", "Interrupted!")
        else:
            timerLabel.setPropertyValue("text", "Time's Up!!!")
            widget.executeAction(0)
            pvs[2].setValue(1)
            Thread(Blink()).start()
        startButton.setEnabled(True)
        stopButton.setEnabled(False)
        resetButton.setEnabled(True)
        bar.setVisible(False)
        hourText.setEnabled(True)
        minText.setEnabled(True)
        secText.setEnabled(True)

if PVUtil.getLong(pvs[0])==1:
	thread =Thread(Timer());
	thread.start()
