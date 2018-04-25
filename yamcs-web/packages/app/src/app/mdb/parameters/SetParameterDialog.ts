import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Component, Inject } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { YamcsService } from '../../core/services/YamcsService';
import { Parameter, Value } from '@yamcs/client';

@Component({
  selector: 'app-set-parameter-dialog',
  templateUrl: './SetParameterDialog.html',
})
export class SetParameterDialog {

  form: FormGroup;

  parameter: Parameter;

  constructor(
    private dialogRef: MatDialogRef<SetParameterDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.parameter = data.parameter;
    switch (this.parameter.type!.engType) {
      case 'boolean':
      case 'float':
      case 'double':
      case 'uint32':
      case 'sint32':
      case 'uint64':
      case 'sint64':
      case 'string':
        this.form = formBuilder.group({
          value: [null, Validators.required]
        });
        break;
      case 'timestamp':
        this.form = formBuilder.group({
          value: [null, Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)]
        });
        break;
      default:
        throw new Error(`Unexpected type ${this.parameter.type!.engType}`);
    }
  }

  save() {
    const userValue = this.form.value.value;
    let value: Value;
    switch (this.parameter.type!.engType) {
      case 'boolean':
        value = { type: 'BOOLEAN', booleanValue: (userValue === 'TRUE') };
        break;
      case 'float':
        value = { type: 'FLOAT', floatValue: userValue };
        break;
      case 'double':
        value = { type: 'DOUBLE', doubleValue: userValue };
        break;
      case 'uint32':
        value = { type: 'UINT32', uint32Value: userValue };
        break;
      case 'sint32':
        value = { type: 'SINT32', sint32Value: userValue };
        break;
      case 'uint64':
        value = { type: 'UINT64', uint64Value: userValue };
        break;
      case 'sint64':
        value = { type: 'SINT64', sint64Value: userValue };
        break;
      case 'string':
        value = { type: 'STRING', stringValue: userValue };
        break;
      case 'timestamp':
        value = { type: 'TIMESTAMP', timestampValue: Date.parse(userValue) };
        break;
      default:
        console.warn(`Unexpected type: ${userValue}`);
        return;
    }
    this.yamcs.getInstanceClient()!.setParameterValue('realtime', this.parameter.qualifiedName, value)
      .then(() => this.dialogRef.close());
  }
}
