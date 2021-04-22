import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './CreateBandDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateBandDialog {

  form: FormGroup;

  constructor(
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
  ) {
    this.form = formBuilder.group({
      name: ['', Validators.required],
    });
  }

  save() {
    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: this.form.value['name'],
    });
  }
}
