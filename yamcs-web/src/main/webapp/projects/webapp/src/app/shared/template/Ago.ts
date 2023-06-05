import { ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { Synchronizer } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AgoPipe } from '../pipes/AgoPipe';

@Component({
  selector: 'app-ago',
  template: '{{ value$ | async }}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Ago implements OnChanges, OnDestroy {

  @Input()
  time: string;

  @Input()
  useMissionTime = true;

  value$ = new BehaviorSubject<string | null>(null);
  timerSubscription: Subscription;

  constructor(synchronizer: Synchronizer, private agoPipe: AgoPipe) {
    this.timerSubscription = synchronizer.sync(() => {
      this.value$.next(agoPipe.transform(this.time, this.useMissionTime));
    });
  }

  ngOnChanges() {
    this.value$.next(this.agoPipe.transform(this.time, this.useMissionTime));
  }

  ngOnDestroy() {
    if (this.timerSubscription) {
      this.timerSubscription.unsubscribe();
    }
  }
}
