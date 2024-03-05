
import { Injectable, OnDestroy } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, Subscription, filter, map } from 'rxjs';

export interface SiteMessage {
  level: 'WARNING' | 'ERROR';
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class MessageService implements OnDestroy {

  /**
   * Active error message. Each message replaces
   * the previous one.
   */
  siteMessage$ = new BehaviorSubject<SiteMessage | null>(null);

  private routerSubscription: Subscription;

  constructor(
    private snackBar: MatSnackBar,
    router: Router,
  ) {
    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd),
      map(evt => this.dismiss()),
    ).subscribe();
  }

  showInfo(message: string) {
    this.snackBar.open(message, 'X', {
      horizontalPosition: 'end',
      duration: 3000,
    });
  }

  showWarning(message: string) {
    this.siteMessage$.next({ level: 'WARNING', message });
  }

  showError(error: string | Error) {
    this.siteMessage$.next({
      level: 'ERROR',
      message: (error instanceof Error) ? error.message : error,
    });
  }

  dismiss() {
    this.siteMessage$.next(null);
  }

  dismissSnackBar() {
    this.snackBar.dismiss();
  }

  ngOnDestroy() {
    this.routerSubscription?.unsubscribe();
  }
}
