import { Routes } from '@angular/router';
import { CreateInstancePage1Component } from './appbase/create-instance-page1/create-instance-page1.component';
import { CreateInstancePage2Component } from './appbase/create-instance-page2/create-instance-page2.component';
import { ExtensionComponent } from './appbase/extension/extension.component';
import { extensionMatcher } from './appbase/extension/extension.matcher';
import { ForbiddenComponent } from './appbase/forbidden/forbidden.component';
import { HomeComponent } from './appbase/home/home.component';
import { NotFoundComponent } from './appbase/not-found/not-found.component';
import { ProfileComponent } from './appbase/profile/profile.component';
import { RouteRefreshComponent } from './appbase/route-refresh/route-refresh.component';
import { ServerUnavailableComponent } from './appbase/server-unavailable/server-unavailable.component';
import { attachContextGuardFn } from './core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from './core/guards/AuthGuard';
import { clearContextGuardFn } from './core/guards/ClearContextGuard';
import { openIDCallbackGuardFn } from './core/guards/OpenIDCallbackGuard';
import { serverSideOpenIDCallbackGuardFn } from './core/guards/ServerSideOpenIDCallbackGuard';
import { InstanceLayoutComponent } from './shared/instance-layout/instance-layout.component';

export const APP_ROUTES: Routes = [
  {
    path: '',
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: HomeComponent,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { hasSidebar: false },
      },
      {
        path: '403',
        component: ForbiddenComponent,
        canActivate: [clearContextGuardFn],
        data: { hasSidebar: false },
      },
      {
        path: 'admin',
        loadChildren: () =>
          import('./admin/admin.routes').then((m) => m.ROUTES),
        canActivate: [authGuardFn],
      },
      {
        path: 'cb',
        canActivate: [clearContextGuardFn, openIDCallbackGuardFn],
        children: [],
        data: { hasSidebar: false },
      },
      {
        path: 'create-instance',
        pathMatch: 'full',
        component: CreateInstancePage1Component,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { hasSidebar: false },
      },
      {
        path: 'create-instance/:template',
        component: CreateInstancePage2Component,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { hasSidebar: false },
      },
      {
        path: 'down',
        component: ServerUnavailableComponent,
        canActivate: [clearContextGuardFn],
        data: { hasSidebar: false },
      },
      {
        path: 'oidc-browser-callback',
        canActivate: [clearContextGuardFn, serverSideOpenIDCallbackGuardFn],
        children: [],
        data: { hasSidebar: false },
      },
      {
        path: 'profile',
        component: ProfileComponent,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { hasSidebar: false },
      },
      {
        path: 'route-refresh',
        component: RouteRefreshComponent,
        canActivate: [authGuardFn, clearContextGuardFn],
        data: { hasSidebar: false },
      },
      {
        path: 'storage',
        loadChildren: () =>
          import('./storage/storage.routes').then((m) => m.ROUTES),
        canActivate: [authGuardFn],
        data: { hasSidebar: false },
      },
      {
        path: '',
        component: InstanceLayoutComponent,
        data: {
          section: 'instance',
        },
        children: [
          {
            path: 'activities',
            loadChildren: () =>
              import('./activities/activities.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
          {
            path: 'alarms',
            loadChildren: () =>
              import('./alarms/alarms.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
          {
            path: 'algorithms',
            loadChildren: () =>
              import('./algorithms/algorithms.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
          {
            path: 'archive',
            loadChildren: () =>
              import('./archive/archive.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
          {
            path: 'commanding',
            loadChildren: () =>
              import('./commanding/commanding.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
          {
            path: 'events',
            loadChildren: () =>
              import('./events/events.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
          {
            path: 'ext',
            canActivate: [authGuardFn, attachContextGuardFn],
            canActivateChild: [authGuardChildFn],
            runGuardsAndResolvers: 'always',
            children: [
              {
                matcher: extensionMatcher,
                component: ExtensionComponent,
              },
            ],
          },
          {
            path: 'file-transfer',
            loadChildren: () =>
              import('./file-transfer/file-transfer.routes').then(
                (m) => m.ROUTES,
              ),
            canActivate: [authGuardFn],
          },
          {
            path: 'instance',
            loadChildren: () =>
              import('./instance-home/instance-home.routes').then(
                (m) => m.ROUTES,
              ),
            canActivate: [authGuardFn],
            data: { preload: true },
          },
          {
            path: 'links',
            loadChildren: () =>
              import('./links/links.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
          {
            path: 'mdb',
            loadChildren: () =>
              import('./mdb/mdb.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
          {
            path: 'procedures',
            loadChildren: () =>
              import('./procedures/procedures.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
          {
            path: 'search',
            loadChildren: () =>
              import('./search/search.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
            data: { preload: true },
          },
          {
            path: 'telemetry',
            loadChildren: () =>
              import('./telemetry/telemetry.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
            data: { preload: true },
          },
          {
            path: 'timeline',
            loadChildren: () =>
              import('./timeline/timeline.routes').then((m) => m.ROUTES),
            canActivate: [authGuardFn],
          },
        ],
      },
      {
        path: '**',
        component: NotFoundComponent,
        canActivate: [clearContextGuardFn],
        data: { hasSidebar: false },
      },
    ],
  },
];
