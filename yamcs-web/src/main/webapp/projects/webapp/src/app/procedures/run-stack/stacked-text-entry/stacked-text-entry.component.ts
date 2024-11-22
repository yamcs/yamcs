import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { EntryLabel } from '../entry-label/entry-label.component';
import { StackedTextEntry } from '../stack-file/StackedEntry';

@Component({
  standalone: true,
  selector: 'app-stacked-text-entry',
  templateUrl: './stacked-text-entry.component.html',
  styleUrl: './stacked-text-entry.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    EntryLabel,
    MarkdownComponent,
    WebappSdkModule,
  ],
})
export class StackedTextEntryComponent {
  entry = input.required<StackedTextEntry>();
}
