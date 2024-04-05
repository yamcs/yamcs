import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Database, User, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { AuthService } from '../../../core/services/AuthService';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';

interface DatabaseObject {
  type: 'table' | 'stream';
  name: string;
}


@Component({
  standalone: true,
  templateUrl: './database.component.html',
  styleUrl: './database.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class DatabaseComponent implements OnDestroy {

  database$: Promise<Database>;
  object$ = new BehaviorSubject<DatabaseObject | null>(null);

  private user: User;
  private routerSubscription: Subscription;

  constructor(
    router: Router,
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    title: Title,
    authService: AuthService,
  ) {
    this.user = authService.getUser()!;
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
    this.routerSubscription?.unsubscribe();
  }
}
