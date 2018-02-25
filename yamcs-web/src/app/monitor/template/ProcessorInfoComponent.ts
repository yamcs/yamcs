import {
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';
import { Observable } from 'rxjs/Observable';
import { TimeInfo } from '../../../yamcs-client';

@Component({
  selector: 'app-processor-info',
  templateUrl: './ProcessorInfoComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorInfoComponent {

  time$: Observable<TimeInfo>;
  connected$: Observable<boolean>;

  constructor(private yamcs: YamcsService) {
    this.time$ = this.yamcs.getSelectedInstance().getTimeUpdates();
    this.connected$ = this.yamcs.getSelectedInstance().connected$;
  }
}
