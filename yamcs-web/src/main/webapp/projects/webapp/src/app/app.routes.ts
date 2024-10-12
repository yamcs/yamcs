import { Routes } from '@angular/router';
import { ContextSwitchComponent } from './appbase/context-switch/context-switch.component';
import { CreateInstancePage1Component } from './appbase/create-instance-page1/create-instance-page1.component';
import { CreateInstancePage2Component } from './appbase/create-instance-page2/create-instance-page2.component';
import { ExtensionComponent } from './appbase/extension/extension.component';
import { extensionMatcher } from './appbase/extension/extension.matcher';
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
import { InstancePageComponent } from './shared/instance-page/instance-page.component';

export const APP_ROUTES: Routes = [{
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
    loadChildren: () => import('projects/webapp/src/app/storage/storage.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
    data: { 'hasSidebar': false }
  }, {
    path: 'activities',
    loadChildren: () => import('projects/webapp/src/app/activities/activities.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'alarms',
    loadChildren: () => import('projects/webapp/src/app/alarms/alarms.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'algorithms',
    loadChildren: () => import('projects/webapp/src/app/algorithms/algorithms.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'archive',
    loadChildren: () => import('projects/webapp/src/app/archive/archive.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'admin',
    loadChildren: () => import('projects/webapp/src/app/admin/admin.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'file-transfer',
    loadChildren: () => import('projects/webapp/src/app/file-transfer/file-transfer.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'commanding',
    loadChildren: () => import('projects/webapp/src/app/commanding/commanding.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'events',
    loadChildren: () => import('projects/webapp/src/app/events/events.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'instance',
    loadChildren: () => import('projects/webapp/src/app/instance-home/instance-home.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
    data: { preload: true },
  }, {
    path: 'links',
    loadChildren: () => import('projects/webapp/src/app/links/links.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'procedures',
    loadChildren: () => import('projects/webapp/src/app/procedures/procedures.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'search',
    loadChildren: () => import('projects/webapp/src/app/search/search.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
    data: { preload: true },
  }, {
    path: 'timeline',
    loadChildren: () => import('projects/webapp/src/app/timeline/timeline.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'telemetry',
    loadChildren: () => import('projects/webapp/src/app/telemetry/telemetry.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
    data: { preload: true },
  }, {
    path: 'mdb',
    loadChildren: () => import('projects/webapp/src/app/mdb/mdb.routes').then(m => m.ROUTES),
    canActivate: [authGuardFn],
  }, {
    path: 'ext',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    component: InstancePageComponent,
    children: [{
      matcher: extensionMatcher,
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
