import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConfigService, WebappSdkModule } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../../../core/services/AuthService';
import { SelectParameterDialogComponent } from '../../../../shared/select-parameter-dialog/select-parameter-dialog.component';
import { ExportArchiveDataDialogComponent } from '../../export-archive-data-dialog/export-archive-data-dialog.component';
import { ParameterTableViewerComponent } from '../parameter-table-viewer/parameter-table-viewer.component';

@Component({
  standalone: true,
  selector: 'app-parameter-table-viewer-controls',
  templateUrl: './parameter-table-viewer-controls.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class ParameterTableViewerControlsComponent {

  private bucket: string;

  initialized$ = new BehaviorSubject<boolean>(false);

  viewer: ParameterTableViewerComponent;

  constructor(
    private dialog: MatDialog,
    private snackbar: MatSnackBar,
    private authService: AuthService,
    configService: ConfigService,
  ) {
    this.bucket = configService.getDisplayBucket();
  }

  public init(viewer: ParameterTableViewerComponent) {
    this.viewer = viewer;
    this.initialized$.next(true);
  }

  addParameter() {
    const dialogRef = this.dialog.open(SelectParameterDialogComponent, {
      width: '500px',
      data: {
        okLabel: 'ADD',
        exclude: this.viewer.getModel().parameters,
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.viewer.addParameter(result);
      }
    });
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

  exportArchiveData() {
    let parameterIds = this.viewer.selection.selected;
    if (!parameterIds.length) {
      parameterIds = this.viewer.getModel().parameters;
    }
    this.dialog.open(ExportArchiveDataDialogComponent, {
      width: '400px',
      data: {
        parameterIds,
      }
    });
  }
}
