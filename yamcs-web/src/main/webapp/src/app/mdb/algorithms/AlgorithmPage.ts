import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './AlgorithmPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmPage {

  algorithm$: Promise<Algorithm>;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService, title: Title) {
    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.algorithm$ = yamcs.yamcsClient.getAlgorithm(this.yamcs.instance!, qualifiedName);
    this.algorithm$.then(algorithm => {
      title.setTitle(algorithm.name);
    });
  }
}
