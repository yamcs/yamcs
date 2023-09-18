import { Injectable, inject } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';

import { CanDeactivateFn } from '@angular/router';
import { ConfigService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable, Observer, of } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { DisplayFilePage } from './DisplayFilePage';
import { DisplayFilePageDirtyDialog } from './DisplayFilePageDirtyDialog';

export const displayFilePageDirtyGuardFn: CanDeactivateFn<DisplayFilePage> = (component: DisplayFilePage) => {
  return inject(DisplayFilePageDirtyGuard).canDeactivate(component);
};

@Injectable()
export class DisplayFilePageDirtyGuard {

  private bucket: string;

  // TODO this is just a workaround around the fact that our current version
  // of Angular seems to trigger our deactivate guard twice...
  private dialogOpen$ = new BehaviorSubject<boolean>(false);
  private dialogRef: MatDialogRef<DisplayFilePageDirtyDialog, any>;

  constructor(
    private dialog: MatDialog,
    private authService: AuthService,
    configService: ConfigService,
  ) {
    this.bucket = configService.getDisplayBucket();
  }

  canDeactivate(component: DisplayFilePage) {
    // Copy the result of the first triggered dialog
    if (this.dialogOpen$.value) {
      return Observable.create((observer: Observer<boolean>) => {
        this.dialogRef.afterClosed().subscribe(result => {
          observer.next(result === true);
          observer.complete();
        }, err => {
          observer.next(false);
          observer.complete();
        });
      });
    }

    if (component.hasPendingChanges() && this.mayManageDisplays()) {
      return Observable.create((observer: Observer<boolean>) => {
        this.dialogOpen$.next(true);
        this.dialogRef = this.dialog.open(DisplayFilePageDirtyDialog, {
          width: '400px',
        });
        this.dialogRef.afterClosed().subscribe(result => {
          this.dialogOpen$.next(false);
          observer.next(result === true);
          observer.complete();
        }, err => {
          this.dialogOpen$.next(false);
          observer.next(false);
          observer.complete();
        });
      });
    } else {
      return of(true);
    }
  }

  private mayManageDisplays() {
    const user = this.authService.getUser()!;
    return user.hasObjectPrivilege('ManageBucket', this.bucket)
      || user.hasSystemPrivilege('ManageAnyBucket');
  }
}
