import { Component } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConfigService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { ScriptViewer } from './ScriptViewer';

@Component({
  templateUrl: './ScriptViewerControls.html',
})
export class ScriptViewerControls {

  private bucket: string;

  initialized$ = new BehaviorSubject<boolean>(false);

  viewer: ScriptViewer;

  constructor(
    private snackbar: MatSnackBar,
    private authService: AuthService,
    configService: ConfigService,
  ) {
    this.bucket = configService.getDisplayBucket();
  }

  public init(viewer: ScriptViewer) {
    this.viewer = viewer;
    this.initialized$.next(true);
  }

  mayManageDisplays() {
    const user = this.authService.getUser()!;
    return user.hasObjectPrivilege('ManageBucket', this.bucket)
      || user.hasSystemPrivilege('ManageAnyBucket');
  }

  save() {
    this.viewer.save().then(() => {
      this.snackbar.open('Changes saved', undefined, {
        duration: 1000,
      });
    }).catch(err => {
      this.snackbar.open('Failed to save changes: ' + err);
    });
  }
}
