import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Cop1Config, Cop1Status, Cop1Subscription, InitiateCop1Request, Link, LinkSubscription } from '../client';
import { AuthService } from '../core/services/AuthService';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { InitiateCop1Dialog } from './InitiateCop1Dialog';

@Component({
  templateUrl: './LinkPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkPage implements OnDestroy {

  link$ = new BehaviorSubject<Link | null>(null);
  cop1Config$ = new BehaviorSubject<Cop1Config | null>(null);
  cop1Status$ = new BehaviorSubject<Cop1Status | null>(null);

  private linkSubscription: LinkSubscription;
  private cop1Subscription: Cop1Subscription;

  constructor(
    private title: Title,
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private authService: AuthService,
    private messageService: MessageService,
    private dialog: MatDialog,
  ) {
    route.paramMap.subscribe(params => {
      const linkName = params.get('link')!;
      this.changeLink(linkName);
    });

    this.linkSubscription = this.yamcs.yamcsClient.createLinkSubscription({
      instance: this.yamcs.instance!,
    }, evt => {
      const link = this.link$.value;
      if (link && link.name === evt.linkInfo.name) {
        this.link$.next(evt.linkInfo);
      }
    });
  }

  private changeLink(name: string) {
    if (this.cop1Subscription) {
      this.cop1Subscription.cancel();
    }

    this.cop1Status$.next(null);
    this.cop1Config$.next(null);

    this.yamcs.yamcsClient.getLink(this.yamcs.instance!, name).then(link => {
      this.link$.next(link);
      this.title.setTitle(name);
      if (link.type.indexOf('Cop1Tc') !== -1) {
        this.yamcs.yamcsClient.getCop1Config(this.yamcs.instance!, name).then(cop1Config => {
          this.cop1Config$.next(cop1Config);
        });

        this.cop1Subscription = this.yamcs.yamcsClient.createCop1Subscription({
          instance: link.instance,
          link: name,
        }, status => {
          this.cop1Status$.next(status);
        });
      }
    });
  }

  openInitiateCop1Dialog(link: string) {
    const dialogRef = this.dialog.open(InitiateCop1Dialog, { width: '400px' });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.initiateCop1(link, result);
      }
    });
  }

  private initiateCop1(link: string, options: InitiateCop1Request) {
    this.yamcs.yamcsClient.initiateCop1(this.yamcs.instance!, link, options).catch(err => {
      this.messageService.showError(err);
    });
  }

  disableCop1(link: string) {
    this.yamcs.yamcsClient.disableCop1(this.yamcs.instance!, link).catch(err => {
      this.messageService.showError(err);
    });
  }

  resumeCop1(link: string) {
    this.yamcs.yamcsClient.resumeCop1(this.yamcs.instance!, link).catch(err => {
      this.messageService.showError(err);
    });
  }

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
  }

  enableLink(link: string) {
    this.yamcs.yamcsClient.enableLink(this.yamcs.instance!, link);
  }

  disableLink(link: string) {
    this.yamcs.yamcsClient.disableLink(this.yamcs.instance!, link);
  }

  resetCounters(link: string) {
    this.yamcs.yamcsClient.editLink(this.yamcs.instance!, link, {
      resetCounters: true,
    });
  }

  ngOnDestroy() {
    if (this.linkSubscription) {
      this.linkSubscription.cancel();
    }
    if (this.cop1Subscription) {
      this.cop1Subscription.cancel();
    }
  }
}
