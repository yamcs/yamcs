import { NgModule } from '@angular/core';
import { RouterModule, Routes, UrlMatcher, UrlSegment } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { SharedModule } from '../shared/SharedModule';
import { InstancePage } from '../shared/template/InstancePage';
import { CreateDisplayDialog } from './displays/CreateDisplayDialog';
import { CreateFolderDialog } from './displays/CreateFolderDialog';
import { DisplayFilePage } from './displays/DisplayFilePage';
import { DisplayFilePageDirtyDialog } from './displays/DisplayFilePageDirtyDialog';
import { DisplayFilePageDirtyGuard, displayFilePageDirtyGuardFn } from './displays/DisplayFilePageDirtyGuard';
import { DisplayFolderPage } from './displays/DisplayFolderPage';
import { DisplayPage } from './displays/DisplayPage';
import { DisplaysPage } from './displays/DisplaysPage';
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
import { PacketPage } from './packets/PacketPage';
import { PacketsPage } from './packets/PacketsPage';
import { CreateParameterListPage } from './parameter-lists/CreateParameterListPage';
import { EditParameterListPage } from './parameter-lists/EditParameterListPage';
import { ParameterListHistoricalDataTab } from './parameter-lists/ParameterListHistoricalDataTab';
import { ParameterListPage } from './parameter-lists/ParameterListPage';
import { ParameterListSummaryTab } from './parameter-lists/ParameterListSummaryTab';
import { ParameterListsPage } from './parameter-lists/ParameterListsPage';
import { ColorPalette } from './parameters/ColorPalette';
import { CompareParameterDialog } from './parameters/CompareParameterDialog';
import { ExportParameterDataDialog } from './parameters/ExportParameterDataDialog';
import { ModifyParameterDialog } from './parameters/ModifyParameterDialog';
import { ParameterAlarmsTab } from './parameters/ParameterAlarmsTab';
import { ParameterAlarmsTable } from './parameters/ParameterAlarmsTable';
import { ParameterChartTab } from './parameters/ParameterChartTab';
import { ParameterDataTab } from './parameters/ParameterDataTab';
import { ParameterDetail } from './parameters/ParameterDetail';
import { ParameterForm } from './parameters/ParameterForm';
import { ParameterPage } from './parameters/ParameterPage';
import { ParameterSummaryTab } from './parameters/ParameterSummaryTab';
import { ParameterValuesTable } from './parameters/ParameterValuesTable';
import { ParametersPage } from './parameters/ParametersPage';
import { SelectRangeDialog } from './parameters/SelectRangeDialog';
import { SetParameterDialog } from './parameters/SetParameterDialog';
import { SeverityMeter } from './parameters/SeverityMeter';
import { Thickness } from './parameters/Thickness';
import { DisplayTypePipe } from './pipes/DisplayTypePipe';
import { PacketDownloadLinkPipe } from './pipes/PacketDownloadLinkPipe';

const packetMatcher: UrlMatcher = url => {
  let consumed = url;

  // Stop consuming at /-/
  // (handled by Angular again)
  const idx = url.findIndex(segment => segment.path === '-');
  if (idx !== -1) {
    consumed = url.slice(0, idx);
  }

  const packet = '/' + consumed.map(segment => segment.path).join('/');
  return {
    consumed,
    posParams: {
      'packet': new UrlSegment(packet, {}),
    },
  };
};

const parameterMatcher: UrlMatcher = url => {
  let consumed = url;

  // Stop consuming at /-/
  // (handled by Angular again)
  const idx = url.findIndex(segment => segment.path === '-');
  if (idx !== -1) {
    consumed = url.slice(0, idx);
  }

  const parameter = '/' + consumed.map(segment => segment.path).join('/');
  return {
    consumed,
    posParams: {
      'parameter': new UrlSegment(parameter, {}),
    },
  };
};

const routes: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePage,
  children: [{
    path: '',
    pathMatch: 'full',
    redirectTo: 'home',
  }, {
    path: 'displays',
    pathMatch: 'full',
    redirectTo: 'displays/browse'
  }, {
    path: 'displays/browse',
    component: DisplaysPage,
    children: [{
      path: '**',
      component: DisplayFolderPage,
    }]
  }, {
    path: 'displays/files',
    component: DisplayPage,
    children: [{
      path: '**',
      component: DisplayFilePage,
      canDeactivate: [displayFilePageDirtyGuardFn],
    }]
  }, {
    path: 'packets',
    children: [{
      path: '',
      pathMatch: 'full',
      component: PacketsPage,
    }, {
      matcher: packetMatcher,
      children: [{
        path: '-/log/:gentime/:seqno',
        component: PacketPage,
      }]
    }]
  }, {
    path: 'parameters',
    children: [{
      path: '',
      pathMatch: 'full',
      component: ParametersPage,
      title: 'Parameters',
    }, {
      matcher: parameterMatcher,
      component: ParameterPage,
      children: [{
        path: '',
        pathMatch: 'full',
        redirectTo: '-/summary'
      }, {
        path: '-/summary',
        component: ParameterSummaryTab,
      }, {
        path: '-/chart',
        component: ParameterChartTab,
      }, {
        path: '-/data',
        component: ParameterDataTab,
      }, {
        path: '-/alarms',
        component: ParameterAlarmsTab,
      }]
    }]
  }, {
    path: 'parameter-lists',
    pathMatch: 'full',
    component: ParameterListsPage,
  }, {
    path: 'parameter-lists/create',
    pathMatch: 'full',
    component: CreateParameterListPage,
  }, {
    path: 'parameter-lists/:list',
    component: ParameterListPage,
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: 'realtime',
    }, {
      path: 'realtime',
      component: ParameterListSummaryTab,
    }, {
      path: 'data',
      component: ParameterListHistoricalDataTab,
    }],
  }, {
    path: 'parameter-lists/:list/edit',
    pathMatch: 'full',
    component: EditParameterListPage,
  }]
}];

@NgModule({
  imports: [
    SharedModule,
    RouterModule.forChild(routes),
  ],
  providers: [
    DisplayFilePageDirtyGuard,
  ],
  declarations: [
    ColorPalette,
    CompareParameterDialog,
    CreateDisplayDialog,
    CreateFolderDialog,
    CreateParameterListPage,
    DisplayFilePage,
    DisplayFilePageDirtyDialog,
    DisplayFolderPage,
    DisplayPage,
    DisplaysPage,
    DisplayTypePipe,
    EditParameterListPage,
    ExportArchiveDataDialog,
    ExportParameterDataDialog,
    ImageViewer,
    ModifyParameterDialog,
    MultipleParameterTable,
    OpiDisplayViewer,
    OpiDisplayViewerControls,
    PacketDownloadLinkPipe,
    PacketPage,
    PacketsPage,
    ParameterAlarmsTab,
    ParameterAlarmsTable,
    ParameterChartTab,
    ParameterDataTab,
    ParameterDetail,
    ParameterForm,
    ParameterListHistoricalDataTab,
    ParameterListPage,
    ParameterListsPage,
    ParameterListSummaryTab,
    ParameterPage,
    ParametersPage,
    ParameterSummaryTab,
    ParameterTableViewer,
    ParameterTableViewerControls,
    ParameterValuesTable,
    RenameDisplayDialog,
    ScriptViewer,
    ScriptViewerControls,
    ScrollingParameterTable,
    SelectRangeDialog,
    SetParameterDialog,
    SeverityMeter,
    TextViewer,
    Thickness,
    ViewerControlsHost,
    ViewerHost,
  ],
})
export class TelemetryModule {
}
