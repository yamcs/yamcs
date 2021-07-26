import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { defaultProperties } from './SpacerStyles';

@Component({
  templateUrl: './CreateSpacerPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateSpacerPage {

  form: FormGroup;

  constructor(
    title: Title,
    formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Configure Spacer');
    this.form = formBuilder.group({
      name: '',
      description: '',
      properties: formBuilder.group({
        height: [defaultProperties.height, [Validators.required]],
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
