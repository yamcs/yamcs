import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { SuperuserGuard } from '../core/guards/SuperuserGuard';
import { UnselectInstanceGuard } from '../core/guards/UnselectInstanceGuard';
import { DatabasePage } from './DatabasePage';
import { DatabasesPage } from './DatabasesPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, SuperuserGuard, UnselectInstanceGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'databases'
      },
      {
        path: 'databases',
        pathMatch: 'full',
        component: DatabasesPage,
      },
      {
        path: 'databases/:tablespace',
        children: [
          {
            path: '**',
            component: DatabasePage,
          }
        ]
      },
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class RocksDbRoutingModule { }

export const routingComponents = [
  DatabasesPage,
  DatabasePage,
];
