import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { ConfigService, ParameterList, Synchronizer, WebsiteConfig, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';

@Component({
  templateUrl: './ParameterListPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterListPage {

  config: WebsiteConfig;
  plist$ = new BehaviorSubject<ParameterList | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private authService: AuthService,
    private title: Title,
    private synchronizer: Synchronizer,
    configService: ConfigService,
  ) {
    this.config = configService.getConfig();

    route.paramMap.subscribe(params => {
      const plistId = params.get('list')!;
      this.changeList(plistId);
    });
  }

  mayManageParameterLists() {
    return this.authService.getUser()!.hasSystemPrivilege('ManageParameterLists');
  }

  private changeList(id: string) {
    this.yamcs.yamcsClient.getParameterList(this.yamcs.instance!, id).then(plist => {
      this.plist$.next(plist);
      this.title.setTitle(plist.name);
    });
  }
}
