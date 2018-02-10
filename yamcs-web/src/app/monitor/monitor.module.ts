import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { MonitorRoutingModule, routingComponents } from './monitor-routing.module';
import { MonitorPageTemplateComponent } from './components/monitor-page-template.component';
import { ProcessorInfoComponent } from './components/processor-info.component';
import { SeverityComponent } from './components/severity.component';

@NgModule({
  imports: [
    SharedModule,
    MonitorRoutingModule,
  ],
  declarations: [
    routingComponents,
    MonitorPageTemplateComponent,
    ProcessorInfoComponent,
    SeverityComponent,
  ]
})
export class MonitorModule {
}
