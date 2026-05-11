import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Band } from '@fqqb/timeline';
import {
  GetTimelineItemsOptions,
  MessageService,
  Synchronizer,
  TimelineBand,
  TimelineItem,
  UpdateTimelineViewRequest,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { firstValueFrom } from 'rxjs';
import { ItemState } from './ItemState';
import {
  CreateBandDialogComponent,
  CreateBandDialogData,
} from './dialogs/create-band-dialog.component';
import { CreateViewDialogComponent } from './dialogs/create-view-dialog.component';
import { EditActivityDialogComponent } from './dialogs/edit-activity-dialog.component';
import { EditBandDialogComponent } from './dialogs/edit-band-dialog.component';
import { EditEventDialogComponent } from './dialogs/edit-event-dialog.component';
import { SetItemFailedDialogComponent } from './dialogs/set-item-failed-dialog.component';

@Injectable()
export class TimelineService {
  private destroyRef = inject(DestroyRef);
  private dialog = inject(MatDialog);
  private messageService = inject(MessageService);
  private synchronizer = inject(Synchronizer);
  private yamcs = inject(YamcsService);

  private _refreshTrigger = signal(0);
  /**
   * Signal Signal that can be subscribed to, indicating
   * that the chart should fully refresh.
   */
  refreshTrigger = this._refreshTrigger.asReadonly();

  private itemsByBand = new Map<Band, TimelineItem[]>();

  private _items = signal<ItemState[]>([]);
  readonly items = this._items.asReadonly();

  constructor() {
    const syncSubscription = this.synchronizer.sync(() => {
      const now = this.yamcs.getMissionTime().getTime();
      for (const item of this.items()) {
        item.updateState(now);
      }
    });
    this.destroyRef.onDestroy(() => syncSubscription.unsubscribe());
  }

  reportItems(source: Band, items: TimelineItem[]) {
    this.itemsByBand.set(source, items);
    this.fireItemsChange();
  }

  releaseItems(source: Band) {
    this.itemsByBand.delete(source);
    this.fireItemsChange();
  }

  updateView(viewId: string, options: UpdateTimelineViewRequest) {
    return this.yamcs.yamcsClient
      .updateTimelineView(this.yamcs.instance!, viewId, options)
      .then(() => this.triggerRefresh())
      .catch((err) => this.messageService.showError(err));
  }

  startItem(item: ItemState) {
    return this.yamcs.yamcsClient
      .startTimelineItem(this.yamcs.instance!, item.id)
      .catch((err) => this.messageService.showError(err));
  }

  cancelActivityRun(activityId: string) {
    return this.yamcs.yamcsClient
      .cancelActivity(this.yamcs.instance!, activityId)
      .catch((err) => this.messageService.showError(err));
  }

  cancelItem(item: ItemState) {
    return this.yamcs.yamcsClient
      .cancelTimelineItem(this.yamcs.instance!, item.id)
      .catch((err) => this.messageService.showError(err));
  }

  deleteItem(item: ItemState) {
    return this.yamcs.yamcsClient
      .deleteTimelineItem(this.yamcs.instance!, item.id)
      .catch((err) => this.messageService.showError(err));
  }

  setItemSuccessful(item: ItemState) {
    const activity = item.lastRun;
    if (activity) {
      return this.yamcs.yamcsClient
        .completeManualActivity(this.yamcs.instance!, activity.id, {})
        .catch((err) => this.messageService.showError(err));
    }
  }

  setItemFailed(item: ItemState) {
    const activity = item.lastRun;
    if (activity) {
      this.dialog
        .open(SetItemFailedDialogComponent, {
          width: '400px',
        })
        .afterClosed()
        .subscribe((result) => {
          if (result) {
            this.yamcs.yamcsClient
              .completeManualActivity(this.yamcs.instance!, activity.id, {
                failureReason: result.failureReason,
              })
              .catch((err) => this.messageService.showError(err));
          }
        });
    }
  }

  private fireItemsChange() {
    // Find unique items among all bands
    const itemsById = new Map<string, TimelineItem>();
    for (const items of this.itemsByBand.values()) {
      for (const item of items) {
        itemsById.set(item.id, item);
      }
    }
    const now = this.yamcs.getMissionTime().getTime();
    const items = [...itemsById.values()]
      .map((item) => new ItemState(item, now))
      .sort((a, b) => {
        return a.start() - b.start();
      });
    this._items.set(items);
  }

  fetchTimelineItems(options: GetTimelineItemsOptions) {
    return this.yamcs.yamcsClient
      .getTimelineItems(this.yamcs.instance!, options)
      .catch((err) => this.messageService.showError(err));
  }

  openCreateViewDialog() {
    this.dialog.open(CreateViewDialogComponent, {
      width: '400px',
    });
  }

  triggerRefresh() {
    this._refreshTrigger.update((val) => val + 1);
  }

  async openCreateBandDialog(data?: CreateBandDialogData) {
    const dialogRef = this.dialog.open<
      CreateBandDialogComponent,
      CreateBandDialogData,
      TimelineBand
    >(CreateBandDialogComponent, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      },
      panelClass: 'dialog-full-size',
      data,
    });
    return await firstValueFrom(dialogRef.afterClosed());
  }

  openEditBandDialog(band: TimelineBand) {
    const dialogRef = this.dialog.open(EditBandDialogComponent, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      },
      panelClass: 'dialog-full-size',
      data: { band },
    });
    dialogRef.afterClosed().subscribe((updatedBand) => {
      if (updatedBand) {
        this.triggerRefresh();
      }
    });
  }

  openEditItemDialog(item: TimelineItem) {
    if (item.type === 'EVENT') {
      this.dialog
        .open(EditEventDialogComponent, {
          width: '600px',
          panelClass: 'dialog-force-no-scrollbar',
          data: { item },
        })
        .afterClosed()
        .subscribe((result) => {
          if (result) {
            this.triggerRefresh();
          }
        });
    } else if (item.type === 'ACTIVITY') {
      this.dialog
        .open(EditActivityDialogComponent, {
          width: '600px',
          panelClass: 'dialog-force-no-scrollbar',
          data: { item },
        })
        .afterClosed()
        .subscribe((result) => {
          if (result) {
            this.triggerRefresh();
          }
        });
    }
  }
}
