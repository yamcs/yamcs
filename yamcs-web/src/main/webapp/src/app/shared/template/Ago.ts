import { ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Synchronizer } from '../../core/services/Synchronizer';
import { AgoPipe } from '../pipes/AgoPipe';

@Component({
  selector: 'app-ago',
  template: '{{ value$ | async }}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Ago implements OnChanges, OnDestroy {

  @Input()
  time: string;

  value$ = new BehaviorSubject<string | null>(null);
  timerSubscription: Subscription;

  constructor(synchronizer: Synchronizer, private agoPipe: AgoPipe) {
    this.timerSubscription = synchronizer.sync(() => {
      this.value$.next(agoPipe.transform(this.time));
    });
  }

  ngOnChanges() {
    this.value$.next(this.agoPipe.transform(this.time));
  }

  ngOnDestroy() {
    if (this.timerSubscription) {
      this.timerSubscription.unsubscribe();
    }
  }
}
