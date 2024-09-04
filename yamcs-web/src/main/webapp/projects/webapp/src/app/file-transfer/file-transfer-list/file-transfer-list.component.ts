import { ChangeDetectionStrategy, Component, OnInit, Signal, computed, input, signal } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { FileTransferService, GetFileTransfersOptions, MessageService, Synchronizer, Transfer, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { FileTransferTabsComponent } from '../file-transfer-tabs/file-transfer-tabs.component';
import { TransferFileDialogComponent } from '../transfer-file-dialog/transfer-file-dialog.component';
import { FileTransferIconComponent } from './file-transfer-icon.component';
import { FileTransferDataSource } from './file-transfer.datasource';

const defaultInterval = 'NO_LIMIT';

@Component({
  standalone: true,
  selector: 'app-file-transfer-list',
  templateUrl: './file-transfer-list.component.html',
  styleUrl: './file-transfer-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FileTransferIconComponent,
    FileTransferTabsComponent,
    InstancePageTemplateComponent,
    InstanceToolbarComponent,
    WebappSdkModule,
  ],
})
export class FileTransferListComponent implements OnInit {

  services = input.required<FileTransferService[]>();
  requestedServiceName = input<string | null>(null, { alias: 'service' });
  requestedService = computed(() => {
    // Respect the requested service (from query param)
    // But default to the first if unspecified.
    let service = null;
    const services = this.services();
    for (const candidate of services) {
      if (this.requestedServiceName() === candidate.name) {
        service = candidate;
        break;
      }
    }
    if (!service && !this.requestedServiceName()) {
      service = services.length ? services[0] : null;
    }
    return service;
  });

  service = signal<FileTransferService | null>(null);

  localEntityOptions: Signal<YaSelectOption[]> = computed(() => {
    const options = [{ id: 'ANY', label: 'Any local entity' }];
    for (const entity of this.service()?.localEntities || []) {
      options.push({ id: String(entity.id), label: entity.name });
    }
    return options;
  });

  remoteEntityOptions: Signal<YaSelectOption[]> = computed(() => {
    const options = [{ id: 'ANY', label: 'Any remote entity' }];
    for (const entity of this.service()?.remoteEntities || []) {
      options.push({ id: String(entity.id), label: entity.name });
    }
    return options;
  });

  isIncomplete = (index: number, transfer: Transfer) => {
    return transfer.state !== 'COMPLETED';
  };

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new UntypedFormGroup({
    interval: new UntypedFormControl(defaultInterval),
    customStart: new UntypedFormControl(null),
    customStop: new UntypedFormControl(null),
    direction: new UntypedFormControl('ANY'),
    state: new UntypedFormControl([]),
    localEntityId: new UntypedFormControl('ANY'),
    remoteEntityId: new UntypedFormControl('ANY'),
  });

  intervalOptions: YaSelectOption[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  directionOptions: YaSelectOption[] = [
    { id: 'ANY', label: 'Any direction' },
    { id: 'DOWNLOAD', label: 'Download' },
    { id: 'UPLOAD', label: 'Upload' },
  ];

  stateOptions: YaSelectOption[] = [
    { id: 'QUEUED', label: 'Queued' },
    { id: 'RUNNING', label: 'Running' },
    { id: 'PAUSED', label: 'Paused' },
    { id: 'CANCELLING', label: 'Cancelling' },
    { id: 'FAILED', label: 'Failed' },
    { id: 'COMPLETED', label: 'Completed' },
  ];

  defaultColumns = [
    'status',
    'startTime',
    'localEntity',
    'localFile',
    'direction',
    'remoteEntity',
    'remoteFile',
    'size',
    'actions',
  ];

  displayedColumns$ = new BehaviorSubject<string[]>([]);

  dataSource: FileTransferDataSource;

  private state: string[] = [];
  private direction: string;
  private localEntityId: string;
  private remoteEntityId: string;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private router: Router,
    private route: ActivatedRoute,
    private messageService: MessageService,
    private authService: AuthService,
    private dialog: MatDialog,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('File transfer');
    const storageClient = yamcs.createStorageClient();
    this.dataSource = new FileTransferDataSource(yamcs, synchronizer, storageClient);
  }

  ngOnInit() {
    const initialService = this.requestedService();
    this.switchService(initialService);
  }

