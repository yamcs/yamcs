import { Component, input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { StackedTextEntry } from '../stack-file/StackedEntry';

@Component({
  selector: 'app-stacked-text-entry',
  templateUrl: './stacked-text-entry.component.html',
  styleUrl: './stacked-text-entry.component.css',
  imports: [MarkdownComponent, WebappSdkModule],
})
export class StackedTextEntryComponent {
  entry = input.required<StackedTextEntry>();
}
