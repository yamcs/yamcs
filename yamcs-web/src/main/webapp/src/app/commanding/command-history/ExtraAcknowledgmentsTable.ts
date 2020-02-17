import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord } from './CommandHistoryRecord';

@Component({
    selector: 'app-extra-acknowledgments-table',
    templateUrl: './ExtraAcknowledgmentsTable.html',
    styleUrls: ['./ExtraAcknowledgmentsTable.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExtraAcknowledgmentsTable {

    @Input()
    command: CommandHistoryRecord;

    @Input()
    inline = false;

    @Input()
    showIcons = true;
}
