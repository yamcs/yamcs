import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { MdbRoutingModule, routingComponents } from './mdb-routing.module';
import { MdbPageTemplateComponent } from './components/mdb-page-template.component';
import { MdbToolbarComponent } from './components/mdb-toolbar.component';
import { ParametersTableComponent } from './components/parameters-table.component';

@NgModule({
  imports: [
    SharedModule,
    MdbRoutingModule,
  ],
  declarations: [
    routingComponents,
    MdbPageTemplateComponent,
    MdbToolbarComponent,
    ParametersTableComponent,
  ]
})
export class MdbModule {
}
