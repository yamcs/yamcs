import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Parameter, ParameterType, Value } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../../shared/utils';

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
      case 'integer':
      case 'string':
        this.form = formBuilder.group({
          value: [null, Validators.required]
        });
        break;
      case 'timestamp':
        this.form = formBuilder.group({
          value: [null, Validators.required]
        });
        break;
      case 'enumeration':
        this.form = formBuilder.group({
          value: [null, Validators.required]
        });
        break;
      case 'aggregate':
        this.form = formBuilder.group({
          value: [null, Validators.pattern(/^\{.*\}$/)]
        });
        break;
      case 'array':
        this.form = formBuilder.group({
          value: [null, Validators.pattern(/^\[.*\]$/)]
        });
        break;
      default:
        throw new Error(`Unexpected type ${this.parameter.type!.engType}`);
    }
  }

  save() {
    const userValue = this.form.value.value;
    const value = this.toValue(userValue, this.parameter.type!);
    this.dialogRef.close(value);
  }

  private toValue(userValue: any, type: ParameterType): Value {
    switch (type.engType) {
      case 'boolean':
        return { type: 'BOOLEAN', booleanValue: (userValue === 'TRUE') };
      case 'float':
        return { type: 'FLOAT', floatValue: userValue };
      case 'double':
        return { type: 'DOUBLE', doubleValue: userValue };
      case 'enumeration':
        return { type: 'STRING', stringValue: userValue };
      case 'integer':
        return { type: 'SINT32', sint32Value: userValue };
      case 'string':
        return { type: 'STRING', stringValue: userValue };
      case 'timestamp':
        return { type: 'TIMESTAMP', stringValue: utils.toISOString(userValue) };
      case 'aggregate':
        // TODO use graphical value builder?
        const values: Value[] = [
          { type: 'STRING', stringValue: String(1) },
          { type: 'STRING', stringValue: String(2) },
          { type: 'STRING', stringValue: String(3) },
        ];
        return { type: 'AGGREGATE', aggregateValue: { name: ['member1', 'member2', 'member3'], value: values } };
      default:
        throw new Error(`Unexpected type: ${type.engType}`);
    }
  }
}
