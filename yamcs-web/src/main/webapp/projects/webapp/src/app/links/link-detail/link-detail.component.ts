import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import {
  ActionInfo,
  AuthService,
  Link,
  MessageService,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { LinkStatusComponent } from '../link-status/link-status.component';
import { LinkService } from '../shared/link.service';

@Component({
  selector: 'app-link-detail',
  templateUrl: './link-detail.component.html',
  styleUrl: './link-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LinkStatusComponent, WebappSdkModule],
})
export class LinkDetailComponent {
  @Input()
  link: Link;

  constructor(
    private authService: AuthService,
    private yamcs: YamcsService,
    private messageService: MessageService,
    private linkService: LinkService,
  ) {}

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
  }

  enableLink() {
    this.yamcs.yamcsClient
      .enableLink(this.link.instance, this.link.name)
      .catch((err) => this.messageService.showError(err));
  }

  disableLink() {
    this.yamcs.yamcsClient
      .disableLink(this.link.instance, this.link.name)
      .catch((err) => this.messageService.showError(err));
  }

  resetCounters() {
    this.yamcs.yamcsClient
      .resetLinkCounters(this.link.instance, this.link.name)
      .catch((err) => this.messageService.showError(err));
  }

  runAction(action: ActionInfo) {
    this.linkService.runAction(this.link.name, action);
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
}
