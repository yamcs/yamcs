import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { Title } from '@angular/platform-browser';

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
