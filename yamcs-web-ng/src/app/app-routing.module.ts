import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { InstancePageComponent } from './core/pages/instance.component';
import { InstancesPageComponent } from './core/pages/instances.component';
import { NotFoundPageComponent } from './core/pages/not-found.component';
import { InstanceExistsGuard } from './core/guards/instance-exists.guard';

const routes: Routes = [
  {
    path: '',
    component: InstancesPageComponent,
    pathMatch: 'full',
  },
  { // Escape hatch for forced 404s so that :instance route
    // does not trigger instead.
    path: '404',
    component: NotFoundPageComponent,
  },
  {
    path: ':instance',
    canActivate: [ InstanceExistsGuard ],
    component: InstancePageComponent,
    children: [
      {
        path: 'mdb', loadChildren: 'app/mdb/mdb.module#MdbModule'
      },
    ]
  },
  { path: '**', component: NotFoundPageComponent },
];

@NgModule({
  imports: [ RouterModule.forRoot(routes) ],
  exports: [ RouterModule ],
  providers: [
    InstanceExistsGuard,
  ]
})
export class AppRoutingModule { }
