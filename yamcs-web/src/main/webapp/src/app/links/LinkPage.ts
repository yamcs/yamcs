import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Instance, Link } from '../client';
import { AuthService } from '../core/services/AuthService';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './LinkPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkPage implements OnDestroy {

  instance: Instance;
  link$ = new BehaviorSubject<Link | null>(null);

  private linkSubscription: Subscription;

  constructor(
    private title: Title,
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private authService: AuthService,
  ) {
    this.instance = yamcs.getInstance()!;
    route.paramMap.subscribe(params => {
      const linkName = params.get('link')!;
      this.changeLink(linkName);
    });

    this.yamcs.getInstanceClient()!.getLinkUpdates().then(response => {
      this.linkSubscription = response.linkEvent$.subscribe(evt => {
        const link = this.link$.value;
        if (link && link.name === evt.linkInfo.name) {
          this.link$.next(evt.linkInfo);
        }
      });
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

  enableLink(name: string) {
    this.yamcs.getInstanceClient()!.enableLink(name);
  }

  disableLink(name: string) {
    this.yamcs.getInstanceClient()!.disableLink(name);
  }

  resetCounters(name: string) {
    this.yamcs.getInstanceClient()!.editLink(name, {
      resetCounters: true,
    });
  }

  ngOnDestroy() {
    if (this.linkSubscription) {
      this.linkSubscription.unsubscribe();
    }
    const instanceClient = this.yamcs.getInstanceClient();
    if (instanceClient) {
      instanceClient.unsubscribeLinkUpdates();
    }
  }
}
