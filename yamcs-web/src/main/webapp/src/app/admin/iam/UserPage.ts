import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { ExternalIdentity, UserInfo } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { ChangeUserPasswordDialog } from './ChangeUserPasswordDialog';

@Component({
  templateUrl: './UserPage.html',
  styleUrls: ['./UserPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserPage {

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
    this.dialog.open(ChangeUserPasswordDialog, {
      data: {
        user: this.user$.value,
      },
      width: '400px',
    });
  }
}
