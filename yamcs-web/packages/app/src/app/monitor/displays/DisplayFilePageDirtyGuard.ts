import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material';
import { CanDeactivate } from '@angular/router';
import { Observable, of } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { DisplayFilePage } from './DisplayFilePage';
import { DisplayFilePageDirtyDialog } from './DisplayFilePageDirtyDialog';

@Injectable()
export class DisplayFilePageDirtyGuard implements CanDeactivate<DisplayFilePage> {

  constructor(private dialog: MatDialog, private authService: AuthService) {
  }

  canDeactivate(component: DisplayFilePage): Observable<boolean> {
    if (component.hasPendingChanges() && this.mayManageDisplays()) {
      const dialogRef = this.dialog.open(DisplayFilePageDirtyDialog, {
        width: '400px',
      });
      return dialogRef.afterClosed();
    } else {
      return of(true);
    }
  }

  private mayManageDisplays() {
    const user = this.authService.getUser()!;
    return user.hasObjectPrivilege('ManageBucket', 'displays')
      || user.hasSystemPrivilege('ManageAnyBucket');
  }
}
