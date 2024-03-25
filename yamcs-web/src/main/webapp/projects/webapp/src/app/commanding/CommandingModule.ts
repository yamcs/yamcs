import { NgModule } from '@angular/core';
import { RouterModule, Routes, UrlMatcher, UrlSegment } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { mayControlCommandQueueGuardFn } from '../core/guards/MayControlCommandQueueGuard';
import { SharedModule } from '../shared/SharedModule';
import { InstancePage } from '../shared/template/InstancePage';
import { ActionLogTab as ClearanceActionLogTab } from './clearances/ActionLogTab';
import { ChangeLevelDialog } from './clearances/ChangeLevelDialog';
import { clearancesEnabledGuardFn } from './clearances/ClearancesEnabledGuard';
import { ClearancesPage } from './clearances/ClearancesPage';
import { ClearancesPageTabs } from './clearances/ClearancesPageTabs';
import { AcknowledgmentIcon } from './command-history/AcknowledgmentIcon';
import { CascadingPrefixPipe } from './command-history/CascadingPrefixPipe';
import { CommandArguments } from './command-history/CommandArguments';
import { CommandDetail } from './command-history/CommandDetail';
import { CommandHistoryPage } from './command-history/CommandHistoryPage';
import { CommandHistoryPrintable } from './command-history/CommandHistoryPrintable';
import { CommandPage } from './command-history/CommandPage';
import { ExtraAcknowledgmentsTable } from './command-history/ExtraAcknowledgmentsTable';
import { TransmissionConstraintsIcon } from './command-history/TransmissionConstraintsIcon';
import { YamcsAcknowledgmentsTable } from './command-history/YamcsAcknowledgmentsTable';
import { CommandForm } from './command-sender/CommandForm';
import { CommandReportPage } from './command-sender/CommandReportPage';
import { ConfigureCommandPage } from './command-sender/ConfigureCommandPage';
import { ScheduleCommandDialog } from './command-sender/ScheduleCommandDialog';
import { SelectEnumerationDialog } from './command-sender/SelectEnumerationDialog';
import { SendCommandPage } from './command-sender/SendCommandPage';
import { SendCommandWizardStep } from './command-sender/SendCommandWizardStep';
import { AggregateArgument } from './command-sender/arguments/aggregate/AggregateArgument';
import { ArgumentComponent } from './command-sender/arguments/argument/ArgumentComponent';
import { ArrayArgument } from './command-sender/arguments/array/ArrayArgument';
import { BinaryArgument } from './command-sender/arguments/binary/BinaryArgument';
import { BooleanArgument } from './command-sender/arguments/boolean/BooleanArgument';
import { EnumerationArgument } from './command-sender/arguments/enumeration/EnumerationArgument';
import { FloatArgument } from './command-sender/arguments/float/FloatArgument';
import { IntegerArgument } from './command-sender/arguments/integer/IntegerArgument';
import { StringArgument } from './command-sender/arguments/string/StringArgument';
import { TimeArgument } from './command-sender/arguments/time/TimeArgument';
import { CommandDownloadLinkPipe } from './pipes/CommandDownloadLinkPipe';
import { ActionLogTab as QueueActionLogTab } from './queues/ActionLogTab';
import { QueuedCommandsTab } from './queues/QueuedCommandsTab';
import { QueuedCommandsTable } from './queues/QueuedCommandsTable';
import { QueuesPage } from './queues/QueuesPage';
import { AcknowledgmentNamePipe } from './stacks/AcknowledgmentNamePipe';
import { AdvanceAckHelp } from './stacks/AdvanceAckHelp';
import { CreateFolderDialog } from './stacks/CreateFolderDialog';
import { CreateStackDialog } from './stacks/CreateStackDialog';
import { EditStackEntryDialog } from './stacks/EditStackEntryDialog';
import { RenameStackDialog } from './stacks/RenameStackDialog';
import { ScheduleStackDialog } from './stacks/ScheduleStackDialog';
import { StackFilePage } from './stacks/StackFilePage';
import { StackFilePageDirtyDialog } from './stacks/StackFilePageDirtyDialog';
import { StackFilePageDirtyGuard, stackFilePageDirtyGuardFn } from './stacks/StackFilePageDirtyGuard';
import { StackFolderPage } from './stacks/StackFolderPage';
import { StackPage } from './stacks/StackPage';
import { StackedCommandDetail } from './stacks/StackedCommandDetail';
import { StacksPage } from './stacks/StacksPage';