  switchService(service: FileTransferService | null) {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        service: service?.name || null,
      },
      queryParamsHandling: 'merge',
    });

    this.dataSource.stopStreaming();
    this.service.set(service);
    if (service) {
      this.dataSource.startStreaming(service);
    }

    const displayedColumns = [...this.defaultColumns];
    if (service?.capabilities?.hasTransferType) {
      displayedColumns.splice(displayedColumns.length - 1, 0, 'transferType');
    }
    this.displayedColumns$.next(displayedColumns);

    this.initializeOptions();
    this.loadData();

    this.filterForm.get('direction')!.valueChanges.forEach(direction => {
      this.direction = (direction !== 'ANY') ? direction : null;
      this.loadData();
    });

    this.filterForm.get('state')!.valueChanges.forEach(state => {
      this.state = state;
      this.loadData();
    });

    this.filterForm.get('localEntityId')!.valueChanges.forEach(localEntityId => {
      this.localEntityId = (localEntityId !== 'ANY') ? localEntityId : null;
      this.loadData();
    });

    this.filterForm.get('remoteEntityId')!.valueChanges.forEach(remoteEntityId => {
      this.remoteEntityId = (remoteEntityId !== 'ANY') ? remoteEntityId : null;
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
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('direction')) {
      this.direction = queryParams.get('direction')!;
      this.filterForm.get('direction')!.setValue(this.direction);
    }
    if (queryParams.has('state')) {
      this.state = queryParams.getAll('state')!;
      this.filterForm.get('state')!.setValue(this.state);
    }
    if (queryParams.has('localEntityId')) {
      this.localEntityId = queryParams.get('localEntityId')!;
      this.filterForm.get('localEntityId')!.setValue(this.localEntityId);
    }
    if (queryParams.has('remoteEntityId')) {
      this.remoteEntityId = queryParams.get('remoteEntityId')!;
      this.filterForm.get('remoteEntityId')!.setValue(this.remoteEntityId);
    }
    if (queryParams.has('interval')) {
      this.appliedInterval = queryParams.get('interval')!;
      this.filterForm.get('interval')!.setValue(this.appliedInterval);
      if (this.appliedInterval === 'CUSTOM') {
        const customStart = queryParams.get('customStart')!;
        this.filterForm.get('customStart')!.setValue(customStart);
        this.validStart = utils.toDate(customStart);
        const customStop = queryParams.get('customStop')!;
        this.filterForm.get('customStop')!.setValue(customStop);
        this.validStop = utils.toDate(customStop);
      } else if (this.appliedInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
      } else {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, this.appliedInterval);
      }
    } else {
      this.appliedInterval = defaultInterval;
      this.validStop = null;
      this.validStart = null;
    }
  }

  mayControlFileTransfers() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlFileTransfers');
  }

  showCreateTransferDialog(service: FileTransferService) {
    this.dialog.open(TransferFileDialogComponent, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      },
      panelClass: 'dialog-full-size',
      data: { service, }
    });
  }

  jumpToNow() {
    this.filterForm.get('interval')!.setValue('NO_LIMIT');
  }

  applyCustomDates() {
    this.validStart = utils.toDate(this.filterForm.value['customStart']);
    this.validStop = utils.toDate(this.filterForm.value['customStop']);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  loadData() {
    this.updateURL();
    const options: GetFileTransfersOptions = {
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.direction) {
      options.direction = this.direction as any;
    }
    if (this.localEntityId) {
      options.localEntityId = this.localEntityId as any;
    }
    if (this.remoteEntityId) {
      options.remoteEntityId = this.remoteEntityId as any;
    }
    if (this.state.length) {
      options.state = this.state;
    }

    const service = this.service();
    if (service) {
      this.dataSource.loadTransfers(service, options)
        .catch(err => this.messageService.showError(err));
    }
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        direction: this.direction || null,
        state: this.state.length ? this.state : null,
        localEntityId: this.localEntityId || null,
        remoteEntityId: this.remoteEntityId || null,
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStart'] : null,
        customStop: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStop'] : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  transferPercent(transfer: Transfer) {
    return transfer.totalSize != 0 ? (transfer.sizeTransferred / transfer.totalSize) : 0;
  }

  pauseTransfer(transfer: Transfer) {
    const id = transfer.id;
    const serviceName = this.service()!.name;
    this.yamcs.yamcsClient.pauseFileTransfer(this.yamcs.instance!, serviceName, id).catch(err => {
      this.messageService.showError(err);
    });
  }

  resumeTransfer(transfer: Transfer) {
    const id = transfer.id;
    const serviceName = this.service()!.name;
    this.yamcs.yamcsClient.resumeFileTransfer(this.yamcs.instance!, serviceName, id).catch(err => {
      this.messageService.showError(err);
    });
  }

  cancelTransfer(transfer: Transfer) {
    const id = transfer.id;
    const serviceName = this.service()!.name;
    this.yamcs.yamcsClient.cancelFileTransfer(this.yamcs.instance!, serviceName, id).catch(err => {
      this.messageService.showError(err);
    });
  }
}
