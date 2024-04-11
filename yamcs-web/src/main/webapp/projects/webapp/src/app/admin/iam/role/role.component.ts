import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { RoleInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './role.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class RoleComponent {

  role$ = new BehaviorSubject<RoleInfo | null>(null);

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private title: Title,
  ) {
    route.paramMap.subscribe(params => {
      const name = params.get('name')!;
      this.changeRole(name);
    });
  }

  private changeRole(name: string) {
    this.yamcs.yamcsClient.getRole(name).then(role => {
      this.role$.next(role);
      this.title.setTitle(role.name);
    });
  }
}
