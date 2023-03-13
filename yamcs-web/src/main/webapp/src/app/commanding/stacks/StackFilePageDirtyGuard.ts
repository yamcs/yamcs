import { Injectable } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { CanDeactivate } from '@angular/router';
import { BehaviorSubject, Observable, Observer, of } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService } from '../../core/services/ConfigService';
import { StackFilePage } from './StackFilePage';
import { StackFilePageDirtyDialog } from './StackFilePageDirtyDialog';

@Injectable()
export class StackFilePageDirtyGuard implements CanDeactivate<StackFilePage> {

  private bucket: string;

  // TODO this is just a workaround around the fact that our current version
  // of Angular seems to trigger our deactivate guard twice...
  private dialogOpen$ = new BehaviorSubject<boolean>(false);
  private dialogRef: MatDialogRef<StackFilePageDirtyDialog, any>;

  constructor(
    private dialog: MatDialog,
    private authService: AuthService,
    configService: ConfigService,
  ) {
    this.bucket = configService.getConfig().stackBucket;
  }

  canDeactivate(component: StackFilePage) {
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

    if (component.hasPendingChanges() && this.mayManageStacks()) {
      return Observable.create((observer: Observer<boolean>) => {
        this.dialogOpen$.next(true);
        this.dialogRef = this.dialog.open(StackFilePageDirtyDialog, {
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

  private mayManageStacks() {
    const user = this.authService.getUser()!;
    return user.hasObjectPrivilege('ManageBucket', this.bucket)
      || user.hasSystemPrivilege('ManageAnyBucket');
  }
}
