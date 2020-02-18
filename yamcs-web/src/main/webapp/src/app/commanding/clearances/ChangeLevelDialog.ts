import { Component, Inject } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Clearance } from '../../client';

@Component({
    selector: 'app-change-level-dialog',
    templateUrl: './ChangeLevelDialog.html',
    styleUrls: ['./ChangeLevelDialog.css'],
})
export class ChangeLevelDialog {

    form: FormGroup;

    constructor(
        private dialogRef: MatDialogRef<ChangeLevelDialog>,
        formBuilder: FormBuilder,
        @Inject(MAT_DIALOG_DATA) readonly data: any,
    ) {

        this.form = formBuilder.group({
            'level': new FormControl(null, [Validators.required]),
        });

        if (data.clearance) {
            const clearance = data.clearance as Clearance;
            this.form.setValue({
                level: clearance.level || 'DISABLED',
            });
        }
    }

    confirm() {
        this.dialogRef.close({
            level: this.form.value['level'] === 'DISABLED' ? undefined : this.form.value['level'],
        });
    }
}
