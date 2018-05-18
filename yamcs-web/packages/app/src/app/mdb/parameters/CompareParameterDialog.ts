import { ChangeDetectionStrategy, Component, Inject, OnInit, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material';
import { Parameter } from '@yamcs/client';
import { Observable } from 'rxjs';
import { debounceTime, map, switchMap } from 'rxjs/operators';
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

  @ViewChild('palette')
  palette: ColorPalette;

  @ViewChild('thickness')
  thickness: Thickness;

  constructor(
    private dialogRef: MatDialogRef<CompareParameterDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {}

  ngOnInit() {
    const excludedParameters = this.data.exclude as Parameter[];
    this.filteredOptions = this.parameter.valueChanges.pipe(
      debounceTime(300),
      switchMap(val => this.yamcs.getInstanceClient()!.getParameters({
        q: val,
        limit: 10,
      })),
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
