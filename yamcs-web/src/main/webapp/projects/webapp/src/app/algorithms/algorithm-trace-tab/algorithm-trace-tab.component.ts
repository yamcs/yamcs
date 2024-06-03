import { AfterViewInit, ChangeDetectionStrategy, Component, OnInit, input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlgorithmStatus, AlgorithmTrace, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  standalone: true,
  templateUrl: './algorithm-trace-tab.component.html',
  styleUrl: './algorithm-trace-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AlgorithmTraceTabComponent implements OnInit, AfterViewInit {

  qualifiedName = input.required<string>({ alias: 'algorithm' });

  algorithm$: Promise<Algorithm>;
  status$ = new BehaviorSubject<AlgorithmStatus | null>(null);
  trace$ = new BehaviorSubject<AlgorithmTrace | null>(null);

  section$ = new BehaviorSubject<string>('runs');

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
  ) { }

  ngOnInit(): void {
    this.algorithm$ = this.yamcs.yamcsClient.getAlgorithm(this.yamcs.instance!, this.qualifiedName());
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
        this.yamcs.instance!, this.yamcs.processor, this.qualifiedName()
      ).then(status => {
        this.status$.next(status);
      }).catch(err => {
        this.messageService.showError(err);
      });

      this.yamcs.yamcsClient.getAlgorithmTrace(
        this.yamcs.instance!, this.yamcs.processor, this.qualifiedName()
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
    this.yamcs.yamcsClient.startAlgorithmTrace(
      this.yamcs.instance!, this.yamcs.processor!, this.qualifiedName()
    ).then(() => {
      this.refreshData();
    }).catch(err => this.messageService.showError(err));
  }

  stopTrace() {
    this.yamcs.yamcsClient.stopAlgorithmTrace(
      this.yamcs.instance!, this.yamcs.processor!, this.qualifiedName()
    ).then(() => {
      this.refreshData();
      this.switchToSection('runs');
      this.router.navigate([], {
        replaceUrl: true,
        relativeTo: this.route,
        queryParams: { section: null },
        queryParamsHandling: 'merge',
      });
    }).catch(err => this.messageService.showError(err));
  }
}
