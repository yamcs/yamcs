/*from org.csstudio.opibuilder.scriptUtil import PVUtil
widget.setPropertyValue("text", Date.now().toString());*/

importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
//var pv0 = PVUtil.getDouble(pvs[0]);
widget.setPropertyValue("text",PVUtil.getTimeString(pvs[0]));
//widget.setPropertyValue("text",Date.now());
//for
//if(pv0==0)
	//widget.setPropertyValue("text",Date());
//else
//	widget.setPropertyValue("text","######");
