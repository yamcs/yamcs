import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { BaseComponent, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { CreateBandWizardStepComponent } from '../../create-band-wizard-step/create-band-wizard-step.component';
import { propertyInfo } from '../Spacer';
import { SpacerStylesComponent } from '../spacer-styles/spacer-styles.component';

@Component({
  standalone: true,
  templateUrl: './create-spacer.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateBandWizardStepComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
    SpacerStylesComponent,
  ],
})
export class CreateSpacerComponent extends BaseComponent {

  form: FormGroup;

  constructor(
    formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
  ) {
    super();
    this.setTitle('Configure Spacer');
    this.form = formBuilder.group({
      name: '',
      description: '',
      properties: formBuilder.group({
        height: [propertyInfo.height.defaultValue, [Validators.required]],
      })
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: formValue.name,
      description: formValue.description,
      type: 'SPACER',
      shared: true,
      properties: formValue.properties,
    }).then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
