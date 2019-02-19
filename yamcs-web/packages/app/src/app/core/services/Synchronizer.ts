import { Injectable } from '@angular/core';
import { Observable, Subscription, timer } from 'rxjs';
import { share } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class Synchronizer {

  /**
   * Shared observable that emits every second.
   */
  private everySecond$: Observable<number>;
  private everyHalfSecond$: Observable<number>;

  constructor() {
    this.everySecond$ = timer(1000, 1000).pipe(
      share(),
    );
    this.everyHalfSecond$ = timer(500, 500).pipe(
      share(),
    );
  }

  /**
   * Execute a function every second.
   */
  sync(fn: () => void): Subscription {
    return this.everySecond$.subscribe(fn);
  }

  /**
   * Execute a function every 500ms.
   * Avoid doing expensive work at this rate.
   */
  syncFast(fn: () => void): Subscription {
    return this.everyHalfSecond$.subscribe(fn);
  }
}
