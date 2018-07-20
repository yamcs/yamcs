import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { ForbiddenPage } from './apputil/pages/ForbiddenPage';
import { HomePage } from './apputil/pages/HomePage';
import { LoginPage } from './apputil/pages/LoginPage';
import { NotFoundPage } from './apputil/pages/NotFoundPage';
import { ProfilePage } from './apputil/pages/ProfilePage';
import { ServerUnavailablePage } from './apputil/pages/ServerUnavailablePage';
import { AuthGuard } from './core/guards/AuthGuard';
import { UnselectInstanceGuard } from './core/guards/UnselectInstanceGuard';

/*
 * Notes that the nested modules also have AuthGuards.
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
      },
      {
        path: 'monitor',
        loadChildren: 'src/app/monitor/MonitorModule#MonitorModule',
        canActivate: [AuthGuard],
      },
      {
        path: 'mdb',
        loadChildren: 'src/app/mdb/MdbModule#MdbModule',
        canActivate: [AuthGuard],
      },
      {
        path: 'system',
        loadChildren: 'src/app/system/SystemModule#SystemModule',
        canActivate: [AuthGuard],
      },
      {
        path: 'profile',
        component: ProfilePage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'login',
        component: LoginPage,
        canActivate: [UnselectInstanceGuard],
      },
      {
        path: 'down',
        component: ServerUnavailablePage,
        canActivate: [UnselectInstanceGuard],
      },
      {
        path: '403',
        component: ForbiddenPage,
        canActivate: [UnselectInstanceGuard],
      },
      {
        path: '**',
        component: NotFoundPage,
        canActivate: [UnselectInstanceGuard],
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
