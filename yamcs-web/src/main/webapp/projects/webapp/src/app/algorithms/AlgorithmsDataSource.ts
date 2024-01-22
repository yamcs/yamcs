import { DataSource } from '@angular/cdk/table';
import { Algorithm, GetAlgorithmsOptions, SpaceSystem, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

export class ListItem {
  name: string;
  system?: SpaceSystem;
  algorithm?: Algorithm;
}

export class AlgorithmsDataSource extends DataSource<ListItem> {

  items$ = new BehaviorSubject<ListItem[]>([]);
  totalSize$ = new BehaviorSubject<number>(0);
  loading$ = new BehaviorSubject<boolean>(false);

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect() {
    return this.items$;
  }

  loadAlgorithms(options: GetAlgorithmsOptions) {
    this.loading$.next(true);
    return this.yamcs.yamcsClient.getAlgorithms(this.yamcs.instance!, options).then(page => {
      this.loading$.next(false);
      this.totalSize$.next(page.totalSize);
      const items: ListItem[] = [];
      for (const system of (page.systems || [])) {
        items.push({ name: system.qualifiedName, system });
      }
      for (const algorithm of (page.algorithms || [])) {
        items.push({ name: algorithm.qualifiedName, algorithm });
      }
      this.items$.next(items);
    });
  }

  disconnect() {
    this.items$.complete();
    this.totalSize$.complete();
    this.loading$.complete();
  }
}
