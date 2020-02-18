import { Component, Input, OnDestroy } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Alarm } from '../client';
import { Synchronizer } from '../core/services/Synchronizer';

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
