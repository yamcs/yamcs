import { ChangeDetectionStrategy, Component, Inject, OnInit } from '@angular/core';
import { UntypedFormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MemberPathPipe, Parameter, YamcsService } from '@yamcs/webapp-sdk';
import { Observable } from 'rxjs';
import { debounceTime, map, switchMap } from 'rxjs/operators';

export interface SelectParameterOptions {
  label?: string;
  okLabel?: string;
  exclude?: string[];
  limit?: number;
}

/**
 * Reusabe dialog for selecting a single parameter via its qualified name.
 * Allows also manual parameter entry for parameters that do not (yet) exist on the server.
 */
@Component({
  selector: 'app-select-parameter-dialog',
  templateUrl: './SelectParameterDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectParameterDialog implements OnInit {

  parameter = new UntypedFormControl(null, [Validators.required]);

  filteredOptions: Observable<Parameter[]>;

  label: string;
  okLabel: string;
  limit: number;

  constructor(
    private dialogRef: MatDialogRef<SelectParameterDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: SelectParameterOptions,
    private memberPathPipe: MemberPathPipe,
  ) {
    this.label = data.label || 'Search parameter';
    this.okLabel = data.okLabel || 'SELECT';
    this.limit = data.limit || 10;
  }

  ngOnInit() {
    const excludedParameters = this.data.exclude || [];
    this.filteredOptions = this.parameter.valueChanges.pipe(
      debounceTime(300),
      switchMap(val => this.yamcs.yamcsClient.getParameters(this.yamcs.instance!, {
        q: val,
        limit: this.limit,
        searchMembers: true,
      })),
      map(page => page.parameters || []),
      map(candidates => {
        return candidates.filter(candidate => {
          for (const excludedParameter of excludedParameters) {
            const qualifiedName = this.memberPathPipe.transform(candidate);
            if (excludedParameter === qualifiedName) {
              return false;
            }
          }
          return true;
        });
      }),
    );
  }

  select() {
    this.dialogRef.close(this.parameter.value);
  }
}
