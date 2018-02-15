import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { NotFoundPageComponent } from './core/pages/not-found.component';
import { InstanceExistsGuard } from './core/guards/instance-exists.guard';
import { HomePageComponent } from './core/pages/home.component';
import { ProfileComponent } from './core/pages/profile.component';

const routes: Routes = [
  {
    path: '',
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: HomePageComponent
      },
      {
        path: 'monitor',
        loadChildren: 'app/monitor/monitor.module#MonitorModule',
      },
      {
        path: 'mdb',
        loadChildren: 'app/mdb/mdb.module#MdbModule',
      },
      {
        path: 'system',
        loadChildren: 'app/system/system.module#SystemModule',
      },
      {
        path: 'profile',
        component: ProfileComponent,
      },
      {
        path: '**',
        component: NotFoundPageComponent,
      },
    ]
  },
];

@NgModule({
  imports: [ RouterModule.forRoot(routes) ],
  exports: [ RouterModule ],
  providers: [
    InstanceExistsGuard,
  ]
})
export class AppRoutingModule { }
