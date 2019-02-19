import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { SelectParameterDialog } from '../mdb/parameters/SelectParameterDialog';
import { AcknowledgeAlarmDialog } from './alarms/AcknowledgeAlarmDialog';
import { AlarmDetail } from './alarms/AlarmDetail';
import { CreateDisplayDialog } from './displays/CreateDisplayDialog';
import { DisplayFilePageDirtyDialog } from './displays/DisplayFilePageDirtyDialog';
import { ExportArchiveDataDialog } from './displays/ExportArchiveDataDialog';
import { ImageViewer } from './displays/ImageViewer';
import { MultipleParameterTable } from './displays/MultipleParameterTable';
import { OpiDisplayViewer } from './displays/OpiDisplayViewer';
import { ParameterTableViewer } from './displays/ParameterTableViewer';
import { ParameterTableViewerControls } from './displays/ParameterTableViewerControls';
import { RenameDisplayDialog } from './displays/RenameDisplayDialog';
import { ScriptViewer } from './displays/ScriptViewer';
import { ScrollingParameterTable } from './displays/ScrollingParameterTable';
import { TextViewer } from './displays/TextViewer';
import { UploadFilesDialog } from './displays/UploadFilesDialog';
import { UssDisplayViewer } from './displays/UssDisplayViewer';
import { UssDisplayViewerControls } from './displays/UssDisplayViewerControls';
import { ViewerControlsHost } from './displays/ViewerControlsHost';
import { ViewerHost } from './displays/ViewerHost';
import { CreateEventDialog } from './events/CreateEventDialog';
import { EventSeverity } from './events/EventSeverity';
import { PageContentHost } from './ext/PageContentHost';
import { CreateLayoutDialog } from './layouts/CreateLayoutDialog';
import { DisplayNavigator } from './layouts/DisplayNavigator';
import { Frame } from './layouts/Frame';
import { FrameHost } from './layouts/FrameHost';
import { Layout } from './layouts/Layout';
import { RenameLayoutDialog } from './layouts/RenameLayoutDialog';
import { MonitorRoutingModule, routingComponents } from './MonitorRoutingModule';
import { DisplayTypePipe } from './pipes/DisplayTypePipe';

const dialogComponents = [
  AcknowledgeAlarmDialog,
  CreateDisplayDialog,
  CreateEventDialog,
  CreateLayoutDialog,
  DisplayFilePageDirtyDialog,
  ExportArchiveDataDialog,
  RenameDisplayDialog,
  RenameLayoutDialog,
  SelectParameterDialog,
  UploadFilesDialog,
];

const pipes = [
  DisplayTypePipe,
];

const directives = [
  FrameHost,
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
  UssDisplayViewerControls,
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
    Frame,
    EventSeverity,
    Layout,
    MultipleParameterTable,
    ScrollingParameterTable,
  ],
  entryComponents: [
    dialogComponents,
    viewers,
    Frame,
  ]
})
export class MonitorModule {
}
