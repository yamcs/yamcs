import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { SystemRoutingModule, routingComponents } from './system-routing.module';
import { SystemToolbarComponent } from './components/system-toolbar.component';
import { SystemPageTemplateComponent } from './components/system-page-template.component';
import { RecordComponent } from './components/record.component';
import { HexComponent } from './components/hex.component';

@NgModule({
  imports: [
    SharedModule,
    SystemRoutingModule,
  ],
  declarations: [
    routingComponents,
    HexComponent,
    RecordComponent,
    SystemPageTemplateComponent,
    SystemToolbarComponent,
  ]
})
export class SystemModule {
}
