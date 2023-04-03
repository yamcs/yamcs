import { ChangeDetectionStrategy, Component, Inject, ViewChild } from '@angular/core';
import { MatLegacyDialogRef, MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';
import { ColorPalette } from './ColorPalette';
import { Thickness } from './Thickness';

@Component({
  selector: 'app-modify-parameter-dialog',
  templateUrl: './ModifyParameterDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModifyParameterDialog {

  @ViewChild('palette', { static: true })
  palette: ColorPalette;

  @ViewChild('thickness', { static: true })
  thickness: Thickness;

  constructor(
    private dialogRef: MatLegacyDialogRef<ModifyParameterDialog>,
    @Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any,
  ) { }

  select() {
    this.dialogRef.close({
      color: this.palette.selectedColor,
      thickness: this.thickness.selectedThickness,
    });
  }
}
