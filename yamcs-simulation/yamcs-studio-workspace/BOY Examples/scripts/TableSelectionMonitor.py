from org.csstudio.opibuilder.scriptUtil import PVUtil
from org.csstudio.swt.widgets.natives.SpreadSheetTable  import ITableSelectionChangedListener
from java.util import Arrays

table = widget.getTable()

class SelectionListener(ITableSelectionChangedListener):
    def selectionChanged(self, selection):
        text=""
        for row in selection:
            i=0
            for s in row:
                text += s;
                if i != (len(row)-1):
                     text += ", "
                i+=1
            text = text + "\n"
        display.getWidget("selectionLabel").setPropertyValue("text", text)

table.addSelectionChangedListener(SelectionListener())


