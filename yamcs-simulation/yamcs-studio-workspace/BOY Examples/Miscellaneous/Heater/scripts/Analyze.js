importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

// All connected?
if (pvs[0].getValue() != null  &&  pvs[1].getValue() != null)
{
	var pid_out = PVUtil.getDouble(pvs[0]);
	var sine = PVUtil.getDouble(pvs[1]);
	
	var text;
	var color;
	
	if (pid_out > 600)
	{
	    text = "Giving it all ...";
	    color = ColorFontUtil.PINK;
	}
    else if (pid_out > 400)
	{
	    text = "Heating a lot ...";
	    color = ColorFontUtil.PURPLE;
	}
    else if (pid_out > 200)
	{
	    text = "Heating some ...";
	    color = ColorFontUtil.RED;
	}
	else if (pid_out > 100)
	{
	    text = "Warming up ...";
	    color = ColorFontUtil.ORANGE;
	}
	else if (pid_out > 0)
	{
	    text = "Keeping warm ...";
	    color = ColorFontUtil.YELLOW;
	}
	else if (pid_out < 0)
	{
	    text = "Cooling down ...";
	    color = ColorFontUtil.LIGHT_BLUE;
	}
	else
	{
	    text = "Temperature is just right";
	    color = ColorFontUtil.GREEN;
	}
	widget.setPropertyValue("text", text);
	widget.setPropertyValue("background_color", color);
	widget.setPropertyValue("x", 440 + sine);
}
