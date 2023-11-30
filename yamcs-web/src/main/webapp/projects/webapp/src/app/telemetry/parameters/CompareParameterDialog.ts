import { ChangeDetectionStrategy, Component, Inject, OnInit, ViewChild } from '@angular/core';
import { UntypedFormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MemberPathPipe, Parameter, YamcsService } from '@yamcs/webapp-sdk';
import { Observable } from 'rxjs';
import { debounceTime, map, switchMap } from 'rxjs/operators';
import { ColorPalette } from './ColorPalette';
import { Thickness } from './Thickness';

@Component({
  selector: 'app-compare-parameter-dialog',
  templateUrl: './CompareParameterDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompareParameterDialog implements OnInit {

  parameter = new UntypedFormControl(null, [Validators.required]);

  filteredOptions: Observable<Parameter[]>;

  @ViewChild('palette', { static: true })
  palette: ColorPalette;

  @ViewChild('thickness', { static: true })
  thickness: Thickness;

  constructor(
    private dialogRef: MatDialogRef<CompareParameterDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
    private memberPathPipe: MemberPathPipe,
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
            const qualifiedName = this.memberPathPipe.transform(candidate);
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
