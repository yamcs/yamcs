import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, ValidatorFn, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { InitiateCop1Request } from '@yamcs/webapp-sdk';

const CombinedValidator: ValidatorFn = (form: UntypedFormGroup) => {
  const type = form.get('type')!.value;

  const clcwCheckTimeout = form.get('clcwCheckTimeout')!.value;
  const vr = form.get('vr')!.value;

  if (type == 'WITH_CLCW_CHECK' && (clcwCheckTimeout === null || '' === clcwCheckTimeout)) {
    return { combined: "CLCW Check Timeout must be specified" };
  } else if (type == 'SET_VR' && (vr === null || '' == vr)) {
    return { combined: "V(R) must be specified" };
  }
  return null;
};

@Component({
  selector: 'app-initiate-cop1-dialog',
  templateUrl: './InitiateCop1Dialog.html',
})
export class InitiateCop1Dialog {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<InitiateCop1Dialog>,
    formBuilder: UntypedFormBuilder,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form = formBuilder.group({
      type: ['WITH_CLCW_CHECK', Validators.required],
      clcwCheckTimeout: '3000',
      vr: null,
    }, {
      validators: [CombinedValidator],
    });
  }

  sendRequest() {
    const value = this.form.value;

    const options: InitiateCop1Request = {
      type: value['type']
    };

    if (options.type === 'WITH_CLCW_CHECK') {
      options.clcwCheckInitializeTimeout = Number(value['clcwCheckTimeout']);
    } else if (options.type === 'SET_VR') {
      options.vR = Number(value['vr']);
    }

    this.dialogRef.close(options);
  }
}
