import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Instance, ParameterValue } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { ParameterTable } from './ParameterTableModel';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-parameter-table-viewer',
  templateUrl: './ParameterTableViewer.html',
  styleUrls: ['./ParameterTableViewer.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterTableViewer implements Viewer, OnDestroy {

  dataSource = new MatTableDataSource<Record>([]);
  instance: Instance;

  displayedColumns = [
    'severity',
    'name',
    'generationTimeUTC',
    'rawValue',
    'engValue',
    'rangeCondition',
    'acquisitionStatus',
    'actions',
  ];

  private latestValues = new Map<string, ParameterValue>();
  private dirty = false;

  private dataSynchronizer: number;
  private dataSubscription: Subscription;

  constructor(private yamcs: YamcsService, private changeDetector: ChangeDetectorRef) {
    this.dataSynchronizer = window.setInterval(() => {
      if (this.dirty) {
        const data = this.dataSource.data;
        for (const rec of data) {
          rec.pval = this.latestValues.get(rec.name);
        }
        this.dataSource.data = data;
        this.dirty = false;
        this.changeDetector.detectChanges();
      }
    }, 1000 /* update rate */);
  }

  public loadPath(path: string) {
    this.instance = this.yamcs.getInstance();
    this.yamcs.yamcsClient.getStaticText(`${this.instance.name}/displays${path}`).then(text => {
      const parameterTable = JSON.parse(text) as ParameterTable;
      console.log(parameterTable);

      const ids = parameterTable.parameters.map(name => ({ name }));
      this.dataSource.data = ids;
      this.changeDetector.detectChanges();

      this.connectDisplay(parameterTable);
    });
  }

  private connectDisplay(parameterTable: ParameterTable) {
    const ids = parameterTable.parameters.map(name => ({ name }));
    if (ids.length) {
      this.yamcs.getInstanceClient()!.getParameterValueUpdates({
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
      }).then(res => {
        this.dataSubscription = res.parameterValues$.subscribe(pvals => {
          for (const pval of pvals) {
            this.latestValues.set(pval.id.name, pval);
          }
          this.dirty = true;
        });
      });
    }
  }

  public isFullscreenSupported() {
    return false;
  }

  moveUp(index: number) {
    const data = this.dataSource.data;
    const x = data[index];
    if (index === 0) {
      data[index] = data[data.length - 1];
      data[data.length - 1] = x;
    } else {
      data[index] = data[index - 1];
      data[index - 1] = x;
    }
    this.dataSource.data = data;
  }

  moveDown(index: number) {
    const data = this.dataSource.data;
    const x = data[index];
    if (index === data.length - 1) {
      data[index] = data[0];
      data[0] = x;
    } else {
      data[index] = data[index + 1];
      data[index + 1] = x;
    }
    this.dataSource.data = data;
  }

  ngOnDestroy() {
    if (this.dataSubscription) {
      this.dataSubscription.unsubscribe();
    }
    if (this.dataSynchronizer) {
      window.clearInterval(this.dataSynchronizer);
    }
  }
}

interface Record {
  name: string;
  pval?: ParameterValue;
}
