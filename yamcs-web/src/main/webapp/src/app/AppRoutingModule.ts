import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/guards/AuthGuard';
import { ClearContextGuard } from './core/guards/ClearContextGuard';
import { CreateInstancePage1 } from './core/pages/CreateInstancePage1';
import { CreateInstancePage2 } from './core/pages/CreateInstancePage2';
import { ForbiddenPage } from './core/pages/ForbiddenPage';
import { HomePage } from './core/pages/HomePage';
import { LoginPage } from './core/pages/LoginPage';
import { NotFoundPage } from './core/pages/NotFoundPage';
import { ProfilePage } from './core/pages/ProfilePage';
import { ServerUnavailablePage } from './core/pages/ServerUnavailablePage';
import { CustomPreloadingStrategy } from './CustomPreloadingStrategy';

/*
 * Notice that nested modules also have AuthGuards.
 * These will fully execute before other guards in those modules.
 */

const routes: Routes = [
  {
    path: '',
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: HomePage,
        canActivate: [AuthGuard, ClearContextGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: 'create-instance',
        pathMatch: 'full',
        component: CreateInstancePage1,
        canActivate: [AuthGuard, ClearContextGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: 'create-instance/:template',
        component: CreateInstancePage2,
        canActivate: [AuthGuard, ClearContextGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: 'profile',
        component: ProfilePage,
        canActivate: [AuthGuard, ClearContextGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: 'alarms',
        loadChildren: () => import('src/app/alarms/AlarmsModule').then(m => m.AlarmsModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'archive',
        loadChildren: () => import('src/app/archive/ArchiveModule').then(m => m.ArchiveModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'admin',
        loadChildren: () => import('src/app/admin/AdminModule').then(m => m.AdminModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'cfdp',
        loadChildren: () => import('src/app/cfdp/CfdpModule').then(m => m.CfdpModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'commanding',
        loadChildren: () => import('src/app/commanding/CommandingModule').then(m => m.CommandingModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'events',
        loadChildren: () => import('src/app/events/EventsModule').then(m => m.EventsModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'instance',
        loadChildren: () => import('src/app/instancehome/InstanceHomeModule').then(m => m.InstanceHomeModule),
        canActivate: [AuthGuard],
        data: { preload: true },
      },
      {
        path: 'links',
        loadChildren: () => import('src/app/links/LinksModule').then(m => m.LinksModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'telemetry',
        loadChildren: () => import('src/app/telemetry/TelemetryModule').then(m => m.TelemetryModule),
        canActivate: [AuthGuard],
        data: { preload: true },
      },
      {
        path: 'mdb',
        loadChildren: () => import('src/app/mdb/MdbModule').then(m => m.MdbModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'login',
        component: LoginPage,
        canActivate: [ClearContextGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: 'down',
        component: ServerUnavailablePage,
        canActivate: [ClearContextGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: '403',
        component: ForbiddenPage,
        canActivate: [ClearContextGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: '**',
        component: NotFoundPage,
        canActivate: [ClearContextGuard],
        data: { 'hasSidebar': false }
      },
    ]
  },
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      onSameUrlNavigation: 'reload',  // See MonitorPage.ts for documentation
      preloadingStrategy: CustomPreloadingStrategy,
    }),
  ],
  exports: [RouterModule],
})
export class AppRoutingModule { }
