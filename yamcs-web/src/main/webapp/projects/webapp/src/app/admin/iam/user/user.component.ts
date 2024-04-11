import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { ExternalIdentity, MessageService, UserInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';
import { ChangeUserPasswordDialogComponent } from '../change-user-password-dialog/change-user-password-dialog.component';

@Component({
  standalone: true,
  templateUrl: './user.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class UserComponent {

  user$ = new BehaviorSubject<UserInfo | null>(null);

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private title: Title,
    private dialog: MatDialog,
    private messageService: MessageService,
  ) {

    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe(params => {
      const username = params.get('username')!;
      this.changeUser(username);
    });
  }

  private changeUser(username: string) {
    this.yamcs.yamcsClient.getUser(username).then(user => {
      this.user$.next(user);
      this.title.setTitle(user.name);
    });
  }

  deleteIdentity(identity: ExternalIdentity) {
    if (confirm(`Are you sure you want to delete the ${identity.provider} identity?`)) {
      const username = this.user$.value!.name;
      this.yamcs.yamcsClient.deleteIdentity(username, identity.provider)
        .then(() => this.changeUser(username))
        .catch(err => this.messageService.showError(err));
    }
  }

  showChangeUserPasswordDialog() {
    this.dialog.open(ChangeUserPasswordDialogComponent, {
      data: {
        user: this.user$.value,
      },
      width: '400px',
    });
  }
}
