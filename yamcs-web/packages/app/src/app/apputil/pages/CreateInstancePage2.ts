import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { InstanceTemplate } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './CreateInstancePage2.html',
  styleUrls: ['./CreateInstancePage2.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateInstancePage2 {

  form: FormGroup;
  template: InstanceTemplate;

  constructor(
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    private router: Router,
    title: Title,
    route: ActivatedRoute,
  ) {
    title.setTitle('Create an Instance - Yamcs');
    this.form = formBuilder.group({
      name: new FormControl('', [Validators.required])
    });

    const templateId = route.snapshot.paramMap.get('template')!;
    yamcs.yamcsClient.getInstanceTemplate(templateId).then(template => {
      this.template = template;
    });
  }

  onConfirm() {
    this.yamcs.yamcsClient.createInstance({
      name: this.form.get('name')!.value,
      template: this.template.name,
    });

    // Don't wait for request response (only returns after the instance has fully started)
    this.router.navigateByUrl('/');
  }
}
