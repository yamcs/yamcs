import { ChangeDetectionStrategy, Component } from '@angular/core';
import {
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators,
} from '@angular/forms';
import { BaseComponent, WebappSdkModule } from '@yamcs/webapp-sdk';
import { CreateBandWizardStepComponent } from '../../create-band-wizard-step/create-band-wizard-step.component';

@Component({
  templateUrl: './create-time-ruler.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CreateBandWizardStepComponent, WebappSdkModule],
})
export class CreateTimeRulerComponent extends BaseComponent {
  form: UntypedFormGroup;

  constructor(formBuilder: UntypedFormBuilder) {
    super();
    this.setTitle('Configure Time Ruler');

    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      properties: formBuilder.group({
        timezone: ['UTC', [Validators.required]],
      }),
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    this.yamcs.yamcsClient
      .createTimelineBand(this.yamcs.instance!, {
        name: formValue.name,
        description: formValue.description,
        type: 'TIME_RULER',
        shared: true,
        properties: formValue.properties,
      })
      .then(() =>
        this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`),
      )
      .catch((err) => this.messageService.showError(err));
  }
}
