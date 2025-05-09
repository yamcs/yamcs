import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import {
  AuthService,
  ConfigService,
  ParameterList,
  WebappSdkModule,
  WebsiteConfig,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  templateUrl: './parameter-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ParameterListComponent {
  config: WebsiteConfig;
  plist$ = new BehaviorSubject<ParameterList | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private authService: AuthService,
    private title: Title,
    configService: ConfigService,
  ) {
    this.config = configService.getConfig();

    route.paramMap.subscribe((params) => {
      const plistId = params.get('list')!;
      this.changeList(plistId);
    });
  }

  mayManageParameterLists() {
    return this.authService
      .getUser()!
      .hasSystemPrivilege('ManageParameterLists');
  }

  private changeList(id: string) {
    this.yamcs.yamcsClient
      .getParameterList(this.yamcs.instance!, id)
      .then((plist) => {
        this.plist$.next(plist);
        this.title.setTitle(plist.name);
      });
  }
}
