import { Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { Gap, PlaybackRange } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { Option } from '../shared/forms/Select';
import * as utils from '../shared/utils';

@Component({
  selector: 'app-request-playback-dialog',
  templateUrl: './RequestPlaybackDialog.html',
})
export class RequestPlaybackDialog {

  gaps: Gap[];
  linkOptions$ = new BehaviorSubject<Option[]>([]);

  form = new FormGroup({
    mergeTolerance: new FormControl(30),
    link: new FormControl('', Validators.required)
  });

  constructor(
    private dialogRef: MatDialogRef<RequestPlaybackDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.gaps = this.data.gaps;

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
    const ranges = [];
    const rangeCache = new Map<number, PlaybackRange>();
    const tolerance = this.form.value['mergeTolerance'] * 60 * 1000;

    for (const gap of this.gaps) {
      const prev = rangeCache.get(gap.apid);
      if (prev && (this.toMillis(gap.start) - this.toMillis(prev.stop)) < tolerance) {
        prev.stop = gap.stop;
      } else {
        const range = {
          apid: gap.apid,
          start: gap.start,
          stop: gap.stop,
        };
        ranges.push(range);
        rangeCache.set(gap.apid, range);
      }
    }

    this.dialogRef.close({
      link: this.form.value['link'],
      ranges,
    });
  }

  private toMillis(dateString: string) {
    return utils.toDate(dateString).getTime();
  }
}
