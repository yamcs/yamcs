import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
} from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';
import { Observable } from 'rxjs/Observable';
import { TimeInfo } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Subscription } from 'rxjs/Subscription';

@Component({
  selector: 'app-processor-info',
  templateUrl: './ProcessorInfoComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorInfoComponent implements OnDestroy {

  time$ = new BehaviorSubject<TimeInfo | null>(null);
  timeSubscription: Subscription;

  connected$: Observable<boolean>;

  constructor(private yamcs: YamcsService) {
    this.yamcs.getSelectedInstance().getTimeUpdates().then(response => {
      this.timeSubscription = response.time$.subscribe(time => {
        this.time$.next(time);
      });
    });
    this.connected$ = this.yamcs.getSelectedInstance().connected$;
  }

  ngOnDestroy() {
    if (this.timeSubscription) {
      this.timeSubscription.unsubscribe();
    }
  }
}
