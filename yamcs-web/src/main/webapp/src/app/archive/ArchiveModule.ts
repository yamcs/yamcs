import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { ArchiveRoutingModule, routingComponents } from './ArchiveRoutingModule';
import { DownloadDumpDialog } from './DownloadDumpDialog';
import { JumpToDialog } from './JumpToDialog';
import { TimelineTooltip } from './TimelineTooltip';

@NgModule({
  imports: [
    SharedModule,
    ArchiveRoutingModule,
  ],
  declarations: [
    routingComponents,
    DownloadDumpDialog,
    JumpToDialog,
    TimelineTooltip,
  ],
})
export class ArchiveModule {
}
