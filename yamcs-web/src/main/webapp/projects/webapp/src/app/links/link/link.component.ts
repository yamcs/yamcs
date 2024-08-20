import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { ActionInfo, Cop1Config, Cop1Status, Cop1Subscription, InitiateCop1Request, Link, LinkSubscription, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { InitiateCop1DialogComponent } from '../initiate-cop1-dialog/initiate-cop1-dialog.component';
import { LinkStatusComponent } from '../link-status/link-status.component';
import { LinkService } from '../shared/link.service';

@Component({
  standalone: true,
  templateUrl: './link.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    LinkStatusComponent,
    WebappSdkModule,
  ],
})
export class LinkComponent implements OnDestroy {

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
    private linkService: LinkService,
  ) {
    route.paramMap.subscribe(params => {
      const linkName = params.get('link')!;
      this.changeLink(linkName);
    });

    this.linkSubscription = this.yamcs.yamcsClient.createLinkSubscription({
      instance: this.yamcs.instance!,
    }, evt => {
      for (const linkInfo of evt.links || []) {
        const link = this.link$.value;
        if (link && link.name === linkInfo.name) {
          this.link$.next(linkInfo);
        }
      }
    });
  }

  private changeLink(name: string) {
    this.cop1Subscription?.cancel();

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
    const dialogRef = this.dialog.open(InitiateCop1DialogComponent, { width: '400px' });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.initiateCop1(link, result);
      }
    });
  }

  private initiateCop1(link: string, options: InitiateCop1Request) {
    this.yamcs.yamcsClient.initiateCop1(this.yamcs.instance!, link, options)
      .catch(err => this.messageService.showError(err));
  }

  disableCop1(link: string) {
    this.yamcs.yamcsClient.disableCop1(this.yamcs.instance!, link)
      .catch(err => this.messageService.showError(err));
  }

  resumeCop1(link: string) {
    this.yamcs.yamcsClient.resumeCop1(this.yamcs.instance!, link)
      .catch(err => this.messageService.showError(err));
  }

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
  }

  enableLink(link: string) {
    this.yamcs.yamcsClient.enableLink(this.yamcs.instance!, link)
      .catch(err => this.messageService.showError(err));
  }

  disableLink(link: string) {
    this.yamcs.yamcsClient.disableLink(this.yamcs.instance!, link)
      .catch(err => this.messageService.showError(err));
  }

  resetCounters(link: string) {
    this.yamcs.yamcsClient.resetLinkCounters(this.yamcs.instance!, link)
      .catch(err => this.messageService.showError(err));
  }

  runAction(link: string, action: ActionInfo) {
    this.linkService.runAction(link, action);
  }

  getEntriesForValue(value: any) {
    if (value === undefined || value === null) {
      return [];
    }
    const entries: string[] = [];
    if (Array.isArray(value)) {
      for (let i = 0; i < value.length; i++) {
        if (typeof value[i] === 'object') {
          entries.push('' + JSON.stringify(value[i]));
        } else {
          entries.push('' + value[i]);
        }
      }
    } else {
      entries.push('' + value);
    }
    return entries;
  }

  ngOnDestroy() {
    this.linkSubscription?.cancel();
    this.cop1Subscription?.cancel();
  }
}
