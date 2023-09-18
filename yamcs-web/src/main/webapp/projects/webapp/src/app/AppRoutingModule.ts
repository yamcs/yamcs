import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CustomPreloadingStrategy } from './CustomPreloadingStrategy';
import { ContextSwitchPage } from './appbase/pages/ContextSwitchPage';
import { CreateInstancePage1 } from './appbase/pages/CreateInstancePage1';
import { CreateInstancePage2 } from './appbase/pages/CreateInstancePage2';
import { ExtensionPage } from './appbase/pages/ExtensionPage';
import { ForbiddenPage } from './appbase/pages/ForbiddenPage';
import { HomePage } from './appbase/pages/HomePage';
import { NotFoundPage } from './appbase/pages/NotFoundPage';
import { ProfilePage } from './appbase/pages/ProfilePage';
import { ServerUnavailablePage } from './appbase/pages/ServerUnavailablePage';
import { attachContextGuardFn } from './core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from './core/guards/AuthGuard';
import { clearContextGuardFn } from './core/guards/ClearContextGuard';
import { openIDCallbackGuardFn } from './core/guards/OpenIDCallbackGuard';
import { serverSideOpenIDCallbackGuardFn } from './core/guards/ServerSideOpenIDCallbackGuard';
import { InstancePage } from './shared/template/InstancePage';

const routes: Routes = [
  {
    path: '',
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: HomePage,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { 'hasSidebar': false }
      }, {
        path: 'create-instance',
        pathMatch: 'full',
        component: CreateInstancePage1,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { 'hasSidebar': false }
      }, {
        path: 'create-instance/:template',
        component: CreateInstancePage2,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { 'hasSidebar': false }
      }, {
        path: 'context-switch/:context/:current',
        component: ContextSwitchPage,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { 'hasSidebar': false }
      }, {
        path: 'profile',
        component: ProfilePage,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { 'hasSidebar': false }
      }, {
        path: 'storage',
        loadChildren: () => import('projects/webapp/src/app/storage/StorageModule').then(m => m.StorageModule),
        canActivate: [authGuardFn],
        data: { 'hasSidebar': false }
      }, {
        path: 'alarms',
        loadChildren: () => import('projects/webapp/src/app/alarms/AlarmsModule').then(m => m.AlarmsModule),
        canActivate: [authGuardFn],
      }, {
        path: 'algorithms',
        loadChildren: () => import('projects/webapp/src/app/algorithms/AlgorithmsModule').then(m => m.AlgorithmsModule),
        canActivate: [authGuardFn],
      }, {
        path: 'archive',
        loadChildren: () => import('projects/webapp/src/app/archive/ArchiveModule').then(m => m.ArchiveModule),
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
        loadChildren: () => import('projects/webapp/src/app/events/EventsModule').then(m => m.EventsModule),
        canActivate: [authGuardFn],
      }, {
        path: 'gaps',
        loadChildren: () => import('projects/webapp/src/app/gaps/GapsModule').then(m => m.GapsModule),
        canActivate: [authGuardFn],
      }, {
        path: 'instance',
        loadChildren: () => import('projects/webapp/src/app/instancehome/InstanceHomeModule').then(m => m.InstanceHomeModule),
        canActivate: [authGuardFn],
        data: { preload: true },
      }, {
        path: 'links',
        loadChildren: () => import('projects/webapp/src/app/links/LinksModule').then(m => m.LinksModule),
        canActivate: [authGuardFn],
      }, {
        path: 'search',
        loadChildren: () => import('projects/webapp/src/app/search/SearchModule').then(m => m.SearchModule),
        canActivate: [authGuardFn],
        data: { preload: true },
      }, {
        path: 'timeline',
        loadChildren: () => import('projects/webapp/src/app/timeline/TimelineModule').then(m => m.TimelineModule),
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
          component: ExtensionPage,
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
        component: ServerUnavailablePage,
        canActivate: [clearContextGuardFn],
        data: { 'hasSidebar': false }
      }, {
        path: '403',
        component: ForbiddenPage,
        canActivate: [clearContextGuardFn],
        data: { 'hasSidebar': false }
      }, {
        path: '**',
        component: NotFoundPage,
        canActivate: [clearContextGuardFn],
        data: { 'hasSidebar': false }
      },
    ]
  },
];

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
