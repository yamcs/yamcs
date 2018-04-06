import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Component, Inject } from '@angular/core';
import { FormControl } from '@angular/forms';
import { LayoutStorage } from './LayoutStorage';
import { Router } from '@angular/router';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-save-layout-dialog',
  templateUrl: './SaveLayoutDialog.html',
})
export class SaveLayoutDialog {

  name = new FormControl();

  constructor(
    private dialogRef: MatDialogRef<SaveLayoutDialog>,
    private yamcs: YamcsService,
    private router: Router,
    @Inject(MAT_DIALOG_DATA) readonly data: any) {
  }

  save() {
    const instance = this.yamcs.getInstance();
    const layoutName = this.name.value;
    const layoutState = this.data.state;
    LayoutStorage.saveLayout(instance.name, layoutName, layoutState);
    this.dialogRef.close();
    this.router.navigateByUrl(`/monitor/layouts/${layoutName}?instance=${instance.name}`);
  }
}
