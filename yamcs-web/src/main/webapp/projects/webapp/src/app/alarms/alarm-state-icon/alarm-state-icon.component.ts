import { Component, Input, OnDestroy } from '@angular/core';
import { Alarm, Synchronizer, WebappSdkModule } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-alarm-state-icon',
  templateUrl: './alarm-state-icon.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class AlarmStateIconComponent implements OnDestroy {

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
    this.syncSubscription?.unsubscribe();
  }
}
