import { AfterViewInit, ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { AlgorithmStatus, AlgorithmTrace, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  templateUrl: './AlgorithmTraceTab.html',
  styleUrls: ['./AlgorithmTraceTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmTraceTab implements AfterViewInit {

  private qualifiedName: string;

  algorithm$: Promise<Algorithm>;
  status$ = new BehaviorSubject<AlgorithmStatus | null>(null);
  trace$ = new BehaviorSubject<AlgorithmTrace | null>(null);

  section$ = new BehaviorSubject<string>('runs');

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    readonly yamcs: YamcsService,
    title: Title,
    private messageService: MessageService,
  ) {
    this.qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.algorithm$ = yamcs.yamcsClient.getAlgorithm(this.yamcs.instance!, this.qualifiedName);
    this.algorithm$.then(algorithm => {
      title.setTitle(algorithm.name);
    });
    this.refreshData();
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('section')) {
      this.switchToSection(queryParams.get('section')!);
    }
  }

  switchToSection(section: string) {
    this.section$.next(section);
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: { section },
      queryParamsHandling: 'merge',
    });
  }

  refreshData() {
    if (this.yamcs.processor) {
      this.yamcs.yamcsClient.getAlgorithmStatus(
        this.yamcs.instance!, this.yamcs.processor, this.qualifiedName
      ).then(status => {
        this.status$.next(status);
      }).catch(err => {
        this.messageService.showError(err);
      });

      this.yamcs.yamcsClient.getAlgorithmTrace(
        this.yamcs.instance!, this.yamcs.processor, this.qualifiedName
      ).then(trace => {
        this.trace$.next(trace);
      }).catch(err => {
        this.messageService.showError(err);
      });
    } else {
      this.status$.next(null);
      this.trace$.next(null);
    }
  }

  startTrace() {
    const qualifiedName = this.route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.yamcs.yamcsClient.startAlgorithmTrace(
      this.yamcs.instance!, this.yamcs.processor!, qualifiedName
    ).then(() => {
      this.refreshData();
    }).catch(err => {
      this.messageService.showError(err);
    });
  }

  stopTrace() {
    const qualifiedName = this.route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.yamcs.yamcsClient.stopAlgorithmTrace(
      this.yamcs.instance!, this.yamcs.processor!, qualifiedName
    ).then(() => {
      this.refreshData();
      this.switchToSection('runs');
      this.router.navigate([], {
        replaceUrl: true,
        relativeTo: this.route,
        queryParams: { section: null },
        queryParamsHandling: 'merge',
      });
    }).catch(err => {
      this.messageService.showError(err);
    });
  }
}
