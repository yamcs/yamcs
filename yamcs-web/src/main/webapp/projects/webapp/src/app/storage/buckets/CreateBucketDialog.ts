import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MessageService, StorageClient, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-create-bucket-dialog',
  templateUrl: './CreateBucketDialog.html',
})
export class CreateBucketDialog {

  form: UntypedFormGroup;

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<CreateBucketDialog>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
    private messageService: MessageService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.form = formBuilder.group({
      name: ['', Validators.required],
    });
  }

  save() {
    this.storageClient.createBucket({
      name: this.form.value['name'],
    }).then(() => this.dialogRef.close(true))
      .catch(err => {
        this.dialogRef.close(false);
        this.messageService.showError(err);
      });
  }
}
