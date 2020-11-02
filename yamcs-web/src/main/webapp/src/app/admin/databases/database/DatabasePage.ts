import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { Database } from '../../../client';
import { YamcsService } from '../../../core/services/YamcsService';

interface DatabaseObject {
  type: 'table' | 'stream';
  name: string;
}

@Component({
  templateUrl: './DatabasePage.html',
  styleUrls: ['./DatabasePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatabasePage implements OnDestroy {

  database$: Promise<Database>;
  object$ = new BehaviorSubject<DatabaseObject | null>(null);

  private routerSubscription: Subscription;

  constructor(
    router: Router,
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    title: Title,
  ) {
    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe((evt: NavigationEnd) => {
      const activeChild = route.snapshot.firstChild!;
      let objectName = activeChild.paramMap.get('table');
      if (objectName) {
        this.object$.next({ type: 'table', name: objectName });
        return;
      }

      objectName = activeChild.paramMap.get('stream');
      if (objectName) {
        this.object$.next({ type: 'stream', name: objectName });
        return;
      }

      this.object$.next(null);
    });
    const name = route.snapshot.paramMap.get('database')!;
    title.setTitle(name);
    this.database$ = yamcs.yamcsClient.getDatabase(name);
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}
