import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { ParameterValue, Synchronizer, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { ParameterTableBuffer } from '../ParameterTableBuffer';
import { ParameterTable } from '../ParameterTableModel';

@Component({
  standalone: true,
  selector: 'app-scrolling-parameter-table',
  templateUrl: './scrolling-parameter-table.component.html',
  styleUrl: './scrolling-parameter-table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ScrollingParameterTable implements OnInit, OnChanges, OnDestroy {

  @Input()
  model: ParameterTable = {
    scroll: true,
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

  bufferSizeControl = new UntypedFormControl('10');
  private bufferSizeControlSubscription: Subscription;

  private syncSubscription: Subscription;

  displayedColumns = [
    'generationTime',
    'actions',
  ];

  constructor(readonly yamcs: YamcsService, private changeDetector: ChangeDetectorRef, synchronizer: Synchronizer) {
    this.syncSubscription = synchronizer.syncFast(() => {
      if (!this.paused) {
        this.refreshTable();
      }
    });

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
          generationTime: anyValue.generationTime,
          pvals: sample,
        });
      }
    }

    this.dataSource.data = recs;
    this.changeDetector.detectChanges();
  }

  private findAnyMatchingParameterValue(sample: { [key: string]: ParameterValue; }) {
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
      'generationTime',
      ...this.model.parameters,
      'actions',
    ];
  }

  ngOnDestroy() {
    this.syncSubscription?.unsubscribe();
    this.bufferSizeControlSubscription?.unsubscribe();
  }
}

export interface ScrollRecord {
  generationTime: string;
  pvals: { [key: string]: ParameterValue; };
}
