importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
importPackage(Packages.java.lang);
 
var pvInt0 = PVUtil.getLong(pvArray[0]);
System.out.println(pvInt0.getClass());
System.out.println(pvInt0);
if(true)
	widgetController.setPropertyValue("group_name",pvInt0);
else
	widgetController.setPropertyValue("group_name","1");