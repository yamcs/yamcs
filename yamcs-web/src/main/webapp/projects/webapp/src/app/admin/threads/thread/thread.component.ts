import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { ThreadInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AdminPageComponent } from '../../shared/admin-page/admin-page.component';
import { AppAdminToolbarLabel } from '../../shared/admin-toolbar/admin-toolbar-label.directive';
import { AppAdminToolbar } from '../../shared/admin-toolbar/admin-toolbar.component';
import { TraceElementComponent } from '../trace-element/trace-element.component';

@Component({
  templateUrl: './thread.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageComponent,
    AppAdminToolbar,
    AppAdminToolbarLabel,
    WebappSdkModule,
    TraceElementComponent,
  ],
})
export class ThreadComponent {
  thread$ = new BehaviorSubject<ThreadInfo | null>(null);

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private title: Title,
  ) {
    route.paramMap.subscribe((params) => {
      const id = params.get('id')!;
      this.changeThread(Number(id));
    });
  }

  private changeThread(id: number) {
    this.yamcs.yamcsClient.getThread(id).then((thread) => {
      this.thread$.next(thread);
      this.title.setTitle(thread.name);
    });
  }
}
