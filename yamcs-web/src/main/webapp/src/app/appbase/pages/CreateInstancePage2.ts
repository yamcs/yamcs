import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { InstanceTemplate } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './CreateInstancePage2.html',
  styleUrls: ['./CreateInstancePage2.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateInstancePage2 {

  form: FormGroup;
  template$ = new BehaviorSubject<InstanceTemplate | null>(null);

  constructor(
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    private router: Router,
    private messageService: MessageService,
    title: Title,
    route: ActivatedRoute,
  ) {
    title.setTitle('Create an Instance');
    this.form = formBuilder.group({
      name: new FormControl('', [Validators.required])
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

        this.form.addControl(variable.name, new FormControl(initialValue, validators));
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
