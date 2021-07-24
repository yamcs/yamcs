import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { Option } from '../shared/forms/Select';
import tznames from '../shared/tznames';

@Component({
  templateUrl: './CreateBandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateBandPage {

  typeOptions: Option[] = [
    { id: 'timescale', label: 'Time Ruler' },
    { id: 'eventBand', label: 'Event Band' },
  ];

  tzOptions: Option[] = [];

  typeForm: FormGroup;

  timescaleForm: FormGroup;
  eventBandForm: FormGroup;

  constructor(
    title: Title,
    formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Create a Band');
    for (const tz of tznames) {
      this.tzOptions.push({ id: tz, label: tz });
    }
    this.typeForm = formBuilder.group({
      type: ['timescale', [Validators.required]]
    });
    this.timescaleForm = formBuilder.group({
      name: ['', [Validators.required]],
      timezone: ['UTC', [Validators.required]],
    });
    this.eventBandForm = formBuilder.group({
      name: ['', [Validators.required]],
    });
  }

  onConfirm() {
    const formValue = this.timescaleForm.value;

    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: formValue.name,
      type: 'TIME_RULER',
      extra: {
        timezone: formValue.timezone,
      }
    }).then(() => this.router.navigateByUrl(`/timeline/chart?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
