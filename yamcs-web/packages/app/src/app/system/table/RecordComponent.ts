import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Record, Table } from '@yamcs/client';

@Component({
  selector: 'app-record',
  templateUrl: './RecordComponent.html',
  styleUrls: ['./RecordComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecordComponent {

  @Input()
  table: Table;

  @Input()
  record: Record;
}
