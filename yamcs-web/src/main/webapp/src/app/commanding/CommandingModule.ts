import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { ChangeLevelDialog } from './clearances/ChangeLevelDialog';
import { ClearancesPageTabs } from './clearances/ClearancesPageTabs';
import { AcknowledgmentIcon } from './command-history/AcknowledgmentIcon';
import { CommandDetail } from './command-history/CommandDetail';
import { CommandHistoryPrintable } from './command-history/CommandHistoryPrintable';
import { ExtraAcknowledgmentsTable } from './command-history/ExtraAcknowledgmentsTable';
import { TransmissionConstraintsIcon } from './command-history/TransmissionConstraintsIcon';
import { YamcsAcknowledgmentsTable } from './command-history/YamcsAcknowledgmentsTable';
import { CommandForm } from './command-sender/CommandForm';
import { CommandFormArgument } from './command-sender/CommandFormArgument';
import { SelectEnumerationDialog } from './command-sender/SelectEnumerationDialog';
import { SendCommandWizardStep } from './command-sender/SendCommandWizardStep';
import { CommandingRoutingModule, routingComponents } from './CommandingRoutingModule';
import { CommandDownloadLinkPipe } from './pipes/CommandDownloadLinkPipe';
import { QueuedCommandsTable } from './queues/QueuedCommandsTable';
import { QueuesTable } from './queues/QueuesTable';
import { CreateFolderDialog } from './stacks/CreateFolderDialog';
import { CreateStackDialog } from './stacks/CreateStackDialog';
import { EditStackEntryDialog } from './stacks/EditStackEntryDialog';
import { RenameStackDialog } from './stacks/RenameStackDialog';
import { StackedCommandDetail } from './stacks/StackedCommandDetail';
import { StackFilePageDirtyDialog } from './stacks/StackFilePageDirtyDialog';

const pipes = [
  CommandDownloadLinkPipe,
];

@NgModule({
  imports: [
    SharedModule,
    CommandingRoutingModule,
  ],
  declarations: [
    routingComponents,
    pipes,
    AcknowledgmentIcon,
    EditStackEntryDialog,
    ChangeLevelDialog,
    ClearancesPageTabs,
    CommandDetail,
    CommandForm,
    CommandFormArgument,
    CommandHistoryPrintable,
    CreateFolderDialog,
    CreateStackDialog,
    ExtraAcknowledgmentsTable,
    QueuedCommandsTable,
    QueuesTable,
    RenameStackDialog,
    SelectEnumerationDialog,
    SendCommandWizardStep,
    StackedCommandDetail,
    StackFilePageDirtyDialog,
    TransmissionConstraintsIcon,
    YamcsAcknowledgmentsTable,
  ],
})
export class CommandingModule {
}
