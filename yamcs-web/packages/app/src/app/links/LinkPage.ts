import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Link } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../core/services/AuthService';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './LinkPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkPage {

  link$ = new BehaviorSubject<Link | null>(null);

  constructor(
    private title: Title,
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private authService: AuthService,
  ) {
    route.paramMap.subscribe(params => {
      const linkName = params.get('link')!;
      this.changeLink(linkName);
    });
  }

  private changeLink(name: string) {
    this.yamcs.getInstanceClient()!.getLink(name).then(link => {
      this.link$.next(link);
      this.title.setTitle(name);
    });
  }

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
  }
}
