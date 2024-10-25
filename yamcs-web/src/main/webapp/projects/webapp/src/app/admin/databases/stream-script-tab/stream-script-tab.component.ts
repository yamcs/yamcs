import { ChangeDetectionStrategy, Component, input, OnInit, SecurityContext, signal } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import * as utils from '../utils';

@Component({
  standalone: true,
  selector: 'app-stream-script-tab',
  templateUrl: './stream-script-tab.component.html',
  styleUrls: [
    './stream-script-tab.component.css',
    '../streamsql.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class StreamScriptTabComponent implements OnInit {

  database = input.required<string>();
  stream = input.required<string>();

  sqlHtml = signal<string | null>(null);

  constructor(
    private messageService: MessageService,
    private yamcs: YamcsService,
    private sanitizer: DomSanitizer,
  ) {
  }

  ngOnInit(): void {
    this.yamcs.yamcsClient.getStream(this.database(), this.stream()).then(stream => {
      const html = utils.formatSQL(stream.script);
      const safeHtml = this.sanitizer.sanitize(SecurityContext.HTML, html);
      this.sqlHtml.set(safeHtml);
    }).catch(err => this.messageService.showError(err));
  }
}
