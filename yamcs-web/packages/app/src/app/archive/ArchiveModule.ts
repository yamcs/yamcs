import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { ArchiveRoutingModule, routingComponents } from './ArchiveRoutingModule';
import { DownloadDumpDialog } from './DownloadDumpDialog';
import { JumpToDialog } from './JumpToDialog';
import { TimelineTooltip } from './TimelineTooltip';

const dialogComponents = [
  DownloadDumpDialog,
  JumpToDialog,
];

@NgModule({
  imports: [
    SharedModule,
    ArchiveRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    TimelineTooltip,
  ],
  entryComponents: [
    dialogComponents,
    TimelineTooltip,
  ]
})
export class ArchiveModule {
}
