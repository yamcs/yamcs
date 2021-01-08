import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { CfdpService, Transfer, TransferSubscription } from '../client';
import { Synchronizer } from '../core/services/Synchronizer';
import { YamcsService } from '../core/services/YamcsService';
import { UploadFileDialog } from './UploadFileDialog';

@Component({
  templateUrl: './FileTransferPage.html',
  styleUrls: ['./FileTransferPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileTransferPage implements OnDestroy {

  services$ = new BehaviorSubject<CfdpService[]>([]);
  service$ = new BehaviorSubject<CfdpService | null>(null);

  private ongoingTransfersById = new Map<number, Transfer>();
  private failedTransfersById = new Map<number, Transfer>();
  private successfulTransfersById = new Map<number, Transfer>();

  ongoingCount$ = new BehaviorSubject<number>(0);
  failedCount$ = new BehaviorSubject<number>(0);
  successfulCount$ = new BehaviorSubject<number>(0);

  private transferSubscription: TransferSubscription;

  private dirty = false;
  private syncSubscription: Subscription;

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    private dialog: MatDialog,
    private route: ActivatedRoute,
    private router: Router,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('CFDP File Transfer');

    const queryParams = route.snapshot.queryParamMap;
    const requestedService = queryParams.get('service');

    yamcs.yamcsClient.getCfdpServices(yamcs.instance!).then(page => {
      this.services$.next(page.services);

      // Respect the requested service (from query param)
      // But default to the first if unspecified.
      let service = null;
      for (const candidate of page.services) {
        if (requestedService === candidate.name) {
          service = candidate;
          break;
        }
      }
      if (!service && !requestedService) {
        service = page.services.length ? page.services[0] : null;
      }
      if (service) {
        this.switchService(service);
      }
    });

    this.syncSubscription = synchronizer.sync(() => {
      if (this.dirty) {
        this.ongoingCount$.next(this.ongoingTransfersById.size);
        this.failedCount$.next(this.failedTransfersById.size);
        this.successfulCount$.next(this.successfulTransfersById.size);
      }
    });
  }

  switchService(service: CfdpService | null) {
    // Update URL
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        service: service?.name || null,
      },
      queryParamsHandling: 'merge',
    });

    // Clear state
    this.ongoingCount$.next(0);
    this.failedCount$.next(0);
    this.successfulCount$.next(0);
    this.ongoingTransfersById.clear();
    this.failedTransfersById.clear();
    this.successfulTransfersById.clear();
    if (this.transferSubscription) {
      this.transferSubscription.cancel();
    }

    this.service$.next(service);
    if (service) {
      this.transferSubscription = this.yamcs.yamcsClient.createTransferSubscription({
        instance: this.yamcs.instance!,
        serviceName: service.name,
      }, transfer => {
        switch (transfer.state) {
          case 'RUNNING':
          case 'PAUSED':
            this.ongoingTransfersById.set(transfer.id, transfer);
            break;
          case 'FAILED':
            this.ongoingTransfersById.delete(transfer.id);
            this.failedTransfersById.set(transfer.id, transfer);
            break;
          case 'COMPLETED':
            this.ongoingTransfersById.delete(transfer.id);
            this.successfulTransfersById.set(transfer.id, transfer);
            break;
        }

        // Dirty mechanism, to prevent overloading change detection
        // on fast updates
        this.dirty = true;
      });
    }
  }

  uploadFile(service: CfdpService) {
    const dialogRef = this.dialog.open(UploadFileDialog, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      },
      data: { service, }
    });
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    if (this.transferSubscription) {
      this.transferSubscription.cancel();
    }
  }
}
