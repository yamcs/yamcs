import { Clipboard } from '@angular/cdk/clipboard';
import { Component, input, OnDestroy, OnInit, viewChild } from '@angular/core';
import {
  FormControl,
  UntypedFormControl,
  UntypedFormGroup,
} from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import {
  BaseComponent,
  GetPacketsOptions,
  Packet,
  PacketArchiveInfo,
  PacketTimeBasis,
  ParseFilterSubscription,
  utils,
  WebappSdkModule,
  YaColumnInfo,
  YaSearchFilter2,
  YaSelectOption,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { HexComponent } from '../../../shared/hex/hex.component';
import { CreatePacketQueryDialogComponent } from '../create-packet-query-dialog/create-packet-query-dialog.component';
import { PacketsPageTabsComponent } from '../packets-page-tabs/packets-page-tabs.component';
import { PACKET_COMPLETIONS } from './completions';
import { PacketDownloadLinkPipe } from './packet-download-link.pipe';
import { PacketsDataSource } from './packets.datasource';

const defaultInterval = 'PT1H';

@Component({
  templateUrl: './packet-list.component.html',
  styleUrl: './packet-list.component.css',
  imports: [
    HexComponent,
    PacketDownloadLinkPipe,
    PacketsPageTabsComponent,
    WebappSdkModule,
  ],
})
export class PacketListComponent
  extends BaseComponent
  implements OnInit, OnDestroy
{
  filter = input<string>();
  name = input<string>();
  link = input<string>();
  interval = input<string>();
  customStart = input<string>();
  customStop = input<string>();
  timeBasis = input<string>();
  order = input<string>();

  // From resolver
  parseFilterSubscription = input.required<ParseFilterSubscription>();

  columns: YaColumnInfo[] = [
    { id: 'packetName', label: 'Packet name', alwaysVisible: true },
    { id: 'generationTime', label: 'Generation time', alwaysVisible: true },
    { id: 'earthReceptionTime', label: 'Earth reception time', visible: false },
    { id: 'receptionTime', label: 'Reception time', visible: true },
    { id: 'sequenceNumber', label: 'Sequence number', visible: false },
    { id: 'link', label: 'Link', visible: true },
    { id: 'size', label: 'Size', visible: true },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  searchFilter = viewChild.required<YaSearchFilter2>('searchFilter');
  completions = PACKET_COMPLETIONS;

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new UntypedFormGroup({
    filter: new FormControl<string | null>(null),
    name: new UntypedFormControl('ANY'),
    link: new UntypedFormControl('ANY'),
    interval: new FormControl<string | null>(defaultInterval),
    customStart: new FormControl<string | null>(null),
    customStop: new FormControl<string | null>(null),
    timeBasis: new FormControl<PacketTimeBasis>('GENERATION_TIME'),
    order: new FormControl<'asc' | 'desc'>('desc'),
  });

  dataSource: PacketsDataSource;

  detailPacket$ = new BehaviorSubject<Packet | null>(null);

  nameOptions$ = new BehaviorSubject<YaSelectOption[]>([
    { id: 'ANY', label: 'Any name' },
  ]);

  linkOptions$ = new BehaviorSubject<YaSelectOption[]>([
    { id: 'ANY', label: 'Any link' },
  ]);

  downloadURL$ = new BehaviorSubject<string | null>(null);
  archiveInfo$ = new BehaviorSubject<PacketArchiveInfo | null>(null);
  private statusTimer?: ReturnType<typeof setTimeout>;
  private destroyed = false;
  private queryGeneration = 0;
  private subscriptions = new Subscription();

  constructor(
    private dialog: MatDialog,
    private route: ActivatedRoute,
    private clipboard: Clipboard,
  ) {
    super();
    this.setTitle('Packets');

    this.dataSource = new PacketsDataSource(this.yamcs, this.synchronizer);

    this.yamcs.yamcsClient
      .getPacketNames(this.yamcs.instance!)
      .then((message) => {
        for (const name of message.packets || []) {
          this.nameOptions$.next([
            ...this.nameOptions$.value,
            {
              id: name,
              label: name,
            },
          ]);
        }
        for (const name of message.links || []) {
          this.linkOptions$.next([
            ...this.linkOptions$.value,
            {
              id: name,
              label: name,
            },
          ]);
        }
      });
  }

  ngOnInit(): void {
    this.parseFilterSubscription().addMessageListener((data) => {
      if (data.errorMessage) {
        this.searchFilter().addErrorMark(data.errorMessage, {
          beginLine: data.beginLine!,
          beginColumn: data.beginColumn!,
          endLine: data.endLine!,
          endColumn: data.endColumn!,
        });
      } else {
        this.searchFilter().clearErrorMark();
      }
    });

    this.initializeOptions();
    this.loadData();
    this.refreshArchiveInfo();

    for (const control of ['timeBasis', 'order']) {
      this.subscriptions.add(
        this.filterForm
          .get(control)!
          .valueChanges.subscribe(() => this.loadData()),
      );
    }

    this.filterForm.get('filter')!.valueChanges.forEach((filter) => {
      this.loadData();
    });

    this.filterForm.get('name')!.valueChanges.forEach((name) => {
      this.loadData();
    });

    this.filterForm.get('link')!.valueChanges.forEach((link) => {
      this.loadData();
    });

    this.filterForm.get('interval')!.valueChanges.forEach((nextInterval) => {
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || this.yamcs.getMissionTime();
        const customStop = this.validStop || this.yamcs.getMissionTime();
        this.filterForm
          .get('customStart')!
          .setValue(utils.toISOString(customStart));
        this.filterForm
          .get('customStop')!
          .setValue(utils.toISOString(customStop));
      } else if (nextInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
        this.appliedInterval = nextInterval;
        this.loadData();
      } else {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
    this.filterForm.patchValue({
      timeBasis:
        this.timeBasis() === 'RECEPTION_TIME'
          ? 'RECEPTION_TIME'
          : 'GENERATION_TIME',
      order: this.order() === 'asc' ? 'asc' : 'desc',
    });
    if (this.filter()) {
      const filter = this.filter()!;
      this.filterForm.get('filter')!.setValue(filter);
    }
    if (this.name()) {
      const name = this.name()!;
      this.filterForm.get('name')!.setValue(name);
    }
    if (this.link()) {
      const link = this.link()!;
      this.filterForm.get('link')!.setValue(link);
    }
    if (this.interval()) {
      this.appliedInterval = this.interval()!;
      this.filterForm.get('interval')!.setValue(this.appliedInterval);
      if (this.appliedInterval === 'CUSTOM') {
        const customStart = this.customStart()!;
        this.filterForm.get('customStart')!.setValue(customStart);
        this.validStart = utils.toDate(customStart);
        const customStop = this.customStop()!;
        this.filterForm.get('customStop')!.setValue(customStop);
        this.validStop = utils.toDate(customStop);
      } else if (this.appliedInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
      } else {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(
          this.validStop,
          this.appliedInterval,
        );
      }
    } else {
      this.appliedInterval = defaultInterval;
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = utils.subtractDuration(this.validStop, defaultInterval);
    }
  }

  jumpToNow() {
    const interval = this.filterForm.value['interval'];
    if (interval === 'NO_LIMIT') {
      // NO_LIMIT may include future data under erratic conditions. Reverting
      // to the default interval is more in line with the wording 'jump to now'.
      this.filterForm.get('interval')!.setValue(defaultInterval);
    } else if (interval === 'CUSTOM') {
      // For simplicity reasons, just reset to default 1h interval.
      this.filterForm.get('interval')!.setValue(defaultInterval);
    } else {
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = utils.subtractDuration(this.validStop, interval);
      this.loadData();
    }
  }

  // Used in table trackBy to prevent continuous row recreation
  // tableTrackerFn = (index: number, entry: CommandHistoryEntry) => ;

  applyCustomDates() {
    const { controls } = this.filterForm;
    this.validStart = utils.toDate(controls['customStart'].value);
    this.validStop = utils.toDate(controls['customStop'].value);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  private queryOptions(): GetPacketsOptions {
    const { controls } = this.filterForm;
    const options: GetPacketsOptions = {
      timeBasis: controls['timeBasis'].value,
      order: controls['order'].value,
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    const filter = controls['filter'].value;
    if (filter) {
      options.filter = filter;
    }
    const name = controls['name'].value;
    if (name !== 'ANY') {
      options.name = [name];
    }
    const link = controls['link'].value;
    if (link !== 'ANY') {
      options.link = link;
    }

    return options;
  }

  loadData() {
    const generation = ++this.queryGeneration;
    this.updateURL();
    this.downloadURL$.next(null);
    const options = this.queryOptions();
    if (
      options.timeBasis === 'RECEPTION_TIME' &&
      this.archiveInfo$.value?.receptionTimeIndexState !== 'READY'
    ) {
      this.dataSource.clear();
      return;
    }

    this.dataSource
      .loadEntries('realtime', options)
      .then((current) => {
        if (!current || generation !== this.queryGeneration || this.destroyed) {
          return;
        }
        const downloadURL = this.yamcs.yamcsClient.getPacketsDownloadURL(
          this.yamcs.instance!,
          options,
        );
        this.downloadURL$.next(downloadURL);
      })
      .catch((err) => {
        if (!this.destroyed && generation === this.queryGeneration) {
          this.messageService.showError(err);
          if (options.timeBasis === 'RECEPTION_TIME') {
            this.refreshArchiveInfo();
          }
        }
      });
  }

  loadMoreData() {
    this.dataSource
      .loadMoreData()
      .catch((err) => this.messageService.showError(err));
  }

  private refreshArchiveInfo() {
    clearTimeout(this.statusTimer);
    this.yamcs.yamcsClient
      .getPacketArchiveInfo(this.yamcs.instance!)
      .then((info) => {
        if (this.destroyed) {
          return;
        }
        const wasReady =
          this.archiveInfo$.value?.receptionTimeIndexState === 'READY';
        this.archiveInfo$.next(info);
        if (
          !wasReady &&
          info.receptionTimeIndexState === 'READY' &&
          this.filterForm.value['timeBasis'] === 'RECEPTION_TIME'
        ) {
          this.loadData();
        }
        if (info.receptionTimeIndexState === 'BUILDING') {
          this.statusTimer = setTimeout(() => this.refreshArchiveInfo(), 5000);
        }
      })
      .catch((err) => {
        if (!this.destroyed) {
          this.messageService.showError(err);
        }
      });
  }

  copyHex(packet: Packet) {
    this.fetchPacket(packet)
      .then((packetDetail) => {
        const hex = utils.convertBase64ToHex(packetDetail.packet ?? '');
        if (this.clipboard.copy(hex)) {
          this.messageService.showInfo('Hex copied');
        } else {
          this.messageService.showInfo('Hex copy failed');
        }
      })
      .catch((err) => this.messageService.showError(err));
  }

  copyBinary(packet: Packet) {
    this.fetchPacket(packet)
      .then((packetDetail) => {
        const raw = window.atob(packetDetail.packet ?? '');
        if (this.clipboard.copy(raw)) {
          this.messageService.showInfo('Binary copied');
        } else {
          this.messageService.showInfo('Binary copy failed');
        }
      })
      .catch((err) => this.messageService.showError(err));
  }

  private updateURL() {
    const { controls } = this.filterForm;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: controls['filter'].value || null,
        name: controls['name'].value === 'ANY' ? null : controls['name'].value,
        link: controls['link'].value === 'ANY' ? null : controls['link'].value,
        interval: this.appliedInterval,
        timeBasis:
          controls['timeBasis'].value === 'GENERATION_TIME'
            ? null
            : controls['timeBasis'].value,
        order:
          controls['order'].value === 'desc' ? null : controls['order'].value,
        customStart:
          this.appliedInterval === 'CUSTOM'
            ? this.filterForm.value['customStart']
            : null,
        customStop:
          this.appliedInterval === 'CUSTOM'
            ? this.filterForm.value['customStop']
            : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  selectPacket(packet: Packet) {
    this.fetchPacket(packet)
      .then((packetDetail) => {
        this.detailPacket$.next(packetDetail);
        this.openDetailPane();
      })
      .catch((err) => this.messageService.showError(err));
  }

  private fetchPacket(packet: Packet) {
    // Fetch the full detail of a packet, which includes the binary
    return this.yamcs.yamcsClient.getPacket(
      this.yamcs.instance!,
      packet.id.name,
      packet.generationTime,
      packet.sequenceNumber,
    );
  }

  extractPacket(packet: Packet) {
    this.router.navigate(
      [
        '/telemetry/packets' + packet.id.name,
        '-',
        'log',
        packet.generationTime,
        packet.sequenceNumber,
      ],
      {
        queryParams: {
          c: this.yamcs.context,
        },
      },
    );
  }

  isSelected(packet: Packet) {
    const detail = this.detailPacket$.value;
    if (detail) {
      return (
        packet.id.name === detail.id.name &&
        packet.generationTime === detail.generationTime &&
        packet.sequenceNumber === detail.sequenceNumber
      );
    }
    return false;
  }

  clearQuery() {
    this.filterForm.reset(
      {
        filter: null,
        name: 'ANY',
        link: 'ANY',
        interval: defaultInterval,
        timeBasis: this.filterForm.value['timeBasis'],
        order: this.filterForm.value['order'],
      },
      { emitEvent: false },
    );
    this.appliedInterval = defaultInterval;
    this.validStop = this.yamcs.getMissionTime();
    this.validStart = utils.subtractDuration(this.validStop, defaultInterval);
    this.loadData();
  }

  openSaveQueryDialog() {
    const { controls } = this.filterForm;
    this.dialog
      .open(CreatePacketQueryDialogComponent, {
        width: '800px',
        data: {
          name: controls['name'].value,
          nameOptions: this.nameOptions$.value,
          link: controls['link'].value,
          linkOptions: this.linkOptions$.value,
          // Use currently typed value (even if not submitted)
          filter: this.searchFilter().getTypedValue(),
        },
      })
      .afterClosed()
      .subscribe((res) => {
        if (res) {
          this.messageService.showInfo('Query saved');
        }
      });
  }

  parseQuery(typedQuery: string) {
    this.parseFilterSubscription().sendMessage({
      resource: 'packets',
      filter: typedQuery,
    });
  }

  isClearQueryEnabled() {
    const fv = this.filterForm.value;
    return (
      this.searchFilter().empty() || fv.name !== 'ANY' || fv.link !== 'ANY'
    );
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    ++this.queryGeneration;
    clearTimeout(this.statusTimer);
    this.subscriptions.unsubscribe();
    this.parseFilterSubscription().cancel();
  }
}
