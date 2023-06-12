import { Transfer } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable } from 'rxjs';
import { distinctUntilChanged, map, sampleTime, startWith } from 'rxjs/operators';

export class TransferItem {

  // Not exposed to template (may update too fast)
  private originalTransfer$: BehaviorSubject<Transfer>;

  /**
   * Slowed down derivatives for use in templates
   */
  transfer$: Observable<Transfer>;
  state$: Observable<string>;

  constructor(public transfer: Transfer, public objectUrl: string) {
    this.originalTransfer$ = new BehaviorSubject<Transfer>(transfer);

    this.transfer$ = this.originalTransfer$.pipe(
      sampleTime(500),
      startWith(transfer),
    );
    this.state$ = this.originalTransfer$.pipe(
      map(t => t.state),
      distinctUntilChanged(),
    );
  }

  updateTransfer(transfer: Transfer) {
    this.transfer = transfer;
    this.originalTransfer$.next(transfer);
  }
}
