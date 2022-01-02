
import { Injectable, OnDestroy } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, filter, map, Subscription } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class MessageService implements OnDestroy {

  /**
   * Active error message. Each message replaces
   * the previous one.
   */
  errorMessage$ = new BehaviorSubject<string | null>(null);

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

  showError(error: string | Error) {
    if (error instanceof Error) {
      this.errorMessage$.next(error.message);
    } else {
      this.errorMessage$.next(error);
    }
  }

  dismiss() {
    this.errorMessage$.next(null);
    this.snackBar.dismiss();
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}
