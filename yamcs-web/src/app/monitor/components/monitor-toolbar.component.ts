import {
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';
import { YamcsService } from '../../core/services/yamcs.service';
import { Observable } from 'rxjs/Observable';
import { TimeInfo } from '../../../yamcs-client';

@Component({
  selector: 'app-monitor-toolbar',
  templateUrl: './monitor-toolbar.component.html',
  styleUrls: ['./monitor-toolbar.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorToolbarComponent {

  time$: Observable<TimeInfo>;

  constructor(private yamcs: YamcsService) {
    this.time$ = this.yamcs.getSelectedInstance().getTimeUpdates();
  }
}
