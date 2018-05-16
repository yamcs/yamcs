import { ChangeDetectionStrategy, Component, Inject, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material';
import { ColorPalette } from './ColorPalette';
import { Thickness } from './Thickness';

@Component({
  selector: 'app-modify-parameter-dialog',
  templateUrl: './ModifyParameterDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModifyParameterDialog {

  @ViewChild('palette')
  palette: ColorPalette;

  @ViewChild('thickness')
  thickness: Thickness;

  constructor(
    private dialogRef: MatDialogRef<ModifyParameterDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {}

  select() {
    this.dialogRef.close({
      color: this.palette.selectedColor,
      thickness: this.thickness.selectedThickness,
    });
  }
}
