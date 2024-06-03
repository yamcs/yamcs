import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { ThreadInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';
import { TraceElementComponent } from '../trace-element/trace-element.component';

@Component({
  standalone: true,
  templateUrl: './thread.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
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

    route.paramMap.subscribe(params => {
      const id = params.get('id')!;
      this.changeThread(Number(id));
    });
  }

  private changeThread(id: number) {
    this.yamcs.yamcsClient.getThread(id).then(thread => {
      this.thread$.next(thread);
      this.title.setTitle(thread.name);
    });
  }
}
