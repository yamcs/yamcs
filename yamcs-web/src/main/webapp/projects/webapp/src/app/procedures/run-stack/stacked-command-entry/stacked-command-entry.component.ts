import { KeyValue } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { AcknowledgmentNamePipe } from '../acknowledgment-name.pipe';
import { EntryLabel } from '../entry-label/entry-label.component';
import { StackedCommandEntry } from '../stack-file/StackedEntry';

@Component({
  standalone: true,
  selector: 'app-stacked-command-entry',
  templateUrl: './stacked-command-entry.component.html',
  styleUrl: './stacked-command-entry.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AcknowledgmentNamePipe,
    EntryLabel,
    WebappSdkModule,
  ],
})
export class StackedCommandEntryComponent {
  entry = input.required<StackedCommandEntry>();

  // KeyValuePipe comparator that preserves original order.
  // (default KeyValuePipe is to sort A-Z, but that's undesired for args).
  insertionOrder = (a: KeyValue<string, any>, b: KeyValue<string, any>): number => {
    return 0;
  };
}
