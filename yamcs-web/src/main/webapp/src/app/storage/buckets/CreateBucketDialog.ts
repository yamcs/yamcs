import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatLegacyDialogRef, MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';
import { StorageClient } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-create-bucket-dialog',
  templateUrl: './CreateBucketDialog.html',
})
export class CreateBucketDialog {

  form: UntypedFormGroup;

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatLegacyDialogRef<CreateBucketDialog>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any,
    private messageService: MessageService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.form = formBuilder.group({
      name: ['', Validators.required],
    });
  }

  save() {
    this.storageClient.createBucket(this.data.bucketInstance, {
      name: this.form.value['name'],
    }).then(() => this.dialogRef.close(true))
      .catch(err => {
        this.dialogRef.close(false);
        this.messageService.showError(err);
      });
  }
}
