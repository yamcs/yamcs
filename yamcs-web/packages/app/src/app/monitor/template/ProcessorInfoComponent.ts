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

  timeInfo$ = new BehaviorSubject<TimeInfo | null>(null);
  timeInfoSubscription: Subscription;

  connected$: Observable<boolean>;

  constructor(private yamcs: YamcsService) {
    this.yamcs.getInstanceClient()!.getTimeUpdates().then(response => {
      this.timeInfo$.next(response.timeInfo);
      this.timeInfoSubscription = response.timeInfo$.subscribe(timeInfo => {
        this.timeInfo$.next(timeInfo);
      });
    });
    this.connected$ = this.yamcs.getInstanceClient()!.connected$;
  }

  ngOnDestroy() {
    if (this.timeInfoSubscription) {
      this.timeInfoSubscription.unsubscribe();
    }
  }
}
