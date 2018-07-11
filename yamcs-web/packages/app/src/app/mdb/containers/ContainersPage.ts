import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Container, Instance } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ContainersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainersPage {

  instance: Instance;
  containers$: Promise<Container[]>;

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Containers - Yamcs');
    this.instance = yamcs.getInstance();
    this.containers$ = yamcs.getInstanceClient()!.getContainers();
  }
}
