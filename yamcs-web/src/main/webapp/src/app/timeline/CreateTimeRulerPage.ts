import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './CreateTimeRulerPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateTimeRulerPage {

  form: FormGroup;

  constructor(
    title: Title,
    formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Configure Time Ruler');
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      timezone: ['UTC', [Validators.required]],
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: formValue.name,
      type: 'TIME_RULER',
      shared: true,
      properties: {
        timezone: formValue.timezone,
      }
    }).then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
