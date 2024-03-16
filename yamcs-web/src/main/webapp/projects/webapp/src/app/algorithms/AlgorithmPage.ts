import { ChangeDetectionStrategy, Component, OnInit, input } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './AlgorithmPage.html',
  styleUrl: './AlgorithmPage.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmPage implements OnInit {

  qualifiedName = input.required<string>({ alias: 'algorithm' });

  algorithm$: Promise<Algorithm>;

  constructor(readonly yamcs: YamcsService, private title: Title) {
  }

  ngOnInit(): void {
    this.algorithm$ = this.yamcs.yamcsClient.getAlgorithm(this.yamcs.instance!, this.qualifiedName());
    this.algorithm$.then(algorithm => {
      this.title.setTitle(algorithm.name);
    });
  }
}
