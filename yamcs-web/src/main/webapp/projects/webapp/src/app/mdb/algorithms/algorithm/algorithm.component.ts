import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Algorithm, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AlgorithmDetailComponent } from '../algorithm-detail/algorithm-detail.component';

@Component({
  templateUrl: './algorithm.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AlgorithmDetailComponent, WebappSdkModule],
})
export class AlgorithmComponent {
  algorithm$: Promise<Algorithm>;

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    title: Title,
  ) {
    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.algorithm$ = yamcs.yamcsClient.getAlgorithm(
      this.yamcs.instance!,
      qualifiedName,
    );
    this.algorithm$.then((algorithm) => {
      title.setTitle(algorithm.name);
    });
  }
}
