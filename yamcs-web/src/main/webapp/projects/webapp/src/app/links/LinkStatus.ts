import { ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { Link, OFF_COLOR, ON_COLOR, Synchronizer } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';

const EXPIRY = 2000;

@Component({
  selector: 'app-link-status',
  templateUrl: './LinkStatus.html',
  styleUrls: ['./LinkStatus.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkStatus implements OnChanges, OnDestroy {

  @Input()
  link: Link;

  @Input()
  parentLink: Link;

  private prevInCount = -1;
  private prevOutCount = -1;
  active$ = new BehaviorSubject<boolean>(false);
  okColor$ = this.active$.pipe(
    map(active => active ? ON_COLOR : OFF_COLOR),
  );
  private activeExpiration = -1;

  private syncSubscription: Subscription;

  constructor(synchronizer: Synchronizer) {
    this.syncSubscription = synchronizer.syncFast(() => {
      const now = new Date().getTime();
      if (now >= this.activeExpiration) {
        this.active$.next(false);
        this.activeExpiration = -1;
      }
    });
  }

  ngOnChanges() {
    if (this.link.status === 'OK') {
      const activeIn = this.prevInCount !== -1 && this.prevInCount < this.link.dataInCount;
      const activeOut = this.prevOutCount !== -1 && this.prevOutCount < this.link.dataOutCount;
      if (activeIn || activeOut) {
        this.active$.next(true);
        this.activeExpiration = new Date().getTime() + EXPIRY;
      }
    } else {
      this.active$.next(false);
      this.activeExpiration = -1;
    }

    this.prevInCount = this.link.dataInCount;
    this.prevOutCount = this.link.dataOutCount;
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
