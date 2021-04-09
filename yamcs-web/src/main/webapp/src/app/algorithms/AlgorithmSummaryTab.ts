import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AlgorithmStatus } from '../client';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './AlgorithmSummaryTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmSummaryTab {

  algorithm$: Promise<Algorithm>;
  status$: Promise<AlgorithmStatus | null>;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService) {
    const qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    const instance = this.yamcs.instance!;

    this.algorithm$ = yamcs.yamcsClient.getAlgorithm(instance, qualifiedName);

    if (this.yamcs.processor) {
      this.status$ = yamcs.yamcsClient.getAlgorithmStatus(instance, this.yamcs.processor, qualifiedName);
    } else {
      this.status$ = Promise.resolve(null);
    }
  }
}
