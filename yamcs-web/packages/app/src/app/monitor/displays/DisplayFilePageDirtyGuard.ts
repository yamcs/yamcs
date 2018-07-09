import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material';
import { CanDeactivate } from '@angular/router';
import { Observable, of } from 'rxjs';
import { DisplayFilePage } from './DisplayFilePage';
import { DisplayFilePageDirtyDialog } from './DisplayFilePageDirtyDialog';

@Injectable()
export class DisplayFilePageDirtyGuard implements CanDeactivate<DisplayFilePage> {

  constructor(private dialog: MatDialog) {
  }

  canDeactivate(component: DisplayFilePage): Observable<boolean> {
    if (component.hasPendingChanges()) {
      const dialogRef = this.dialog.open(DisplayFilePageDirtyDialog, {
        width: '400px',
      });
      return dialogRef.afterClosed();
    } else {
      return of(true);
    }
  }
}
