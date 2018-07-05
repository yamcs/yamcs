import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AlarmDetail } from './alarms/AlarmDetail';
import { DownloadDumpDialog } from './archive/DownloadDumpDialog';
import { JumpToDialog } from './archive/JumpToDialog';
import { TimelineTooltip } from './archive/TimelineTooltip';
import { CreateDisplayDialog } from './displays/CreateDisplayDialog';
import { ImageViewer } from './displays/ImageViewer';
import { OpiDisplayViewer } from './displays/OpiDisplayViewer';
import { ParameterTableViewer } from './displays/ParameterTableViewer';
import { ParameterTableViewerControls } from './displays/ParameterTableViewerControls';
import { ScriptViewer } from './displays/ScriptViewer';
import { TextViewer } from './displays/TextViewer';
import { UssDisplayViewer } from './displays/UssDisplayViewer';
import { ViewerControlsHost } from './displays/ViewerControlsHost';
import { ViewerHost } from './displays/ViewerHost';
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
  CreateDisplayDialog,
  CreateEventDialog,
  DownloadDumpDialog,
  JumpToDialog,
  SaveLayoutDialog,
  StartReplayDialog,
];

const pipes = [
  DisplayTypePipe,
];

const directives = [
  PageContentHost,
  ViewerControlsHost,
  ViewerHost,
];

const viewers = [
  ImageViewer,
  OpiDisplayViewer,
  ParameterTableViewer,
  ParameterTableViewerControls,
  ScriptViewer,
  TextViewer,
  UssDisplayViewer,
];

@NgModule({
  imports: [
    SharedModule,
    MonitorRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    directives,
    pipes,
    viewers,
    AlarmDetail,
    DisplayNavigator,
    EventSeverity,
    LayoutComponent,
    MonitorPageTemplate,
    TimelineTooltip,
  ],
  exports: [
    MonitorPageTemplate,
    MonitorToolbar,
  ],
  entryComponents: [
    dialogComponents,
    viewers,
    TimelineTooltip,
  ]
})
export class MonitorModule {
}
