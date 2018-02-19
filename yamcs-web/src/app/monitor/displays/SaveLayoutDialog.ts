import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Component, Inject } from '@angular/core';
import { FormControl } from '@angular/forms';
import { YamcsService } from '../../core/services/YamcsService';
import { SavedLayout } from './SavedLayout';

@Component({
  selector: 'app-save-layout-dialog',
  templateUrl: './SaveLayoutDialog.html',
})
export class SaveLayoutDialog {

  name = new FormControl();

  constructor(
    private yamcs: YamcsService,
    private dialogRef: MatDialogRef<SaveLayoutDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any) {

    console.log('layout', data.state);
  }

  save() {
    const instance = this.yamcs.getSelectedInstance().instance;
    const storageKey = `yamcs.${instance}.savedLayouts`;

    // Append to already saved layouts
    let savedLayouts: SavedLayout[] = [];
    const item = localStorage.getItem(storageKey);
    if (item) {
      savedLayouts = JSON.parse(item) as SavedLayout[];
    }
    savedLayouts.push({
      name: this.name.value,
      state: this.data.state,
    });

    // Persist (for now only to local storage)
    localStorage.setItem(storageKey, JSON.stringify(savedLayouts));

    this.dialogRef.close();
  }
}
