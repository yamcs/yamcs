import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Algorithm, Instance } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './AlgorithmsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmsPage {

  instance: Instance;
  algorithms$: Promise<Algorithm[]>;

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Algorithms - Yamcs');
    this.instance = yamcs.getInstance();
    this.algorithms$ = yamcs.getInstanceClient()!.getAlgorithms();
  }
}
