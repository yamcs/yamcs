import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Container, Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ContainerPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerPage {

  instance: Instance;
  container$ = new BehaviorSubject<Container | null>(null);

  constructor(route: ActivatedRoute, private yamcs: YamcsService, private title: Title) {
    this.instance = yamcs.getInstance();

    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeContainer(qualifiedName);
    });
  }

  changeContainer(qualifiedName: string) {
    this.yamcs.getInstanceClient()!.getContainer(qualifiedName).then(container => {
      this.container$.next(container);
      this.title.setTitle(container.name + ' - Yamcs');
    });
  }
}
