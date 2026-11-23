import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { MatMenuItem } from '@angular/material/menu';
import { MatTooltip } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { DateTimePipe, utils, ValuePipe, YaMore } from '@yamcs/webapp-sdk';
import { ReportSnapshot } from './report-types';
import { REPORT_FIELD_HELP, summarizeReport } from './report-view-model';

@Component({
  selector: 'app-report-snapshot',
  templateUrl: './report-snapshot.component.html',
  styleUrl: './report-snapshot.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DateTimePipe,
    MatMenuItem,
    MatTooltip,
    NgTemplateOutlet,
    RouterLink,
    ValuePipe,
    YaMore,
  ],
})
export class ReportSnapshotComponent {
  report = input.required<ReportSnapshot>();
  title = input.required<string>();
  context = input<string>();

  readonly fieldHelp = REPORT_FIELD_HELP;
  readonly summary = computed(() => summarizeReport(this.report()));
  readonly fullBinary = { maxBytes: Infinity };

  binaryFilename(field: string, page: number, row: number) {
    const name = field === 'tc_packet_bytes' ? 'embedded-tc' : field;
    return name + '-' + this.report().id + '-p' + page + '-r' + row;
  }

  downloadBinary(encoded: string, format: 'hex' | 'bin', filename: string) {
    const file =
      format === 'hex'
        ? new Blob([utils.convertBase64ToHex(encoded)], { type: 'text/plain' })
        : new Blob(
            [Uint8Array.from(atob(encoded), (byte) => byte.charCodeAt(0))],
            {
              type: 'application/octet-stream',
            },
          );
    const href = URL.createObjectURL(file);
    const link = document.createElement('a');
    link.href = href;
    link.download = filename + '.' + format;
    link.click();
    setTimeout(() => URL.revokeObjectURL(href), 0);
  }

  canFormatTimestamp(value?: string) {
    return value && Number.isFinite(Date.parse(value));
  }
}
