import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ClientsPageComponent } from './pages/clients.component';

const routes = [
  {
    path: '',
    component: ClientsPageComponent,
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class ClientsRoutingModule { }

export const routingComponents = [
  ClientsPageComponent,
];
