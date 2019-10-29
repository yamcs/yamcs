import { DataSource } from '@angular/cdk/table';
import { GetParametersOptions, NamedObjectId, Parameter, ParameterValue } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { ParameterWithValue } from './ParameterWithValue';

export class ParametersDataSource extends DataSource<ParameterWithValue> {

  parameters$ = new BehaviorSubject<ParameterWithValue[]>([]);
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
    return this.parameters$;
  }

  async loadParameters(options: GetParametersOptions) {
    this.loading$.next(true);

    // Unsubscribe parameters from a previous query
    const ids = this.parameters$.value.map(p => ({ name: p.qualifiedName }));
    if (ids.length) {
      await this.yamcs.getInstanceClient()!.unsubscribeParameterValueUpdates({
        subscriptionId: this.dataSubscriptionId,
        id: ids,
      });
    }

    this.yamcs.getInstanceClient()!.getParameters(options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      this.parameters$.next(page.parameters || []);
      this.createOrModifySubscription(page.parameters || []);
    });
  }

  private refreshTable() {
    const parameters = this.parameters$.value;
    for (const parameter of parameters) {
      parameter.pval = this.latestValues.get(parameter.qualifiedName);
    }
    this.parameters$.next([...parameters]);
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

  private processDelivery(delivery: ParameterValue[], idMapping: { [key: number]: NamedObjectId }) {
    const byName: { [key: string]: ParameterValue } = {};
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

    const ids = this.parameters$.value.map(p => ({ name: p.qualifiedName }));
    const instanceClient = this.yamcs.getInstanceClient();
    if (ids.length && instanceClient) {
      instanceClient.unsubscribeParameterValueUpdates({
        subscriptionId: this.dataSubscriptionId,
        id: ids,
      });
    }

    this.parameters$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
