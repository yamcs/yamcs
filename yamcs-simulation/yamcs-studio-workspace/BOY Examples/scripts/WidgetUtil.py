#A module to help set widget properties.


def setMyBounds(widget, x, y, width, height):
    widget.setPropertyValue("x", x);
    widget.setPropertyValue("y", y);
    widget.setPropertyValue("width",width);
    widget.setPropertyValue("height",height);
    
def setMyForeColor(widget, color):
    widget.setPropertyValue("foreground_color", color)

def setForeColor(display, name, color):
    display.getWidget(name).setPropertyValue("foreground_color", color)

def setBackColor(display, name, color):
    display.getWidget(name).setPropertyValue("background_color", color)
    
def setText(display, name, text):
    display.getWidget(name).setPropertyValue("text", text)
    
    