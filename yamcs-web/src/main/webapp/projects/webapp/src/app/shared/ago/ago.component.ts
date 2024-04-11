import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { Synchronizer } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AgoPipe } from '../pipes/ago.pipe';

@Component({
  standalone: true,
  selector: 'app-ago',
  template: '{{ value$ | async }}',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AsyncPipe,
  ],
})
export class AgoComponent implements OnChanges, OnDestroy {

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
    this.timerSubscription?.unsubscribe();
  }
}
