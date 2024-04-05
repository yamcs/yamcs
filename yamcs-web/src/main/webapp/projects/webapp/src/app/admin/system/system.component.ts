import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { PluginInfo, SystemInfo, SystemInfoSubscription, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AdminPageTemplateComponent } from '../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './system.component.html',
  styleUrl: './system.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class SystemComponent implements OnDestroy {

  info$ = new BehaviorSubject<SystemInfo | null>(null);
  plugins$ = new BehaviorSubject<PluginInfo[]>([]);

  private systemInfoSubscription: SystemInfoSubscription;

  constructor(
    private yamcs: YamcsService,
    title: Title,
  ) {
    title.setTitle('System');
    this.yamcs.yamcsClient.getGeneralInfo().then(info => this.plugins$.next(info.plugins || []));
    this.systemInfoSubscription = yamcs.yamcsClient.createSystemInfoSubscription(info => {
      this.info$.next(info);
    });
  }

  ngOnDestroy() {
    this.systemInfoSubscription?.cancel();
  }
}
