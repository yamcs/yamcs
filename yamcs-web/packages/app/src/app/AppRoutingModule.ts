import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { NotFoundPage } from './core/pages/NotFoundPage';
import { InstanceExistsGuard } from './core/guards/InstanceExistsGuard';
import { HomePage } from './core/pages/HomePage';
import { ProfilePage } from './core/pages/ProfilePage';

const routes: Routes = [
  {
    path: '',
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: HomePage,
      },
      {
        path: 'monitor',
        loadChildren: 'app/monitor/MonitorModule#MonitorModule',
      },
      {
        path: 'mdb',
        loadChildren: 'app/mdb/MdbModule#MdbModule',
      },
      {
        path: 'system',
        loadChildren: 'app/system/SystemModule#SystemModule',
      },
      {
        path: 'profile',
        component: ProfilePage,
      },
      {
        path: '**',
        component: NotFoundPage,
      },
    ]
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {
    onSameUrlNavigation: 'reload'  // See MonitorPage.ts for documentation
  }) ],
  exports: [ RouterModule ],
  providers: [
    InstanceExistsGuard,
  ]
})
export class AppRoutingModule { }
