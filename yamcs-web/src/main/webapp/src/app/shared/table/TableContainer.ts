import { DataSource } from '@angular/cdk/table';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-table-container',
  templateUrl: './TableContainer.html',
  styleUrls: ['./TableContainer.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableContainer {

  @Input()
  dataSource: DataSource<any>;
}
