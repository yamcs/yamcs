import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AlarmDetail } from './alarms/AlarmDetail';
import { DownloadDumpDialog } from './archive/DownloadDumpDialog';
import { JumpToDialog } from './archive/JumpToDialog';
import { TimelineTooltip } from './archive/TimelineTooltip';
import { CreateEventDialog } from './events/CreateEventDialog';
import { EventSeverity } from './events/EventSeverity';
import { PageContentHost } from './ext/PageContentHost';
import { DisplayNavigator } from './layouts/DisplayNavigator';
import { LayoutComponent } from './layouts/LayoutComponent';
import { SaveLayoutDialog } from './layouts/SaveLayoutDialog';
import { MonitorRoutingModule, routingComponents } from './MonitorRoutingModule';
import { DisplayTypePipe } from './pipes/DisplayTypePipe';
import { MonitorPageTemplate } from './template/MonitorPageTemplate';
import { MonitorToolbar } from './template/MonitorToolbar';
import { StartReplayDialog } from './template/StartReplayDialog';

const dialogComponents = [
  CreateEventDialog,
  DownloadDumpDialog,
  JumpToDialog,
  SaveLayoutDialog,
  StartReplayDialog,
];

const pipes = [
  DisplayTypePipe,
];

@NgModule({
  imports: [
    SharedModule,
    MonitorRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    pipes,
    AlarmDetail,
    DisplayNavigator,
    EventSeverity,
    LayoutComponent,
    MonitorPageTemplate,
    PageContentHost,
    TimelineTooltip,
  ],
  exports: [
    MonitorPageTemplate,
    MonitorToolbar,
  ],
  entryComponents: [
    dialogComponents,
    TimelineTooltip,
  ]
})
export class MonitorModule {
}
