import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatTableDataSource } from '@angular/material';
import { Instance, ParameterValue } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { ParameterTableBuffer } from './ParameterTableBuffer';
import { ParameterTable } from './ParameterTableModel';

@Component({
  selector: 'app-scrolling-parameter-table',
  templateUrl: './ScrollingParameterTable.html',
  styleUrls: ['./ScrollingParameterTable.css'],
  // changeDetection: ChangeDetectionStrategy.OnPush, // FIXME
})
export class ScrollingParameterTable implements OnInit, OnChanges, OnDestroy {

  @Input()
  instance: Instance;

  @Input()
  model: ParameterTable = {
    scroll: true,
    columns: [],
    parameters: [],
  };

  @Input()
  buffer: ParameterTableBuffer;

  @Input()
  showActions: boolean;

  @Input()
  paused: boolean;

  @Output()
  removeColumn = new EventEmitter<string>();

  @Output()
  moveLeft = new EventEmitter<number>();

  @Output()
  moveRight = new EventEmitter<number>();

  @Output()
  bufferSize = new EventEmitter<number>();

  dataSource = new MatTableDataSource<ScrollRecord>([]);

  bufferSizeControl = new FormControl('10');
  private bufferSizeControlSubscription: Subscription;

  private dataSynchronizer: number;

  displayedColumns = [
    'generationTimeUTC',
  ];

  constructor(private changeDetector: ChangeDetectorRef) {
    this.dataSynchronizer = window.setInterval(() => {
      if (!this.paused) {
        this.refreshTable();
      }
    }, 500 /* update rate */);

    this.bufferSizeControl.valueChanges.subscribe(() => {
      const val = this.bufferSizeControl.value;
      if (val !== String(this.model.bufferSize)) {
        this.bufferSize.emit(parseInt(val, 10));
      }
    });
  }

  ngOnInit() {
    if (this.model.bufferSize) {
      this.buffer.setSize(this.model.bufferSize);
      this.bufferSizeControl.setValue(String(this.model.bufferSize));
    }
    this.refreshTable();
  }

  private refreshTable() {
    const recs: ScrollRecord[] = [];
    const snapshot = this.buffer.snapshot();
    for (const sample of snapshot) {
      const anyValue = this.findAnyMatchingParameterValue(sample);
      if (anyValue) {
        recs.push({
          generationTimeUTC: anyValue.generationTimeUTC,
          pvals: sample,
        });
      }
    }

    this.dataSource.data = recs;
    this.changeDetector.detectChanges();
  }

  private findAnyMatchingParameterValue(sample: {[key: string]: ParameterValue}) {
    for (const name in sample) {
      if (sample.hasOwnProperty(name)) {
        if (this.model.parameters.indexOf(name) !== -1) {
          return sample[name];
        }
      }
    }
  }

  ngOnChanges() {
    this.displayedColumns = [
      'generationTimeUTC',
      ...this.model.parameters,
    ];
  }

  ngOnDestroy() {
    if (this.dataSynchronizer) {
      window.clearInterval(this.dataSynchronizer);
    }
    if (this.bufferSizeControlSubscription) {
      this.bufferSizeControlSubscription.unsubscribe();
    }
  }
}

export interface ScrollRecord {
  generationTimeUTC: string;
  pvals: { [key: string]: ParameterValue };
}
