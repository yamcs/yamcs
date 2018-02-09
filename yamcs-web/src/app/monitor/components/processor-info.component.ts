import {
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';
import { YamcsService } from '../../core/services/yamcs.service';
import { Observable } from 'rxjs/Observable';
import { TimeInfo } from '../../../yamcs-client';

@Component({
  selector: 'app-processor-info',
  templateUrl: './processor-info.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorInfoComponent {

  time$: Observable<TimeInfo>;

  constructor(private yamcs: YamcsService) {
    this.time$ = this.yamcs.getSelectedInstance().getTimeUpdates();
  }
}
