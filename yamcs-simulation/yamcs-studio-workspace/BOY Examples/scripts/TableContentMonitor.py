from org.csstudio.opibuilder.scriptUtil import PVUtil
from org.csstudio.swt.widgets.natives.SpreadSheetTable  import ITableModifiedListener
from java.util import Arrays

table = widget.getTable()

class ContentListener(ITableModifiedListener):
    def modified(self, content):
        text=""
        for row in content:
            i=0
            for s in row:
                text += s;
                if i != (len(row)-1):
                     text += ", "
                i+=1
            text = text + "\n"
        display.getWidget("contentLabel").setPropertyValue("text", text)

contentListener = ContentListener()
contentListener.modified(table.getContent())
table.addModifiedListener(contentListener)


