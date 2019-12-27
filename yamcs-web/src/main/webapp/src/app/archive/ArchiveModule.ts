import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { ArchiveRoutingModule, routingComponents } from './ArchiveRoutingModule';
import { DownloadDumpDialog } from './DownloadDumpDialog';
import { RequestPlaybackDialog } from './gaps/RequestPlaybackDialog';
import { JumpToDialog } from './JumpToDialog';
import { ColumnValuePipe } from './pipes/ColumnValuePipe';
import { StreamDataComponent } from './stream/StreamDataComponent';
import { RecordComponent } from './table/RecordComponent';
import { ShowEnumDialog } from './table/ShowEnumDialog';
import { TimelineTooltip } from './TimelineTooltip';

const dialogComponents = [
  DownloadDumpDialog,
  JumpToDialog,
  RequestPlaybackDialog,
  ShowEnumDialog,
];

const pipes = [
  ColumnValuePipe,
];

@NgModule({
  imports: [
    SharedModule,
    ArchiveRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    pipes,
    RecordComponent,
    StreamDataComponent,
    TimelineTooltip,
  ],
  entryComponents: [
    dialogComponents,
    TimelineTooltip,
  ]
})
export class ArchiveModule {
}
