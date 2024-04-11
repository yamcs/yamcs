import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { CreateBandWizardStepComponent } from '../../create-band-wizard-step/create-band-wizard-step.component';

@Component({
  standalone: true,
  templateUrl: './create-command-band.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateBandWizardStepComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class CreateCommandBandComponent {

  form: UntypedFormGroup;

  constructor(
    title: Title,
    formBuilder: UntypedFormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Configure Command Band');
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      properties: formBuilder.group({})
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: formValue.name,
      description: formValue.description,
      type: 'COMMAND_BAND',
      shared: true,
      properties: formValue.properties,
    }).then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
