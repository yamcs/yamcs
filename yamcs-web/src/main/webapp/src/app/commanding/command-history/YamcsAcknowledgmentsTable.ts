import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord } from './CommandHistoryRecord';

@Component({
    selector: 'app-yamcs-acknowledgments-table',
    templateUrl: './YamcsAcknowledgmentsTable.html',
    styleUrls: ['./YamcsAcknowledgmentsTable.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YamcsAcknowledgmentsTable {

    @Input()
    command: CommandHistoryRecord;

    @Input()
    inline = false;
}
