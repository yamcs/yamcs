import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { CreateInstancePage1 } from './apputil/pages/CreateInstancePage1';
import { CreateInstancePage2 } from './apputil/pages/CreateInstancePage2';
import { ForbiddenPage } from './apputil/pages/ForbiddenPage';
import { HomePage } from './apputil/pages/HomePage';
import { LoginPage } from './apputil/pages/LoginPage';
import { NotFoundPage } from './apputil/pages/NotFoundPage';
import { ProfilePage } from './apputil/pages/ProfilePage';
import { ServerUnavailablePage } from './apputil/pages/ServerUnavailablePage';
import { AuthGuard } from './core/guards/AuthGuard';
import { UnselectInstanceGuard } from './core/guards/UnselectInstanceGuard';

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
        canActivate: [AuthGuard, UnselectInstanceGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: 'create-instance',
        pathMatch: 'full',
        component: CreateInstancePage1,
        canActivate: [AuthGuard, UnselectInstanceGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: 'create-instance/:template',
        component: CreateInstancePage2,
        canActivate: [AuthGuard, UnselectInstanceGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: 'profile',
        component: ProfilePage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
        data: { 'hasSidebar': false }
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
        path: 'instance',
        loadChildren: () => import('src/app/instancehome/InstanceHomeModule').then(m => m.InstanceHomeModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'links',
        loadChildren: () => import('src/app/links/LinksModule').then(m => m.LinksModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'monitor',
        loadChildren: () => import('src/app/monitor/MonitorModule').then(m => m.MonitorModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'mdb',
        loadChildren: () => import('src/app/mdb/MdbModule').then(m => m.MdbModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'system',
        loadChildren: () => import('src/app/system/SystemModule').then(m => m.SystemModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'login',
        component: LoginPage,
        canActivate: [UnselectInstanceGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: 'down',
        component: ServerUnavailablePage,
        canActivate: [UnselectInstanceGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: '403',
        component: ForbiddenPage,
        canActivate: [UnselectInstanceGuard],
        data: { 'hasSidebar': false }
      },
      {
        path: '**',
        component: NotFoundPage,
        canActivate: [UnselectInstanceGuard],
        data: { 'hasSidebar': false }
      },
    ]
  },
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      onSameUrlNavigation: 'reload',  // See MonitorPage.ts for documentation
      preloadingStrategy: PreloadAllModules,
    }),
  ],
  exports: [ RouterModule ],
})
export class AppRoutingModule { }
