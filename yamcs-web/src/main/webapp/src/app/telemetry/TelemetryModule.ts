import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CreateDisplayDialog } from './displays/CreateDisplayDialog';
import { CreateFolderDialog } from './displays/CreateFolderDialog';
import { DisplayFilePageDirtyDialog } from './displays/DisplayFilePageDirtyDialog';
import { ExportArchiveDataDialog } from './displays/ExportArchiveDataDialog';
import { ImageViewer } from './displays/ImageViewer';
import { MultipleParameterTable } from './displays/MultipleParameterTable';
import { OpiDisplayViewer } from './displays/OpiDisplayViewer';
import { OpiDisplayViewerControls } from './displays/OpiDisplayViewerControls';
import { ParameterTableViewer } from './displays/ParameterTableViewer';
import { ParameterTableViewerControls } from './displays/ParameterTableViewerControls';
import { RenameDisplayDialog } from './displays/RenameDisplayDialog';
import { ScriptViewer } from './displays/ScriptViewer';
import { ScriptViewerControls } from './displays/ScriptViewerControls';
import { ScrollingParameterTable } from './displays/ScrollingParameterTable';
import { TextViewer } from './displays/TextViewer';
import { ViewerControlsHost } from './displays/ViewerControlsHost';
import { ViewerHost } from './displays/ViewerHost';
import { ColorPalette } from './parameters/ColorPalette';
import { CompareParameterDialog } from './parameters/CompareParameterDialog';
import { ExportParameterDataDialog } from './parameters/ExportParameterDataDialog';
import { ModifyParameterDialog } from './parameters/ModifyParameterDialog';
import { ParameterAlarmsTable } from './parameters/ParameterAlarmsTable';
import { ParameterDetail } from './parameters/ParameterDetail';
import { ParameterForm } from './parameters/ParameterForm';
import { ParameterValuesTable } from './parameters/ParameterValuesTable';
import { SelectRangeDialog } from './parameters/SelectRangeDialog';
import { SetParameterDialog } from './parameters/SetParameterDialog';
import { SeverityMeter } from './parameters/SeverityMeter';
import { Thickness } from './parameters/Thickness';
import { DisplayTypePipe } from './pipes/DisplayTypePipe';
import { PacketDownloadLinkPipe } from './pipes/PacketDownloadLinkPipe';
import { routingComponents, TelemetryRoutingModule } from './TelemetryRoutingModule';

const pipes = [
  DisplayTypePipe,
  PacketDownloadLinkPipe,
];

const directives = [
  ViewerControlsHost,
  ViewerHost,
];

const viewers = [
  ImageViewer,
  OpiDisplayViewer,
  OpiDisplayViewerControls,
  ParameterTableViewer,
  ParameterTableViewerControls,
  ScriptViewer,
  ScriptViewerControls,
  TextViewer,
];

@NgModule({
  imports: [
    SharedModule,
    TelemetryRoutingModule,
  ],
  declarations: [
    routingComponents,
    directives,
    pipes,
    viewers,
    ColorPalette,
    CompareParameterDialog,
    CreateDisplayDialog,
    CreateFolderDialog,
    DisplayFilePageDirtyDialog,
    ExportArchiveDataDialog,
    ExportParameterDataDialog,
    ModifyParameterDialog,
    MultipleParameterTable,
    ParameterAlarmsTable,
    ParameterDetail,
    ParameterForm,
    ParameterValuesTable,
    RenameDisplayDialog,
    ScrollingParameterTable,
    SelectRangeDialog,
    SetParameterDialog,
    SeverityMeter,
    Thickness,
  ],
})
export class TelemetryModule {
}
