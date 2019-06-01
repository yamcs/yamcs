import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
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
import { ColorPalette } from './parameters/ColorPalette';
import { CompareParameterDialog } from './parameters/CompareParameterDialog';
import { ModifyParameterDialog } from './parameters/ModifyParameterDialog';
import { ParameterDetail } from './parameters/ParameterDetail';
import { ParameterValuesTable } from './parameters/ParameterValuesTable';
import { SelectRangeDialog } from './parameters/SelectRangeDialog';
import { SetParameterDialog } from './parameters/SetParameterDialog';
import { SeverityMeter } from './parameters/SeverityMeter';
import { Thickness } from './parameters/Thickness';
import { DisplayTypePipe } from './pipes/DisplayTypePipe';

const dialogComponents = [
  AcknowledgeAlarmDialog,
  CompareParameterDialog,
  CreateDisplayDialog,
  CreateEventDialog,
  CreateLayoutDialog,
  DisplayFilePageDirtyDialog,
  ExportArchiveDataDialog,
  ModifyParameterDialog,
  RenameDisplayDialog,
  RenameLayoutDialog,
  SelectRangeDialog,
  SetParameterDialog,
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
    ColorPalette,
    DisplayNavigator,
    Frame,
    EventSeverity,
    Layout,
    MultipleParameterTable,
    ParameterDetail,
    ParameterValuesTable,
    ScrollingParameterTable,
    SeverityMeter,
    Thickness,
  ],
  entryComponents: [
    dialogComponents,
    viewers,
    Frame,
  ]
})
export class MonitorModule {
}
