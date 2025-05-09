import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { BaseComponent, WebappSdkModule } from '@yamcs/webapp-sdk';
import { CreateBandWizardStepComponent } from '../../create-band-wizard-step/create-band-wizard-step.component';
import { propertyInfo } from '../Spacer';
import { SpacerStylesComponent } from '../spacer-styles/spacer-styles.component';

@Component({
  templateUrl: './create-spacer.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateBandWizardStepComponent,
    WebappSdkModule,
    SpacerStylesComponent,
  ],
})
export class CreateSpacerComponent extends BaseComponent {
  form: FormGroup;

  constructor(formBuilder: FormBuilder) {
    super();
    this.setTitle('Configure Spacer');
    this.form = formBuilder.group({
      name: '',
      description: '',
      properties: formBuilder.group({
        height: [propertyInfo.height.defaultValue, [Validators.required]],
      }),
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    this.yamcs.yamcsClient
      .createTimelineBand(this.yamcs.instance!, {
        name: formValue.name,
        description: formValue.description,
        type: 'SPACER',
        shared: true,
        properties: formValue.properties,
      })
      .then(() =>
        this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`),
      )
      .catch((err) => this.messageService.showError(err));
  }
}
