import { ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy, inject } from '@angular/core';
import { Activity, Synchronizer, WebappSdkModule } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-activity-duration',
  template: '{{ elapsed$ | async | duration }}',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ActivityDurationComponent implements OnChanges, OnDestroy {

  private synchronizer = inject(Synchronizer);

  @Input()
  activity: Activity;

  elapsed$ = new BehaviorSubject<number>(0);

  private syncSubscription: Subscription;

  ngOnChanges() {
    this.syncSubscription?.unsubscribe();
    if (!this.activity) {
      return;
    }

    this.updateState();
    if (!this.activity.stop) {
      this.syncSubscription = this.synchronizer.sync(() => this.updateState());
    }
  }

  private updateState() {
    if (!this.activity.start) {
      return;
    }
    const dt1 = new Date(this.activity.start);
    if (this.activity.stop) {
      const dt2 = new Date(this.activity.stop);
      this.elapsed$.next(this.millisBetween(dt1, dt2));
    } else {
      const millis = this.millisBetween(dt1, new Date());
      this.elapsed$.next(millis);
    }
  }

  private millisBetween(dt1: Date, dt2: Date) {
    return dt2.getTime() - dt1.getTime();
  }

  ngOnDestroy() {
    this.syncSubscription?.unsubscribe();
  }
}
