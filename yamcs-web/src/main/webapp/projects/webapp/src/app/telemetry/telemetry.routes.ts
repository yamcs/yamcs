import { Routes, UrlMatcher, UrlSegment } from '@angular/router';
import { ExtensionComponent } from '../appbase/extension/extension.component';
import { extensionMatcher } from '../appbase/extension/extension.matcher';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { DisplayFilePageDirtyGuard, displayFilePageDirtyGuardFn } from './displays/display-file-dirty-guard/display-file-dirty.guard';
import { DisplayFileComponent } from './displays/display-file/display-file.component';
import { DisplayFolderComponent } from './displays/display-folder/display-folder.component';
import { DisplayPage } from './displays/display-page/display-page.component';
import { DisplaysPage } from './displays/displays-page/displays-page.component';
import { PacketListComponent } from './packets/packet-list/packet-list.component';
import { PacketComponent } from './packets/packet/packet.component';
import { CreateParameterListComponent } from './parameter-lists/create-parameter-list/create-parameter-list.component';
import { EditParameterListComponent } from './parameter-lists/edit-parameter-list/edit-parameter-list.component';
import { ParameterListHistoricalDataTabComponent } from './parameter-lists/parameter-list-historical-data-tab/parameter-list-historical-data-tab.component';
import { ParameterListListComponent } from './parameter-lists/parameter-list-list/parameter-list-list.component';
import { ParameterListSummaryTabComponent } from './parameter-lists/parameter-list-summary-tab/parameter-list-summary-tab.component';
import { ParameterListComponent } from './parameter-lists/parameter-list/parameter-list.component';
import { ParameterAlarmsTabComponent } from './parameters/parameter-alarms-tab/parameter-alarms-tab.component';
import { ParameterChartTabComponent } from './parameters/parameter-chart-tab/parameter-chart-tab.component';
import { ParameterDataTabComponent } from './parameters/parameter-data-tab/parameter-data-tab.component';
import { ParameterSummaryTabComponent } from './parameters/parameter-summary-tab/parameter-summary-tab.component';
import { ParameterComponent } from './parameters/parameter/parameter.component';
import { ParametersComponent } from './parameters/parameters/parameters.component';

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

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
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
      component: DisplayFolderComponent,
    }]
  }, {
    path: 'displays/files',
    component: DisplayPage,
    providers: [
      DisplayFilePageDirtyGuard,
    ],
    children: [{
      path: '**',
      component: DisplayFileComponent,
      canDeactivate: [displayFilePageDirtyGuardFn],
      data: { 'showRangeSelector': true },
    }]
  }, {
    path: 'packets',
    children: [{
      path: '',
      pathMatch: 'full',
      component: PacketListComponent,
    }, {
      matcher: packetMatcher,
      children: [{
        path: '-/log/:gentime/:seqno',
        component: PacketComponent,
      }]
    }]
  }, {
    path: 'parameters',
    children: [{
      path: '',
      pathMatch: 'full',
      component: ParametersComponent,
      title: 'Parameters',
    }, {
      matcher: parameterMatcher,
      component: ParameterComponent,
      children: [{
        path: '',
        pathMatch: 'full',
        redirectTo: '-/summary'
      }, {
        path: '-/summary',
        component: ParameterSummaryTabComponent,
      }, {
        path: '-/chart',
        component: ParameterChartTabComponent,
      }, {
        path: '-/data',
        component: ParameterDataTabComponent,
      }, {
        path: '-/alarms',
        component: ParameterAlarmsTabComponent,
      }]
    }]
  }, {
    path: 'parameter-lists',
    pathMatch: 'full',
    component: ParameterListListComponent,
  }, {
    path: 'parameter-lists/create',
    pathMatch: 'full',
    component: CreateParameterListComponent,
  }, {
    path: 'parameter-lists/:list',
    component: ParameterListComponent,
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: 'realtime',
    }, {
      path: 'realtime',
      component: ParameterListSummaryTabComponent,
    }, {
      path: 'data',
      component: ParameterListHistoricalDataTabComponent,
    }],
  }, {
    path: 'parameter-lists/:list/edit',
    pathMatch: 'full',
    component: EditParameterListComponent,
  }, {
    path: 'ext',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    children: [{
      matcher: extensionMatcher,
      component: ExtensionComponent,
    }]
  }]
}];
