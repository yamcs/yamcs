import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance, Container } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './ContainerPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerPage {

  instance: Instance;
  container$: Promise<Container>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, title: Title) {
    this.instance = yamcs.getInstance();

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.container$ = yamcs.getInstanceClient()!.getContainer(qualifiedName);
    this.container$.then(container => {
      title.setTitle(container.name + ' - Yamcs');
    });
  }
}
