importPackage(Packages.org.eclipse.swt);
importPackage(Packages.org.eclipse.swt.widgets);
importPackage(Packages.org.eclipse.swt.events);
importPackage(Packages.org.eclipse.swt.layout);
importPackage(Packages.org.eclipse.jface.dialogs);
importPackage(Packages.java.lang);
importPackage(Packages.org.csstudio.opibuilder.scriptUtil);

if(PVUtil.getDouble(pvs[0]) == 100){
	execute();
}

function execute2(){
	var op = MessageDialog.openWarning(
			null, "Warning", "The temperature you set is too high!");
}



function execute() {	
		//The script will be executed in the UI thread, so it is not allowed to create a new display.
		var display = Display.getCurrent();
		var shell = new Shell(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		shell.setSize(465, 200);
		shell.setText("MessageDialog");
		shell.setLayout(new GridLayout(5, false));
		text = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL
				| SWT.V_SCROLL);
		var data = new GridData(GridData.FILL_BOTH);
		data.horizontalSpan = 5;
		text.setLayoutData(data);
		
		confirm = new Button(shell, SWT.NONE);
		confirm.setText("Confirm");
		gridconfirm = new GridData();
		gridconfirm.widthHint = 85;
		gridconfirm.heightHint = 25;
		confirm.setLayoutData(gridconfirm);
		var information = new Button(shell, SWT.NONE);
		information.setText("Information");
		var gridinformation = new GridData();
		gridinformation.widthHint = 85;
		gridinformation.heightHint = 25;
		information.setLayoutData(gridinformation);
	
		listener = {
			widgetSelected:function(event) 
			{
				text.setText("hello");
				var op = MessageDialog.openInformation(null, "Information", text.getText());

			}
		};
		alistener = new SelectionListener(listener);
		information.addSelectionListener(alistener);

		shell.open();
		shell.layout();
		
}