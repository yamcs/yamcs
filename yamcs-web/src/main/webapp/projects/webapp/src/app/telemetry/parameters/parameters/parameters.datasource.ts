import { DataSource } from '@angular/cdk/table';
import { ChangeDetectorRef } from '@angular/core';
import { GetParametersOptions, NamedObjectId, Parameter, ParameterSubscription, ParameterValue, SpaceSystem, Synchronizer, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

export class ListItem {
  name: string;
  system?: SpaceSystem;
  parameter?: Parameter;
  pval?: ParameterValue;
}

export class ParametersDataSource extends DataSource<ListItem> {

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

  async loadParameters(options: GetParametersOptions) {
    this.loading$.next(true);

    if (this.dataSubscription) {
      this.dataSubscription.cancel();
      this.dataSubscription = undefined;
    }

    return this.yamcs.yamcsClient.getParameters(this.yamcs.instance!, options).then(page => {
      this.totalSize$.next(page.totalSize);
      const items: ListItem[] = [];
      for (const system of (page.systems || [])) {
        items.push({ name: system.qualifiedName, system });
      }
      for (const parameter of (page.parameters || [])) {
        items.push({
          name: utils.getMemberPath(parameter)!,
          parameter: parameter,
        });
      }
      this.items$.next(items);
      this.startSubscription(page.parameters || []);
    }).finally(() => this.loading$.next(false));
  }

  private refreshTable() {
    const items = this.items$.value;
    for (const item of items) {
      if (!item.system) {
        item.pval = this.latestValues.get(item.name);
      }
    }
    this.items$.next([...items]);
    this.changeDetection.detectChanges();
  }

  private startSubscription(parameters: Parameter[]) {
    const ids = parameters.map(p => {
      const fullPath = utils.getMemberPath(p)!;
      return { name: fullPath };
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

        // Update page, don't wait on first sync
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

  getAliasNamespaces() {
    const namespaces: string[] = [];
    for (const item of this.items$.value) {
      if (item.parameter?.alias) {
        for (const alias of item.parameter.alias) {
          if (alias.namespace && namespaces.indexOf(alias.namespace) === -1) {
            namespaces.push(alias.namespace);
          }
        }
      }
    }
    return namespaces.sort();
  }

  disconnect() {
    this.syncSubscription?.unsubscribe();
    this.dataSubscription?.cancel();

    this.items$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
