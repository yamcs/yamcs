import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { InstancePageComponent } from './core/pages/instance.component';
import { InstancesPageComponent } from './core/pages/instances.component';

const routes: Routes = [
  {
    path: '',
    component: InstancesPageComponent,
    pathMatch: 'full',
  },
  {
    path: ':instance',
    component: InstancePageComponent,
    children: [
      {
        path: 'mdb', loadChildren: 'app/mdb/mdb.module#MdbModule'
      },
    ]
  },
];

@NgModule({
  imports: [ RouterModule.forRoot(routes) ],
  exports: [ RouterModule ],
})
export class AppRoutingModule { }
