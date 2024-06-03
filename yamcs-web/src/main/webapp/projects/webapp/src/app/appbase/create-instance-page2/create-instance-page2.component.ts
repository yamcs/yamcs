import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { InstanceTemplate, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { CreateInstanceWizardStepComponent } from '../create-instance-wizard-step/create-instance-wizard-step.component';

import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  templateUrl: './create-instance-page2.component.html',
  styleUrl: './create-instance-page2.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateInstanceWizardStepComponent,
    WebappSdkModule,
  ],
})
export class CreateInstancePage2Component {

  form: UntypedFormGroup;
  template$ = new BehaviorSubject<InstanceTemplate | null>(null);

  constructor(
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    private router: Router,
    private messageService: MessageService,
    title: Title,
    route: ActivatedRoute,
  ) {
    title.setTitle('Create an Instance');
    this.form = formBuilder.group({
      name: new UntypedFormControl('', [Validators.required])
    });

    const templateId = route.snapshot.paramMap.get('template')!;
    yamcs.yamcsClient.getInstanceTemplate(templateId).then(template => {
      this.template$.next(template);
      for (const variable of template.variables || []) {
        const validators = variable.required ? [Validators.required] : [];
        let initialValue = variable.choices ? variable.choices[0] : undefined;
        if (variable.initial !== undefined) {
          initialValue = variable.initial;
        }

        this.form.addControl(variable.name, new UntypedFormControl(initialValue, validators));
      }
    });
  }

  onConfirm() {
    const template = this.template$.value!;
    const templateArgs: { [key: string]: string; } = {};
    for (const variable of template.variables || []) {
      if (this.form.get(variable.name)!.value) {
        templateArgs[variable.name] = this.form.get(variable.name)!.value;
      }
    }

    this.yamcs.yamcsClient.createInstance({
      name: this.form.get('name')!.value,
      template: template.name,
      templateArgs,
    }).then(() => this.router.navigateByUrl('/'))
      .catch(err => this.messageService.showError(err));
  }
}
