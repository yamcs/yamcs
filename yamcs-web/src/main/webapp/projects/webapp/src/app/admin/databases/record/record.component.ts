import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Record, Table, WebappSdkModule } from '@yamcs/webapp-sdk';
import { HexComponent } from '../../../shared/hex/hex.component';
import { ColumnValuePipe } from '../shared/column-value.pipe';

@Component({
  standalone: true,
  selector: 'app-record',
  templateUrl: './record.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ColumnValuePipe,
    HexComponent,
    WebappSdkModule,
  ],
})
export class RecordComponent {

  @Input()
  table: Table;

  @Input()
  record: Record;
}
