import { Component } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConfigService, WebappSdkModule } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../../../core/services/AuthService';
import { ScriptViewerComponent } from '../script-viewer/script-viewer.component';

@Component({
  standalone: true,
  templateUrl: './script-viewer-controls.component.html',
  imports: [
    WebappSdkModule,
  ]
})
export class ScriptViewerControlsComponent {

  private bucket: string;

  initialized$ = new BehaviorSubject<boolean>(false);

  viewer: ScriptViewerComponent;

  constructor(
    private snackbar: MatSnackBar,
    private authService: AuthService,
    configService: ConfigService,
  ) {
    this.bucket = configService.getDisplayBucket();
  }

  public init(viewer: ScriptViewerComponent) {
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
