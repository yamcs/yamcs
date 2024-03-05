import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { ProceduresRoutingModule, routingComponents } from './ProceduresRoutingModule';
import { ScheduleScriptDialog } from './ScheduleScriptDialog';

@NgModule({
  imports: [
    SharedModule,
    ProceduresRoutingModule,
  ],
  declarations: [
    routingComponents,
    ScheduleScriptDialog,
  ],
})
export class ProceduresModule {
}
