import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { TimelineBand } from '../client/types/timeline';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './CreateViewPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateViewPage {

  form: FormGroup;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private formBuilder: FormBuilder,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Create a View');
    this.form = this.formBuilder.group({
      name: ['', [Validators.required]],
      bands: [[], []],
    });
  }

  onConfirm() {
    const formValue = this.form.value;
    this.yamcs.yamcsClient.createTimelineView(this.yamcs.instance!, {
      name: formValue.name,
      bands: formValue.bands.map((v: TimelineBand) => v.id),
    }).then(() => this.router.navigateByUrl(`/timeline/views?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
