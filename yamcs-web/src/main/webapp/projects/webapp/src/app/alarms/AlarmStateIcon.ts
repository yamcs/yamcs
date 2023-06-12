import { Component, Input, OnDestroy } from '@angular/core';
import { Alarm, Synchronizer } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  selector: 'app-alarm-state-icon',
  templateUrl: './AlarmStateIcon.html',
})
export class AlarmStateIcon implements OnDestroy {

  @Input()
  alarm: Alarm;

  private syncSubscription: Subscription;
  visibility$ = new BehaviorSubject<boolean>(true);

  constructor(synchronizer: Synchronizer) {
    this.syncSubscription = synchronizer.syncFast(() => {
      this.visibility$.next(!this.visibility$.value);
    });
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
