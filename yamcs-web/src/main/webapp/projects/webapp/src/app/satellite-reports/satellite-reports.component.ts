import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  resource,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatTooltip } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  DateTimePipe,
  YaButton,
  YaDateTimeInput,
  YaInstancePage,
  YaInstanceToolbar,
  YaPageTabs,
  YaPanel,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { ReportClient } from './report-client';
import { ReportSnapshotComponent } from './report-snapshot.component';
import { ReportSnapshot, ReportTypeItem } from './report-types';
import {
  getPresetHistoryRange,
  HISTORY_INTERVALS,
  REPORT_FIELD_HELP,
  selectLatest,
  STATUS_LABELS,
} from './report-view-model';

const HISTORY_TIMESTAMP =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/;

interface HistoryState {
  reports: ReportSnapshot[];
  next?: string;
  loading: boolean;
  error?: unknown;
}

@Component({
  templateUrl: './satellite-reports.component.html',
  styleUrl: './satellite-reports.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DateTimePipe,
    MatTooltip,
    ReactiveFormsModule,
    ReportSnapshotComponent,
    RouterLink,
    YaButton,
    YaDateTimeInput,
    YaInstancePage,
    YaInstanceToolbar,
    YaPageTabs,
    YaPanel,
  ],
})
export class SatelliteReportsComponent {
  private yamcs = inject(YamcsService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private client = new ReportClient(this.yamcs.yamcsClient);

  private connection = toSignal(this.yamcs.connectionInfo$);
  private query = toSignal(this.route.queryParamMap, { requireSync: true });
  private routeParams = toSignal(this.route.paramMap, { requireSync: true });

  readonly view = this.route.snapshot.data['view'] as
    'latest' | 'history' | 'snapshot';
  readonly statusLabels = STATUS_LABELS;
  readonly fieldHelp = REPORT_FIELD_HELP;
  readonly historyIntervals = HISTORY_INTERVALS;
  readonly context = computed(() => this.query().get('c') ?? undefined);
  readonly start = computed(() => this.query().get('start') || undefined);
  readonly stop = computed(() => this.query().get('stop') || undefined);
  readonly historyInterval = computed(() => {
    const interval = this.query().get('interval');
    return (
      this.historyIntervals.find((option) => option.id === interval)?.id ??
      (this.start() || this.stop() ? 'CUSTOM' : 'NO_LIMIT')
    );
  });
  private instance = computed(() => this.connection()?.instance?.name);

  readonly catalog = resource({
    params: () => {
      const instance = this.instance();
      return instance ? { instance } : undefined;
    },
    loader: ({ params }) => this.client.listTypes(params.instance),
  });

  readonly snapshot = resource({
    params: () => {
      const instance = this.instance();
      const id = this.routeParams().get('id');
      return instance && id ? { instance, id } : undefined;
    },
    loader: ({ params }) => this.client.getReport(params.instance, params.id),
  });

  readonly groups = computed(() => {
    const groups = new Map<number, ReportTypeItem[]>();
    const types = this.catalog.hasValue() ? this.catalog.value() : [];
    for (const type of types) {
      const group = groups.get(type.service) ?? [];
      group.push(type);
      groups.set(type.service, group);
    }
    return Array.from(groups, ([service, types]) => ({ service, types }));
  });

  readonly selected = computed(() => {
    const type = this.snapshot.hasValue()
      ? this.snapshot.value().type
      : this.query().get('type');
    const types = this.catalog.hasValue() ? this.catalog.value() : [];
    return type ? types.find((item) => item.type === type) : types[0];
  });

  readonly scopeParams = computed(() => ({
    c: this.context(),
    type: this.selected()?.type ?? this.query().get('type'),
    interval: this.query().get('interval'),
    start: this.start(),
    stop: this.stop(),
  }));

  readonly latest = computed(() => {
    const selected = this.selected();
    return selected && selectLatest(selected);
  });

  readonly historyDates = computed(
    () =>
      new FormGroup({
        start: new FormControl(this.validDate(this.start())),
        stop: new FormControl(this.validDate(this.stop())),
      }),
  );

  readonly invalidHistoryDates = computed(() => {
    const start = this.start();
    const stop = this.stop();
    return !!(
      (start && !this.validDate(start)) ||
      (stop && !this.validDate(stop)) ||
      (start && stop && Date.parse(start) >= Date.parse(stop))
    );
  });

  private historyRequest = computed(
    () => {
      const instance = this.instance();
      const type = this.selected()?.type;
      if (
        this.view !== 'history' ||
        !instance ||
        !type ||
        this.invalidHistoryDates()
      ) {
        return undefined;
      }
      return {
        instance,
        filters: {
          type,
          start: this.start(),
          stop: this.stop(),
        },
      };
    },
    {
      equal: (a, b) =>
        a?.instance === b?.instance &&
        a?.filters.type === b?.filters.type &&
        a?.filters.start === b?.filters.start &&
        a?.filters.stop === b?.filters.stop,
    },
  );

  readonly history = resource({
    params: this.historyRequest,
    loader: ({ params }) =>
      this.client.listReports(params.instance, params.filters),
  });

  readonly historyState = linkedSignal<HistoryState>(() => {
    const page =
      this.history.hasValue() && !this.history.isLoading()
        ? this.history.value()
        : undefined;
    return {
      reports: page?.reports ?? [],
      next: page?.continuationToken,
      loading: false,
    };
  });

  refresh() {
    if (this.view === 'history' && this.historyRequest()) {
      const interval = this.historyInterval();
      if (interval === 'CUSTOM' || interval === 'NO_LIMIT') {
        this.history.reload();
      } else {
        this.applyHistoryInterval(interval);
      }
    } else if (this.view === 'snapshot') {
      this.snapshot.reload();
    } else {
      this.catalog.reload();
    }
  }

  selectType(type: string) {
    this.router.navigate(
      [
        this.view === 'history'
          ? '/satellite-reports/history'
          : '/satellite-reports',
      ],
      {
        queryParams: { ...this.scopeParams(), type },
      },
    );
  }

  applyHistoryInterval(interval: string) {
    const missionTime = this.yamcs.getMissionTime();
    const now = Number.isFinite(missionTime.getTime())
      ? missionTime
      : new Date();
    const range = getPresetHistoryRange(interval, now);
    if (
      interval === this.historyInterval() &&
      range &&
      range.start === this.start() &&
      range.stop === this.stop()
    ) {
      this.history.reload();
      return;
    }
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...this.scopeParams(), interval, ...range },
    });
  }

  applyDates() {
    const form = this.historyDates();
    if (form.invalid) {
      return;
    }
    const { start, stop } = form.getRawValue();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...this.scopeParams(), interval: 'CUSTOM', start, stop },
    });
  }

  async loadMore() {
    const request = this.historyRequest();
    const firstPage = this.history.hasValue() && this.history.value();
    const state = this.historyState();
    if (!request || !firstPage || !state.next || state.loading) {
      return;
    }
    this.historyState.set({ ...state, loading: true, error: undefined });
    try {
      const page = await this.client.listReports(
        request.instance,
        request.filters,
        state.next,
      );
      if (
        this.historyRequest() === request &&
        this.history.hasValue() &&
        this.history.value() === firstPage &&
        !this.history.isLoading()
      ) {
        this.historyState.set({
          reports: [...state.reports, ...(page.reports ?? [])],
          next: page.continuationToken,
          loading: false,
        });
      }
    } catch (error) {
      if (
        this.historyRequest() === request &&
        this.history.hasValue() &&
        this.history.value() === firstPage &&
        !this.history.isLoading()
      ) {
        this.historyState.set({ ...state, loading: false, error });
      }
    }
  }

  private validDate(value?: string) {
    return value &&
      HISTORY_TIMESTAMP.test(value) &&
      Number.isFinite(Date.parse(value))
      ? value
      : null;
  }
}
