import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { ParameterValue } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { ParameterTableBuffer } from './ParameterTableBuffer';
import { ParameterTable } from './ParameterTableModel';

@Component({
  selector: 'app-multiple-parameter-table',
  templateUrl: './MultipleParameterTable.html',
  styleUrls: ['./MultipleParameterTable.css'],
  // changeDetection: ChangeDetectionStrategy.OnPush, // FIXME
})
export class MultipleParameterTable implements OnInit, OnChanges, OnDestroy {

  @Input()
  instance: string;

  @Input()
  model: ParameterTable = {
    scroll: false,
    columns: [],
    parameters: [],
  };

  @Input()
  buffer: ParameterTableBuffer;

  @Input()
  showActions: boolean;

  @Input()
  paused: boolean;

  @Input()
  selection: SelectionModel<string>;

  @Output()
  moveUp = new EventEmitter<number>();

  @Output()
  moveDown = new EventEmitter<number>();

  dataSource = new MatTableDataSource<ParameterTableRecord>([]);

  private syncSubscription: Subscription;

  private defaultColumns = [
    'severity',
    'name',
    'generationTimeUTC',
    'rawValue',
    'engValue',
    'acquisitionStatus',
  ];

  displayedColumns: string[];

  constructor(private changeDetector: ChangeDetectorRef, synchronizer: Synchronizer) {
    this.syncSubscription = synchronizer.syncFast(() => {
      if (!this.paused) {
        this.refreshTable();
      }
    });
  }

  ngOnInit() {
    this.refreshTable();
  }

  private refreshTable() {
    const recs: ParameterTableRecord[] = this.model.parameters.map(name => ({ name }));
    for (const rec of recs) {
      rec.pval = this.buffer.getLatestValue(rec.name);
    }
    this.dataSource.data = recs;
    this.changeDetector.detectChanges();
  }

  ngOnChanges() {
    if (this.showActions) {
      this.displayedColumns = ['select', ...this.defaultColumns, 'actions'];
    } else {
      this.displayedColumns = this.defaultColumns;
    }
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows && numRows > 0;
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.data.forEach(row => this.selection.select(row.name));
  }

  toggleOne(name: string) {
    if (!this.selection.isSelected(name) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(name);
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}

export interface ParameterTableRecord {
  name: string;
  pval?: ParameterValue;
}