const commandMatcher: UrlMatcher = url => {
  let consumed = url;

  // Stop consuming at /-/
  // (handled by Angular again)
  const idx = url.findIndex(segment => segment.path === '-');
  if (idx !== -1) {
    consumed = url.slice(0, idx);
  }

  const command = '/' + consumed.map(segment => segment.path).join('/');
  return {
    consumed,
    posParams: {
      'command': new UrlSegment(command, {}),
    },
  };
};

const routes: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePage,
  children: [{
    path: 'clearances',
    canActivate: [clearancesEnabledGuardFn],
    children: [{
      path: '',
      pathMatch: 'full',
      component: ClearancesPage,
    }, {
      path: 'log',
      component: ClearanceActionLogTab,
    }]
  }, {
    path: 'send',
    children: [{
      path: '',
      pathMatch: 'full',
      component: SendCommandPage,
    }, {
      matcher: commandMatcher,
      children: [{
        path: '',
        pathMatch: 'full',
        component: ConfigureCommandPage,
      }, {
        path: '-/report/:commandId',
        component: CommandReportPage,
      }],
    }]
  }, {
    path: 'history',
    children: [{
      path: '',
      pathMatch: 'full',
      component: CommandHistoryPage,
    }, {
      path: ':commandId',
      component: CommandPage,
    }]
  }, {
    path: 'queues',
    component: QueuesPage,
    canActivate: [mayControlCommandQueueGuardFn],
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: 'pending',
    }, {
      path: 'pending',
      component: QueuedCommandsTab,
    }, {
      path: 'log',
      component: QueueActionLogTab,
    }]
  }, {
    path: 'stacks',
    pathMatch: 'full',
    redirectTo: 'stacks/browse',
  }, {
    path: 'stacks/browse',
    component: StacksPage,
    children: [{
      path: '**',
      component: StackFolderPage,
    }]
  }, {
    path: 'stacks/files',
    component: StackPage,
    children: [{
      path: '**',
      component: StackFilePage,
      canDeactivate: [stackFilePageDirtyGuardFn],
    }]
  }]
}];

@NgModule({
  imports: [
    SharedModule,
    RouterModule.forChild(routes),
  ],
  providers: [
    StackFilePageDirtyGuard,
  ],
  declarations: [
    AcknowledgmentIcon,
    AcknowledgmentNamePipe,
    AdvanceAckHelp,
    AggregateArgument,
    ArgumentComponent,
    ArrayArgument,
    BinaryArgument,
    BooleanArgument,
    CascadingPrefixPipe,
    ChangeLevelDialog,
    ClearanceActionLogTab,
    ClearancesPage,
    ClearancesPageTabs,
    CommandArguments,
    CommandDetail,
    CommandDownloadLinkPipe,
    CommandForm,
    CommandHistoryPage,
    CommandHistoryPrintable,
    CommandPage,
    CommandReportPage,
    ConfigureCommandPage,
    CreateFolderDialog,
    CreateStackDialog,
    EditStackEntryDialog,
    EnumerationArgument,
    ExtraAcknowledgmentsTable,
    FloatArgument,
    IntegerArgument,
    QueueActionLogTab,
    QueuedCommandsTab,
    QueuedCommandsTable,
    QueuesPage,
    RenameStackDialog,
    ScheduleCommandDialog,
    ScheduleStackDialog,
    SelectEnumerationDialog,
    SendCommandPage,
    SendCommandWizardStep,
    StackedCommandDetail,
    StackFilePage,
    StackFilePageDirtyDialog,
    StackFolderPage,
    StackPage,
    StacksPage,
    StringArgument,
    TimeArgument,
    TransmissionConstraintsIcon,
    YamcsAcknowledgmentsTable,
  ],
})
export class CommandingModule {
}
