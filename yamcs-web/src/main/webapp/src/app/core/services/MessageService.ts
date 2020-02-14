
import { Injectable, OnDestroy } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { HttpError } from '../../client';

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
    if (error.hasOwnProperty('response')) {
      this.showHttpError((error as HttpError).response);
      return;
    }

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

  /**
   * Show a generic exception message in the snack bar if the response
   * is not in the 2xx range.
   *
   * Only use this if you are not doing any custom error handling yetUse this only if you want to make use of generic error handling.
   */
  private showHttpError(response: Response) {
    response.json().then(structuredError => {
      let msg = structuredError['msg'];
      if (!msg) {
        msg = response.statusText || `Error ${response.status}`;
      }
      this.showError(msg);
    }).catch(() => {
      const msg = response.statusText || `Error ${response.status}`;
      this.showError(msg);
    });
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}
