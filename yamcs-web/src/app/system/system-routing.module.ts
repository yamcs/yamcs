import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { InstanceExistsGuard } from '../core/guards/instance-exists.guard';
import { SystemPageComponent } from './pages/system.component';

const routes = [
  {
    path: '',
    canActivate: [InstanceExistsGuard],
    component: SystemPageComponent,
    children: [
      { path: 'clients', loadChildren: 'app/clients/clients.module#ClientsModule' },
      { path: 'links', loadChildren: 'app/links/links.module#LinksModule' },
      { path: 'schema', loadChildren: 'app/schema/schema.module#SchemaModule' },
      { path: 'services', loadChildren: 'app/services/services.module#ServicesModule' },
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class SystemRoutingModule { }

export const routingComponents = [
  SystemPageComponent,
];
