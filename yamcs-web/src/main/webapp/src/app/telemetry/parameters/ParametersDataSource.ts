import { DataSource } from '@angular/cdk/table';
import { BehaviorSubject, Subscription } from 'rxjs';
import { GetParametersOptions, NamedObjectId, Parameter, ParameterSubscription, ParameterValue } from '../../client';
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

  private dataSubscription?: ParameterSubscription;
  private idMapping: { [key: number]: NamedObjectId; } = {};
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

    if (this.dataSubscription) {
      this.dataSubscription.cancel();
      this.dataSubscription = undefined;
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
      this.startSubscription(page.parameters || []);
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

  private startSubscription(parameters: Parameter[]) {
    const ids = parameters.map(p => ({ name: p.qualifiedName }));
    if (ids.length) {
      this.dataSubscription = this.yamcs.yamcsClient.createParameterSubscription({
        instance: this.yamcs.getInstance().name,
        processor: this.yamcs.getProcessor().name,
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
        action: 'REPLACE',
      }, data => {
        if (data.mapping) {
          this.idMapping = data.mapping;
          this.latestValues.clear();
        }
        this.processDelivery(data.values || []);
      });
    }
  }

  private processDelivery(delivery: ParameterValue[]) {
    const byName: { [key: string]: ParameterValue; } = {};
    for (const pval of delivery) {
      const id = this.idMapping[pval.numericId];
      byName[id.name] = pval;
    }

    for (const pval of delivery) {
      const id = this.idMapping[pval.numericId];
      this.latestValues.set(id.name, pval);
    }
  }

  disconnect() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    if (this.dataSubscription) {
      this.dataSubscription.cancel();
    }

    this.items$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
