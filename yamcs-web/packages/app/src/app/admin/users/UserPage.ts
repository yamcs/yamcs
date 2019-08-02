import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { UserInfo } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './UserPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserPage {

  user$ = new BehaviorSubject<UserInfo | null>(null);

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private title: Title,
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
      this.title.setTitle(user.username);
    });
  }
}
