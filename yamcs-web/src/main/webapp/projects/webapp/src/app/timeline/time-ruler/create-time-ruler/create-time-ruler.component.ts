import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { BaseComponent, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { CreateBandWizardStepComponent } from '../../create-band-wizard-step/create-band-wizard-step.component';

@Component({
  standalone: true,
  templateUrl: './create-time-ruler.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateBandWizardStepComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ]
})
export class CreateTimeRulerComponent extends BaseComponent {

  form: UntypedFormGroup;

  constructor(
    formBuilder: UntypedFormBuilder,
    readonly yamcs: YamcsService,
  ) {
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

    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: formValue.name,
      description: formValue.description,
      type: 'TIME_RULER',
      shared: true,
      properties: formValue.properties,
    }).then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
