import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ServicesPageComponent } from './pages/services.component';

const routes = [
  {
    path: '',
    component: ServicesPageComponent,
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class ServicesRoutingModule { }

export const routingComponents = [
  ServicesPageComponent,
];
