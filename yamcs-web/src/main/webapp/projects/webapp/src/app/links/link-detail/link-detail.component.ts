import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Link, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { AuthService } from '../../core/services/AuthService';
import { SharedModule } from '../../shared/SharedModule';
import { LinkStatusComponent } from '../link-status/link-status.component';

@Component({
  standalone: true,
  selector: 'app-link-detail',
  templateUrl: './link-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    LinkStatusComponent,
    SharedModule,
  ],
})
export class LinkDetailComponent {

  @Input()
  link: Link;

  constructor(
    private authService: AuthService,
    private yamcs: YamcsService,
    private messageService: MessageService,
  ) { }

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
  }

  enableLink() {
    this.yamcs.yamcsClient.enableLink(this.link.instance, this.link.name)
      .catch(err => this.messageService.showError(err));
  }

  disableLink() {
    this.yamcs.yamcsClient.disableLink(this.link.instance, this.link.name)
      .catch(err => this.messageService.showError(err));
  }

  resetCounters() {
    this.yamcs.yamcsClient.resetLinkCounters(this.link.instance, this.link.name)
      .catch(err => this.messageService.showError(err));
  }

  runAction(action: string) {
    this.yamcs.yamcsClient.runLinkAction(this.link.instance, this.link.name, action)
      .catch(err => this.messageService.showError(err));
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
