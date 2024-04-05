import { ChangeDetectionStrategy, Component, Inject, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { ColorPaletteComponent } from '../color-palette/color-palette.component';
import { ThicknessComponent } from '../thickness/thickness.component';

@Component({
  standalone: true,
  selector: 'app-modify-parameter-dialog',
  templateUrl: './modify-parameter-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ColorPaletteComponent,
    WebappSdkModule,
    ThicknessComponent,
  ],
})
export class ModifyParameterDialogComponent {

  @ViewChild('palette', { static: true })
  palette: ColorPaletteComponent;

  @ViewChild('thickness', { static: true })
  thickness: ThicknessComponent;

  constructor(
    private dialogRef: MatDialogRef<ModifyParameterDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) { }

  select() {
    this.dialogRef.close({
      color: this.palette.selectedColor,
      thickness: this.thickness.selectedThickness,
    });
  }
}
