import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActionInfo, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { LinkActionDialogComponent } from '../link-action-dialog/link-action-dialog.component';

@Injectable({
  providedIn: 'root',
})
export class LinkService {

  private dialog = inject(MatDialog);
  private yamcs = inject(YamcsService);
  private messageService = inject(MessageService);
  private snackBar = inject(MatSnackBar);

  runAction(link: string, action: ActionInfo) {
    if (action.spec) {
      this.dialog.open(LinkActionDialogComponent, {
        data: { action },
        width: '600px',
      }).afterClosed().subscribe(result => {
        if (result) {
          this.submitRequest(link, action, result);
        }
      });
    } else {
      this.submitRequest(link, action);
    }
  }

  private submitRequest(link: string, action: ActionInfo, message?: { [key: string]: any; }) {
    this.snackBar.open(`Running '${action.label}' ...`, undefined, {
      horizontalPosition: 'end',
    });
    this.yamcs.yamcsClient.runLinkAction(this.yamcs.instance!, link, action.id, message)
      .then(() => {
        this.snackBar.open(`'${action.label}' successful`, undefined, {
          duration: 3000,
          horizontalPosition: 'end',
        });
      }).catch(err => {
        this.messageService.showError(err);
        this.snackBar.open(`'${action.label}' failed`, undefined, {
          duration: 3000,
          horizontalPosition: 'end',
        });
      });
  }
}
