import { ChangeDetectionStrategy, Component, Inject, OnInit, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { debounceTime, map, switchMap } from 'rxjs/operators';
import { Parameter } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { ColorPalette } from './ColorPalette';
import { Thickness } from './Thickness';

@Component({
  selector: 'app-compare-parameter-dialog',
  templateUrl: './CompareParameterDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompareParameterDialog implements OnInit {

  parameter = new FormControl(null, [Validators.required]);

  filteredOptions: Observable<Parameter[]>;

  @ViewChild('palette', { static: true })
  palette: ColorPalette;

  @ViewChild('thickness', { static: true })
  thickness: Thickness;

  constructor(
    private dialogRef: MatDialogRef<CompareParameterDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) { }

  ngOnInit() {
    const excludedParameters = this.data.exclude as Parameter[];
    this.filteredOptions = this.parameter.valueChanges.pipe(
      debounceTime(300),
      switchMap(val => this.yamcs.yamcsClient.getParameters(this.yamcs.getInstance(), {
        q: val,
        limit: 10,
      })),
      map(page => page.parameters || []),
      map(candidates => {
        return candidates.filter(candidate => {
          for (const excludedParameter of excludedParameters) {
            if (excludedParameter.qualifiedName === candidate.qualifiedName) {
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
