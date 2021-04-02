import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { AlgorithmStatus } from '../client';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './AlgorithmPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmPage {

  algorithm$: Promise<Algorithm>;
  status$: Promise<AlgorithmStatus | null>;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService, title: Title) {
    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.algorithm$ = yamcs.yamcsClient.getAlgorithm(this.yamcs.instance!, qualifiedName);
    this.algorithm$.then(algorithm => {
      title.setTitle(algorithm.name);
    });

    if (this.yamcs.processor) {
      this.status$ = yamcs.yamcsClient.getAlgorithmStatus(this.yamcs.instance!, this.yamcs.processor, qualifiedName);
    } else {
      this.status$ = Promise.resolve(null);
    }
  }
}
