import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  input,
} from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Algorithm, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './algorithm.component.html',
  styleUrl: './algorithm.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class AlgorithmComponent implements OnInit {
  qualifiedName = input.required<string>({ alias: 'algorithm' });

  algorithm$: Promise<Algorithm>;

  constructor(
    readonly yamcs: YamcsService,
    private title: Title,
  ) {}

  ngOnInit(): void {
    this.algorithm$ = this.yamcs.yamcsClient.getAlgorithm(
      this.yamcs.instance!,
      this.qualifiedName(),
    );
    this.algorithm$.then((algorithm) => {
      this.title.setTitle(algorithm.name);
    });
  }
}
