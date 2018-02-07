import {
  ChangeDetectionStrategy,
  Component,
  Input,
} from '@angular/core';
import { YamcsService } from '../../core/services/yamcs.service';
import { Observable } from 'rxjs/Observable';
import { TimeInfo } from '../../../yamcs-client';

@Component({
  selector: 'app-monitor-toolbar',
  templateUrl: './monitor-toolbar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorToolbarComponent {

  @Input()
  header: string;

  time$: Observable<TimeInfo>;

  constructor(private yamcs: YamcsService) {
    this.time$ = this.yamcs.getSelectedInstance().getTimeUpdates();
  }
}
