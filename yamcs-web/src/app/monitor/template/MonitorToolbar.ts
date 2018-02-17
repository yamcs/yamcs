import {
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';
import { Observable } from 'rxjs/Observable';
import { TimeInfo } from '../../../yamcs-client';

@Component({
  selector: 'app-monitor-toolbar',
  templateUrl: './MonitorToolbar.html',
  styleUrls: ['./MonitorToolbar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorToolbar {

  time$: Observable<TimeInfo>;

  constructor(private yamcs: YamcsService) {
    this.time$ = this.yamcs.getSelectedInstance().getTimeUpdates();
  }
}
