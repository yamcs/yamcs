import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject, Subscription } from 'rxjs';
import { GetParametersOptions, NamedObjectId, Parameter, ParameterValue } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';

export class ListItem {
  spaceSystem: boolean;
  name: string;
  parameter?: Parameter;
  pval?: ParameterValue;
}

export class ParametersDataSource extends DataSource<ListItem> {

  items$ = new BehaviorSubject<ListItem[]>([]);
  totalSize$ = new BehaviorSubject<number>(0);
  loading$ = new BehaviorSubject<boolean>(false);

  private dataSubscription: Subscription;
  private dataSubscriptionId: number;
  private latestValues = new Map<string, ParameterValue>();

  private syncSubscription: Subscription;

  constructor(private yamcs: YamcsService, private synchronizer: Synchronizer) {
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

    // Unsubscribe parameters from a previous query
    const ids = this.items$.value.filter(item => !item.spaceSystem).map(item => ({ name: item.name }));
    if (ids.length) {
      await this.yamcs.getInstanceClient()!.unsubscribeParameterValueUpdates({
        subscriptionId: this.dataSubscriptionId,
        id: ids,
      });
    }

    this.yamcs.yamcsClient.getParameters(this.yamcs.getInstance().name, options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      const items: ListItem[] = [];
      for (const spaceSystem of (page.spaceSystems || [])) {
        items.push({ spaceSystem: true, name: spaceSystem });
      }
      for (const parameter of (page.parameters || [])) {
        items.push({
          spaceSystem: false,
          name: parameter.qualifiedName,
          parameter: parameter,
        });
      }
      this.items$.next(items);
      this.createOrModifySubscription(page.parameters || []);
    });
  }

  private refreshTable() {
    const items = this.items$.value;
    for (const item of items) {
      if (!item.spaceSystem) {
        item.pval = this.latestValues.get(item.name);
      }
    }
    this.items$.next([...items]);
  }

  private createOrModifySubscription(parameters: Parameter[]) {
    const ids = parameters.map(p => ({ name: p.qualifiedName }));
    if (ids.length) {
      this.yamcs.getInstanceClient()!.getParameterValueUpdates({
        subscriptionId: this.dataSubscriptionId || -1,
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
        useNumericIds: true,
      }).then(res => {
        this.dataSubscriptionId = res.subscriptionId;
        if (this.dataSubscription) {
          this.dataSubscription.unsubscribe();
        }
        this.latestValues.clear();
        this.dataSubscription = res.parameterValues$.subscribe(pvals => {
          this.processDelivery(pvals, res.mapping);
        });
      });
    }
  }

  private processDelivery(delivery: ParameterValue[], idMapping: { [key: number]: NamedObjectId; }) {
    const byName: { [key: string]: ParameterValue; } = {};
    for (const pval of delivery) {
      const id = idMapping[pval.numericId];
      byName[id.name] = pval;
    }

    for (const pval of delivery) {
      const id = idMapping[pval.numericId];
      this.latestValues.set(id.name, pval);
    }
  }

  disconnect() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    if (this.dataSubscription) {
      this.dataSubscription.unsubscribe();
    }

    const ids = this.items$.value.filter(item => !item.spaceSystem).map(item => ({ name: item.name }));
    const instanceClient = this.yamcs.getInstanceClient();
    if (ids.length && instanceClient) {
      instanceClient.unsubscribeParameterValueUpdates({
        subscriptionId: this.dataSubscriptionId,
        id: ids,
      });
    }

    this.items$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
