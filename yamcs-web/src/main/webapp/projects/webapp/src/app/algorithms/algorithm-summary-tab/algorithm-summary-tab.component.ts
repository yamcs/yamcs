import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, input } from '@angular/core';
import { AlgorithmStatus, AlgorithmStatusSubscription, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AlgorithmDetailComponent } from '../algorithm-detail/algorithm-detail.component';

@Component({
  standalone: true,
  templateUrl: './algorithm-summary-tab.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlgorithmDetailComponent,
    WebappSdkModule,
  ],
})
export class AlgorithmSummaryTabComponent implements OnInit, OnDestroy {

  qualifiedName = input.required<string>({ alias: 'algorithm' });

  algorithm$: Promise<Algorithm>;
  status$ = new BehaviorSubject<AlgorithmStatus | null>(null);

  private algorithmStatusSubscription: AlgorithmStatusSubscription;

  constructor(readonly yamcs: YamcsService) {
  }

  ngOnInit(): void {
    const instance = this.yamcs.instance!;

    this.algorithm$ = this.yamcs.yamcsClient.getAlgorithm(instance, this.qualifiedName());

    if (this.yamcs.processor) {
      this.algorithmStatusSubscription = this.yamcs.yamcsClient.createAlgorithmStatusSubscription({
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor,
        name: this.qualifiedName(),
      }, status => this.status$.next(status));
    }
  }

  ngOnDestroy() {
    this.algorithmStatusSubscription?.cancel();
  }
}
