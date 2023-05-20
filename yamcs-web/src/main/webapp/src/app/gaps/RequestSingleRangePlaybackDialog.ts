import { Component, Inject } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { Gap, PlaybackRange } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { Option } from '../shared/forms/Select';

@Component({
  templateUrl: './RequestSingleRangePlaybackDialog.html',
})
export class RequestSingleRangePlaybackDialog {

  gaps: Gap[];
  linkOptions$ = new BehaviorSubject<Option[]>([]);

  form = new UntypedFormGroup({
    apid: new UntypedFormControl('', Validators.required),
    start: new UntypedFormControl('', Validators.required),
    stop: new UntypedFormControl('', Validators.required),
    link: new UntypedFormControl('', Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<RequestSingleRangePlaybackDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const gap: Gap = this.data.gap;
    if (gap) {
      this.form.setValue({
        apid: gap.apid,
        start: gap.start,
        stop: gap.stop,
        link: '',
      });
    }

    this.yamcs.yamcsClient.getLinks(yamcs.instance!).then(links => {
      const linkOptions = [];
      for (const link of links) {
        if (link.type.indexOf('DassPlaybackPacketProvider') !== -1) {
          linkOptions.push({
            id: link.name,
            label: link.name,
          });
        }
      }
      this.linkOptions$.next(linkOptions);
      if (linkOptions.length) {
        this.form.get('link')!.setValue(linkOptions[0].id);
      }
    });
  }

  sendRequest() {
    const range: PlaybackRange = {
      apid: Number(this.form.value['apid']),
      start: this.form.value['start'],
      stop: this.form.value['stop'],
    };

    this.dialogRef.close({
      link: this.form.value['link'],
      ranges: [range],
    });
  }
}
