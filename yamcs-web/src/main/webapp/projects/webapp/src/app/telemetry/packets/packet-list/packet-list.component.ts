import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { DownloadPacketsOptions, GetPacketsOptions, MessageService, Packet, Synchronizer, WebappSdkModule, YaColumnInfo, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { HexComponent } from '../../../shared/hex/hex.component';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { PacketDownloadLinkPipe } from './packet-download-link.pipe';
import { PacketsDataSource } from './packets.datasource';

const defaultInterval = 'PT1H';

@Component({
  standalone: true,
  templateUrl: './packet-list.component.html',
  styleUrl: './packet-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    HexComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    PacketDownloadLinkPipe,
    WebappSdkModule,
  ],
})
export class PacketListComponent {

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

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new UntypedFormGroup({
    name: new UntypedFormControl('ANY'),
    link: new UntypedFormControl('ANY'),
    interval: new UntypedFormControl(defaultInterval),
    customStart: new UntypedFormControl(null),
    customStop: new UntypedFormControl(null),
  });

  dataSource: PacketsDataSource;

  detailPacket$ = new BehaviorSubject<Packet | null>(null);

  nameOptions$ = new BehaviorSubject<YaSelectOption[]>([
    { id: 'ANY', label: 'Any name' },
  ]);

  linkOptions$ = new BehaviorSubject<YaSelectOption[]>([
    { id: 'ANY', label: 'Any link' },
  ]);

  intervalOptions: YaSelectOption[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  downloadURL$ = new BehaviorSubject<string | null>(null);

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private name: string;
  private link: string;

  constructor(
    readonly yamcs: YamcsService,
    private router: Router,
    private route: ActivatedRoute,
    title: Title,
    synchronizer: Synchronizer,
    private clipboard: Clipboard,
    private messageService: MessageService,
  ) {
    title.setTitle('Packets');

    this.dataSource = new PacketsDataSource(this.yamcs, synchronizer);

    yamcs.yamcsClient.getPacketNames(yamcs.instance!).then(message => {
      for (const name of message.packets || []) {
        this.nameOptions$.next([
          ...this.nameOptions$.value,
          {
            id: name,
            label: name,
          }
        ]);
      }
      for (const name of message.links || []) {
        this.linkOptions$.next([
          ...this.linkOptions$.value,
          {
            id: name,
            label: name,
          }
        ]);
      }
    });

    this.initializeOptions();
    this.loadData();

    this.filterForm.get('name')!.valueChanges.forEach(name => {
      this.name = (name !== 'ANY') ? name : null;
      this.loadData();
    });

    this.filterForm.get('link')!.valueChanges.forEach(link => {
      this.link = (link !== 'ANY') ? link : null;
      this.loadData();
    });

    this.filterForm.get('interval')!.valueChanges.forEach(nextInterval => {
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || this.yamcs.getMissionTime();
        const customStop = this.validStop || this.yamcs.getMissionTime();
        this.filterForm.get('customStart')!.setValue(utils.toISOString(customStart));
        this.filterForm.get('customStop')!.setValue(utils.toISOString(customStop));
      } else if (nextInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
        this.appliedInterval = nextInterval;
        this.loadData();
      } else {
        this.validStop = yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('name')) {
      this.name = queryParams.get('name') || '';
      this.filterForm.get('name')!.setValue(this.name);
    }
    if (queryParams.has('link')) {
      this.link = queryParams.get('link') || '';
      this.filterForm.get('link')!.setValue(this.link);
    }
    if (queryParams.has('interval')) {
      this.appliedInterval = queryParams.get('interval')!;
      this.filterForm.get('interval')!.setValue(this.appliedInterval);
      if (this.appliedInterval === 'CUSTOM') {
        const customStart = queryParams.get('customStart')!;
        this.filterForm.get('customStart')!.setValue(customStart);
        this.validStart = new Date(customStart);
        const customStop = queryParams.get('customStop')!;
        this.filterForm.get('customStop')!.setValue(customStop);
        this.validStop = new Date(customStop);
      } else if (this.appliedInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
      } else {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, this.appliedInterval);
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
    this.validStart = utils.toDate(this.filterForm.value['customStart']);
    this.validStop = utils.toDate(this.filterForm.value['customStop']);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  loadData() {
    this.updateURL();
    const options: GetPacketsOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.name) {
      options.name = this.name;
    }
    if (this.link) {
      options.link = this.link;
    }

    const dlOptions: DownloadPacketsOptions = {};
    if (this.validStart) {
      dlOptions.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      dlOptions.stop = this.validStop.toISOString();
    }
    if (this.name) {
      dlOptions.name = this.name;
    }
    if (this.link) {
      dlOptions.link = this.link;
    }

    this.dataSource.loadEntries('realtime', options).then(packets => {
      const downloadURL = this.yamcs.yamcsClient.getPacketsDownloadURL(this.yamcs.instance!, dlOptions);
      this.downloadURL$.next(downloadURL);
    });
  }

  loadMoreData() {
    const options: GetPacketsOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.name) {
      options.name = this.name;
    }
    if (this.link) {
      options.link = this.link;
    }

    this.dataSource.loadMoreData(options);
  }

  copyHex(packet: Packet) {
    this.fetchPacket(packet).then(packetDetail => {
      const hex = utils.convertBase64ToHex(packetDetail.packet ?? '');
      if (this.clipboard.copy(hex)) {
        this.messageService.showInfo('Hex copied');
      } else {
        this.messageService.showInfo('Hex copy failed');
      }
    }).catch(err => this.messageService.showError(err));
  }

  copyBinary(packet: Packet) {
    this.fetchPacket(packet).then(packetDetail => {
      const raw = window.atob(packetDetail.packet ?? '');
      if (this.clipboard.copy(raw)) {
        this.messageService.showInfo('Binary copied');
      } else {
        this.messageService.showInfo('Binary copy failed');
      }
    }).catch(err => this.messageService.showError(err));
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        name: this.name || null,
        link: this.link || null,
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStart'] : null,
        customStop: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStop'] : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  selectPacket(packet: Packet) {
    this.fetchPacket(packet).then(packetDetail => {
      this.detailPacket$.next(packetDetail);
    }).catch(err => this.messageService.showError(err));
  }

  private fetchPacket(packet: Packet) {
    // Fetch the full detail of a packet, which includes the binary
    return this.yamcs.yamcsClient.getPacket(
      this.yamcs.instance!, packet.id.name, packet.generationTime, packet.sequenceNumber);
  }

  extractPacket(packet: Packet) {
    this.router.navigate([
      '/telemetry/packets' + packet.id.name,
      '-',
      'log',
      packet.generationTime,
      packet.sequenceNumber,
    ], {
      queryParams: {
        c: this.yamcs.context,
      }
    });
  }

  isSelected(packet: Packet) {
    const detail = this.detailPacket$.value;
    if (detail) {
      return packet.id.name === detail.id.name
        && packet.generationTime === detail.generationTime
        && packet.sequenceNumber === detail.sequenceNumber;
    }
    return false;
  }
}
