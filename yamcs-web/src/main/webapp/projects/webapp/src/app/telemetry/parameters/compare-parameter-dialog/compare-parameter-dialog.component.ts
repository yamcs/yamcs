import { ChangeDetectionStrategy, Component, Inject, OnInit, ViewChild } from '@angular/core';
import { UntypedFormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Parameter, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { Observable } from 'rxjs';
import { debounceTime, map, switchMap } from 'rxjs/operators';
import { ColorPaletteComponent } from '../color-palette/color-palette.component';
import { ThicknessComponent } from '../thickness/thickness.component';

@Component({
  standalone: true,
  selector: 'app-compare-parameter-dialog',
  templateUrl: './compare-parameter-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ColorPaletteComponent,
    WebappSdkModule,
    ThicknessComponent,
  ],
})
export class CompareParameterDialogComponent implements OnInit {

  parameter = new UntypedFormControl(null, [Validators.required]);

  filteredOptions: Observable<Parameter[]>;

  @ViewChild('palette', { static: true })
  palette: ColorPaletteComponent;

  @ViewChild('thickness', { static: true })
  thickness: ThicknessComponent;

  constructor(
    private dialogRef: MatDialogRef<CompareParameterDialogComponent>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) { }

  ngOnInit() {
    const excludedParameters = this.data.exclude as Parameter[];
    this.filteredOptions = this.parameter.valueChanges.pipe(
      debounceTime(300),
      switchMap(val => this.yamcs.yamcsClient.getParameters(this.yamcs.instance!, {
        q: val,
        limit: 10,
        searchMembers: true,
      })),
      map(page => page.parameters || []),
      map(candidates => {
        return candidates.filter(candidate => {
          for (const excludedParameter of excludedParameters) {
            const qualifiedName = utils.getMemberPath(candidate);
            if (excludedParameter.qualifiedName === qualifiedName) {
              return false;
            }
          }
          return true;
        });
      }),
    );
  }

  select() {
    this.dialogRef.close({
      qualifiedName: this.parameter.value,
      color: this.palette.selectedColor,
      thickness: this.thickness.selectedThickness,
    });
  }
}
