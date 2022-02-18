import { Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
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

  form = new FormGroup({
    apid: new FormControl('', Validators.required),
    start: new FormControl('', Validators.required),
    stop: new FormControl('', Validators.required),
    link: new FormControl('', Validators.required),
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
