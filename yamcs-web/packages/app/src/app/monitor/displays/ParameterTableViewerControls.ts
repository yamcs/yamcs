import { Component } from '@angular/core';
import { MatDialog, MatSnackBar } from '@angular/material';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { SelectParameterDialog } from '../../mdb/parameters/SelectParameterDialog';
import { ParameterTableViewer } from './ParameterTableViewer';

@Component({
  selector: 'app-parameter-table-viewer-controls',
  templateUrl: './ParameterTableViewerControls.html',
})
export class ParameterTableViewerControls {

  initialized$ = new BehaviorSubject<boolean>(false);

  viewer: ParameterTableViewer;

  constructor(
    private dialog: MatDialog,
    private snackbar: MatSnackBar,
    private authService: AuthService,
  ) {}

  public init(viewer: ParameterTableViewer) {
    this.viewer = viewer;
    this.initialized$.next(true);
  }

  addParameter() {
    const dialogRef = this.dialog.open(SelectParameterDialog, {
      width: '500px',
      data: {
        okLabel: 'Add',
        exclude: this.viewer.getParameterNames(),
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
    return user.hasObjectPrivilege('ManageBucket', 'displays')
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
