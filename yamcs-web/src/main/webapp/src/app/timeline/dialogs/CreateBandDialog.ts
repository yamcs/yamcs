import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';
import tznames from '../../shared/tznames';

@Component({
  templateUrl: './CreateBandDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateBandDialog {

  typeOptions: Option[] = [
    { id: 'timescale', label: 'Time Ruler' },
    { id: 'eventBand', label: 'Event Band' },
  ];

  tzOptions: Option[] = [];

  typeForm: FormGroup;

  timescaleForm: FormGroup;
  eventBandForm: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<CreateBandDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    private messageService: MessageService,
  ) {
    for (const tz of tznames) {
      this.tzOptions.push({ id: tz, label: tz });
    }
    this.typeForm = formBuilder.group({
      type: ['timescale', [Validators.required]]
    });
    this.timescaleForm = formBuilder.group({
      name: ['', [Validators.required]],
      timezone: ['', [Validators.required]],
    });
    this.eventBandForm = formBuilder.group({
      name: ['', [Validators.required]],
    });
  }

  save() {
    /*this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: this.timescaleForm.value['name'],
      type: 'TIME_RULER',
      extra: {
        timezone: this.timescaleForm.value['timezone'],
      }
    }).then(item => this.dialogRef.close(item))
      .catch(err => this.messageService.showError(err));*/
  }
}
