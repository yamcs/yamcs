import { ChangeDetectionStrategy, Component, Inject, OnInit } from '@angular/core';
import { UntypedFormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Parameter, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { Observable } from 'rxjs';
import { debounceTime, map, switchMap } from 'rxjs/operators';

export interface SelectParameterOptions {
  label?: string;
  okLabel?: string;
  exclude?: string[];
  hint?: string;
  limit?: number;
}

/**
 * Reusable dialog for selecting a single parameter via its qualified name.
 * Allows also manual parameter entry for parameters that do not (yet) exist on the server.
 */

@Component({
  standalone: true,
  selector: 'app-select-parameter-dialog',
  templateUrl: './select-parameter-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class SelectParameterDialogComponent implements OnInit {

  parameter = new UntypedFormControl(null, [Validators.required]);

  filteredOptions: Observable<Parameter[]>;

  label: string;
  okLabel: string;
  limit: number;
  hint?: string;

  constructor(
    private dialogRef: MatDialogRef<SelectParameterDialogComponent>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: SelectParameterOptions,
  ) {
    this.label = data.label || 'Search parameter';
    this.okLabel = data.okLabel || 'SELECT';
    this.limit = data.limit || 10;
    this.hint = data.hint;
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
            const qualifiedName = utils.getMemberPath(candidate);
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
