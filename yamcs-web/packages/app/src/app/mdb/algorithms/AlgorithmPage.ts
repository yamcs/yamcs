import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Instance } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './AlgorithmPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmPage {

  instance: Instance;
  algorithm$: Promise<Algorithm>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, title: Title) {
    this.instance = yamcs.getInstance();

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.algorithm$ = yamcs.getInstanceClient()!.getAlgorithm(qualifiedName);
    this.algorithm$.then(algorithm => {
      title.setTitle(algorithm.name + ' - Yamcs');
    });
  }
}
