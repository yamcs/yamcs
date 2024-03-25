import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CustomPreloadingStrategy } from './CustomPreloadingStrategy';
import { ContextSwitchComponent } from './appbase/context-switch/context-switch.component';
import { CreateInstancePage1Component } from './appbase/create-instance-page1/create-instance-page1.component';
import { CreateInstancePage2Component } from './appbase/create-instance-page2/create-instance-page2.component';
import { ExtensionComponent } from './appbase/extension/extension.component';
import { ForbiddenComponent } from './appbase/forbidden/forbidden.component';
import { HomeComponent } from './appbase/home/home.component';
import { NotFoundComponent } from './appbase/not-found/not-found.component';
import { ProfileComponent } from './appbase/profile/profile.component';
import { ServerUnavailableComponent } from './appbase/server-unavailable/server-unavailable.component';
import { attachContextGuardFn } from './core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from './core/guards/AuthGuard';
import { clearContextGuardFn } from './core/guards/ClearContextGuard';
import { openIDCallbackGuardFn } from './core/guards/OpenIDCallbackGuard';
import { serverSideOpenIDCallbackGuardFn } from './core/guards/ServerSideOpenIDCallbackGuard';
import { InstancePage } from './shared/template/InstancePage';

const routes: Routes = [{
  path: '',
  children: [{
    path: '',
    pathMatch: 'full',
    component: HomeComponent,
    canActivate: [authGuardFn, clearContextGuardFn],
    data: { 'hasSidebar': false }
  }, {
    path: 'create-instance',
    pathMatch: 'full',
    component: CreateInstancePage1Component,
    canActivate: [authGuardFn, clearContextGuardFn],
    data: { 'hasSidebar': false }
  }, {
    path: 'create-instance/:template',
    component: CreateInstancePage2Component,
    canActivate: [authGuardFn, clearContextGuardFn],
    data: { 'hasSidebar': false }
  }, {
    path: 'context-switch/:context/:current',
    component: ContextSwitchComponent,
    canActivate: [authGuardFn, clearContextGuardFn],
    data: { 'hasSidebar': false }
  }, {
    path: 'profile',
    component: ProfileComponent,
    canActivate: [authGuardFn, clearContextGuardFn],
    data: { 'hasSidebar': false }
  }, {
    path: 'storage',
    loadChildren: () => import('projects/webapp/src/app/storage/storage-routing.module').then(m => m.StorageRoutingModule),
    canActivate: [authGuardFn],
    data: { 'hasSidebar': false }
  }, {
    path: 'activities',
    loadChildren: () => import('projects/webapp/src/app/activities/activities-routing.module').then(m => m.ActivitiesRoutingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'alarms',
    loadChildren: () => import('projects/webapp/src/app/alarms/alarms-routing.module').then(m => m.AlarmsRoutingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'algorithms',
    loadChildren: () => import('projects/webapp/src/app/algorithms/algorithms-routing.module').then(m => m.AlgorithmsRoutingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'archive',
    loadChildren: () => import('projects/webapp/src/app/archive/archive-routing.module').then(m => m.ArchiveRoutingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'admin',
    loadChildren: () => import('projects/webapp/src/app/admin/AdminModule').then(m => m.AdminModule),
    canActivate: [authGuardFn],
  }, {
    path: 'filetransfer',
    loadChildren: () => import('projects/webapp/src/app/filetransfer/FileTransferModule').then(m => m.FileTransferModule),
    canActivate: [authGuardFn],
  }, {
    path: 'commanding',
    loadChildren: () => import('projects/webapp/src/app/commanding/CommandingModule').then(m => m.CommandingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'events',
    loadChildren: () => import('projects/webapp/src/app/events/events-routing.module').then(m => m.EventsRoutingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'gaps',
    loadChildren: () => import('projects/webapp/src/app/gaps/gaps-routing.module').then(m => m.GapsRoutingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'instance',
    loadChildren: () => import('projects/webapp/src/app/instance-home/instance-home-routing.module').then(m => m.InstanceHomeRoutingModule),
    canActivate: [authGuardFn],
    data: { preload: true },
  }, {
    path: 'links',
    loadChildren: () => import('projects/webapp/src/app/links/links-routing.module').then(m => m.LinksRoutingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'procedures',
    loadChildren: () => import('projects/webapp/src/app/procedures/procedures-routing.module').then(m => m.ProceduresRoutingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'search',
    loadChildren: () => import('projects/webapp/src/app/search/search-routing.module').then(m => m.SearchRoutingModule),
    canActivate: [authGuardFn],
    data: { preload: true },
  }, {
    path: 'timeline',
    loadChildren: () => import('projects/webapp/src/app/timeline/timeline-routing.module').then(m => m.TimelineRoutingModule),
    canActivate: [authGuardFn],
  }, {
    path: 'telemetry',
    loadChildren: () => import('projects/webapp/src/app/telemetry/TelemetryModule').then(m => m.TelemetryModule),
    canActivate: [authGuardFn],
    data: { preload: true },
  }, {
    path: 'mdb',
    loadChildren: () => import('projects/webapp/src/app/mdb/MdbModule').then(m => m.MdbModule),
    canActivate: [authGuardFn],
  }, {
    path: 'ext',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    component: InstancePage,
    children: [{
      path: ':extension',
      component: ExtensionComponent,
    }]
  }, {
    path: 'cb',
    canActivate: [clearContextGuardFn, openIDCallbackGuardFn],
    children: [],
    data: { 'hasSidebar': false }
  }, {
    path: 'oidc-browser-callback',
    canActivate: [clearContextGuardFn, serverSideOpenIDCallbackGuardFn],
    children: [],
    data: { 'hasSidebar': false }
  }, {
    path: 'down',
    component: ServerUnavailableComponent,
    canActivate: [clearContextGuardFn],
    data: { 'hasSidebar': false }
  }, {
    path: '403',
    component: ForbiddenComponent,
    canActivate: [clearContextGuardFn],
    data: { 'hasSidebar': false }
  }, {
    path: '**',
    component: NotFoundComponent,
    canActivate: [clearContextGuardFn],
    data: { 'hasSidebar': false }
  }]
}];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      onSameUrlNavigation: 'reload',
      preloadingStrategy: CustomPreloadingStrategy,
      bindToComponentInputs: true,
      paramsInheritanceStrategy: 'always',
    }),
  ],
  exports: [RouterModule],
})
export class AppRoutingModule { }
