import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/SharedModule';
import { AdminSharedModule } from '../shared/AdminSharedModule';
import { StreamDataComponent } from './database/stream/StreamDataComponent';
import { RecordComponent } from './database/table/RecordComponent';
import { ShowEnumDialog } from './database/table/ShowEnumDialog';
import { DatabasesRoutingModule, routingComponents } from './DatabasesRoutingModule';
import { ColumnValuePipe } from './pipes/ColumnValuePipe';
import { Shell } from './shell/Shell';

const pipes = [
  ColumnValuePipe,
];

@NgModule({
  imports: [
    SharedModule,
    AdminSharedModule,
    DatabasesRoutingModule,
  ],
  declarations: [
    pipes,
    routingComponents,
    RecordComponent,
    Shell,
    ShowEnumDialog,
    StreamDataComponent,
  ],
})
export class DatabasesModule {
}
