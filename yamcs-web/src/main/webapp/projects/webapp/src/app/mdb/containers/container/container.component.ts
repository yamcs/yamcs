import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Container, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { ContainerDetailComponent } from '../container-detail/container-detail.component';

@Component({
  standalone: true,
  templateUrl: './container.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ContainerDetailComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ContainerComponent {

  container$ = new BehaviorSubject<Container | null>(null);

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService, private title: Title) {

    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeContainer(qualifiedName);
    });
  }

  changeContainer(qualifiedName: string) {
    this.yamcs.yamcsClient.getContainer(this.yamcs.instance!, qualifiedName).then(container => {
      this.container$.next(container);
      this.title.setTitle(container.name);
    });
  }
}
