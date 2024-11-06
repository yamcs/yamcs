import { DataSource } from '@angular/cdk/table';
import { ChangeDetectorRef } from '@angular/core';
import { NamedObjectId, Parameter, ParameterSubscription, ParameterValue, Synchronizer, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

export class ListItem {
  name: string;
  parameter?: Parameter;
  pval?: ParameterValue;
}

export class StreamingParametersDataSource extends DataSource<ListItem> {

  // Max bytes to fetch and show
  readonly binaryPreview = 16;

  items$ = new BehaviorSubject<ListItem[]>([]);
  totalSize$ = new BehaviorSubject<number>(0);
  loading$ = new BehaviorSubject<boolean>(false);

  private dataSubscription?: ParameterSubscription;
  private idMapping: { [key: number]: NamedObjectId; } = {};
  private latestValues = new Map<string, ParameterValue>();

  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    private synchronizer: Synchronizer,
    private changeDetection: ChangeDetectorRef,
  ) {
    super();
  }

  connect() {
    this.syncSubscription = this.synchronizer.syncFast(() => {
      this.refreshTable();
    });
    return this.items$;
  }

  async loadParameters(parameters: Parameter[]) {
    this.loading$.next(true);

    if (this.dataSubscription) {
      this.dataSubscription.cancel();
      this.dataSubscription = undefined;
    }

    const items: ListItem[] = [];
    for (const parameter of parameters) {
      items.push({
        name: parameter.qualifiedName,
        parameter,
      });
    }
    this.items$.next(items);
    this.startSubscription(parameters || []);
  }

  private refreshTable() {
    const items = this.items$.value;
    for (const item of items) {
      item.pval = this.latestValues.get(item.name);
    }

    this.items$.next([...items]);

    // Needed to show table updates in combination with trackBy
    this.changeDetection.detectChanges();
  }

  private startSubscription(parameters: Parameter[]) {
    const ids = parameters.map(p => {
      return { name: p.qualifiedName };
    });
    if (ids.length) {
      this.dataSubscription = this.yamcs.yamcsClient.createParameterSubscription({
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor!,
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
        maxBytes: this.binaryPreview + 1, // 1 more, so we know when to show ellipsis
        action: 'REPLACE',
      }, data => {
        if (data.mapping) {
          this.idMapping = data.mapping;
          this.latestValues.clear();
        }
        this.processDelivery(data.values || []);
        this.loading$.next(false);

        // Quick emit, don't wait on sync tick
        if (data.mapping) {
          this.refreshTable();
        }
      });
    }
  }

  private processDelivery(delivery: ParameterValue[]) {
    for (const pval of delivery) {
      const id = this.idMapping[pval.numericId];
      if (id) { // Can be unset, in case we get an old update, following a changed subscription
        this.latestValues.set(id.name, pval);
      }
    }
  }

  disconnect() {
    this.syncSubscription?.unsubscribe();
    this.dataSubscription?.cancel();

    this.items$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
