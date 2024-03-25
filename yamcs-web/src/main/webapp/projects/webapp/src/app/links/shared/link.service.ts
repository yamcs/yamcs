import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { LinkAction, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { LinkActionDialogComponent } from '../link-action-dialog/link-action-dialog.component';

@Injectable({
  providedIn: 'root',
})
export class LinkService {

  private dialog = inject(MatDialog);
  private yamcs = inject(YamcsService);
  private messageService = inject(MessageService);

  runAction(link: string, action: LinkAction) {
    if (action.spec) {
      this.dialog.open(LinkActionDialogComponent, {
        data: { action },
        width: '600px',
      }).afterClosed().subscribe(result => {
        if (result) {
          this.yamcs.yamcsClient.runLinkAction(this.yamcs.instance!, link, action.id, result)
            .catch(err => this.messageService.showError(err));
        }
      });
    } else {
      this.yamcs.yamcsClient.runLinkAction(this.yamcs.instance!, link, action.id)
        .catch(err => this.messageService.showError(err));
    }
  }
}
