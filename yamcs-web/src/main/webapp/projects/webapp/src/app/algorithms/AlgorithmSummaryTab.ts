import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AlgorithmStatus, AlgorithmStatusSubscription, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  templateUrl: './AlgorithmSummaryTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmSummaryTab implements OnDestroy {

  algorithm$: Promise<Algorithm>;
  status$ = new BehaviorSubject<AlgorithmStatus | null>(null);

  private algorithmStatusSubscription: AlgorithmStatusSubscription;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService) {
    const qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    const instance = this.yamcs.instance!;

    this.algorithm$ = yamcs.yamcsClient.getAlgorithm(instance, qualifiedName);

    if (this.yamcs.processor) {
      this.algorithmStatusSubscription = yamcs.yamcsClient.createAlgorithmStatusSubscription({
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor,
        name: qualifiedName,
      }, status => this.status$.next(status));
    }
  }

  ngOnDestroy() {
    if (this.algorithmStatusSubscription) {
      this.algorithmStatusSubscription.cancel();
    }
  }
}
