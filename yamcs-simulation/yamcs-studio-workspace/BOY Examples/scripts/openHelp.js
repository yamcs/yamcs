importPackage(Packages.org.csstudio.opibuilder.scriptUtil);
importPackage(Packages.org.eclipse.ui)

var helpFile=widget.getPropertyValue("name");

PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(
	"/org.csstudio.opibuilder/html/" + helpFile);