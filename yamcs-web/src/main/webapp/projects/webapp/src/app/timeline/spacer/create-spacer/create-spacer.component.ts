import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../../shared/SharedModule';
import { CreateBandWizardStepComponent } from '../../create-band-wizard-step/create-band-wizard-step.component';
import { propertyInfo } from '../Spacer';
import { SpacerStylesComponent } from '../spacer-styles/spacer-styles.component';

@Component({
  standalone: true,
  templateUrl: './create-spacer.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateBandWizardStepComponent,
    SharedModule,
    SpacerStylesComponent,
  ],
})
export class CreateSpacerComponent {

  form: UntypedFormGroup;

  constructor(
    title: Title,
    formBuilder: UntypedFormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Configure Spacer');
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
