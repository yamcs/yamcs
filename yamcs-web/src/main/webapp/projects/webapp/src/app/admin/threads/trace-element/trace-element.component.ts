import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TraceElementInfo } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-trace-element',
  templateUrl: './trace-element.component.html',
  styleUrl: './trace-element.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TraceElementComponent {
  element = input.required<TraceElementInfo>();

  get classPackage() {
    const { className } = this.element();

    const idx = className.lastIndexOf('.');
    if (idx === -1) {
      return undefined;
    } else {
      return className.substring(0, idx);
    }
  }

  get classShortName() {
    const { className } = this.element();

    const idx = className.lastIndexOf('.');
    if (idx === -1) {
      return className;
    } else {
      return className.substring(idx + 1);
    }
  }
}
