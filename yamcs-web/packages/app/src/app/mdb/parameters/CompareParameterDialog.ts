import { Component, Inject, OnInit } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material';
import { Parameter } from '@yamcs/client';
import { Observable } from 'rxjs';
import { debounceTime, switchMap } from 'rxjs/operators';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-compare-parameter-dialog',
  templateUrl: './CompareParameterDialog.html',
})
export class CompareParameterDialog implements OnInit {

  parameter = new FormControl(null, [Validators.required]);

  filteredOptions: Observable<Parameter[]>;

  constructor(
    private dialogRef: MatDialogRef<CompareParameterDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {}

  ngOnInit() {
    this.filteredOptions = this.parameter.valueChanges.pipe(
      debounceTime(300),
      switchMap(val => this.yamcs.getInstanceClient()!.getParameters({
        q: val,
        limit: 10,
      })),
    );
  }

  select() {
    this.dialogRef.close(this.parameter.value);
  }
}
