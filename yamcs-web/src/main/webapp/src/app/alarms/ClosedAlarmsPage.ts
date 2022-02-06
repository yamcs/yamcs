import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './ClosedAlarmsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClosedAlarmsPage {

  constructor(
    readonly yamcs: YamcsService,
  ) {
    yamcs.yamcsClient.getAlarms(yamcs.instance!, {
    }).then(page => {
      console.log('alarms', page);
    });
  }
}
