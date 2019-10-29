import { Component, Inject } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { StorageClient } from '@yamcs/client';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';
import { LayoutState } from './LayoutState';

@Component({
  selector: 'app-create-layout-dialog',
  templateUrl: './CreateLayoutDialog.html',
})
export class CreateLayoutDialog {

  name = new FormControl();

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<CreateLayoutDialog>,
    private yamcs: YamcsService,
    private router: Router,
    private authService: AuthService,
    @Inject(MAT_DIALOG_DATA) readonly data: any
  ) {
    this.storageClient = yamcs.createStorageClient();
  }

  save() {
    const instance = this.yamcs.getInstance();
    const objectName = `layouts/${this.name.value}`;

    const newLayout: LayoutState = {
      frames: [],
    };
    const objectValue = new Blob([JSON.stringify(newLayout, undefined, 2)], {
      type: 'application/json',
    });
    const username = this.authService.getUser()!.getName();
    this.storageClient.uploadObject('_global', `user.${username}`, objectName, objectValue).then(() => {
      this.dialogRef.close();
      this.router.navigateByUrl(`/telemetry/layouts/${this.name.value}?instance=${instance.name}`);
    });
  }
}
