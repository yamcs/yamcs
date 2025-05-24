import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  input,
} from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import {
  AuthService,
  Preferences,
  TimelineView,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AgoComponent } from '../../shared/ago/ago.component';
import { ItemState } from '../ItemState';
import { PREF_DETAIL_ITEMS_DETAIL_WIDTH } from '../preferences';
import { TimelineService } from '../timeline.service';
import { ItemDetailComponent } from './item-detail.component';

@Component({
  selector: 'app-items-tab',
  templateUrl: 'items-tab.component.html',
  styleUrl: 'items-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AgoComponent, ItemDetailComponent, WebappSdkModule],
})
export class ItemsTabComponent implements AfterViewInit {
  private authService = inject(AuthService);
  private el = inject(ElementRef);
  private prefs = inject(Preferences);
  private timelineService = inject(TimelineService);

  view = input<TimelineView | null>();

  displayedColumns = [
    'name',
    'progress',
    'type',
    'start',
    'duration',
    'status',
    'actions',
    'more',
  ];
  tableTrackerFn = (index: number, row: ItemState) => row.id;
  dataSource = new MatTableDataSource<ItemState>();

  selectedItem$ = new BehaviorSubject<ItemState | null>(null);

  constructor() {
    effect(() => {
      const items = this.timelineService.items();
      this.dataSource.data = items;

      const detailItem = this.selectedItem$.value;
      if (detailItem) {
        let newDetailItem: ItemState | null = null;
        for (const item of items) {
          if (item.id === detailItem.id) {
            newDetailItem = item;
            break;
          }
        }
        this.selectedItem$.next(newDetailItem);
      }
    });
  }

  ngAfterViewInit(): void {
    const detailWidth = this.prefs.getNumber(
      PREF_DETAIL_ITEMS_DETAIL_WIDTH,
      500,
    );
    this.el.nativeElement.style.setProperty(
      '--y-timeline-items-detail-width',
      `${detailWidth}px`,
    );
  }

  openEditItemDialog(itemState: ItemState) {
    this.timelineService.openEditItemDialog(itemState.item);
  }

  // Abort item == cancel latest activity run
  openAbortItemDialog(itemState: ItemState) {
    const activity = itemState.lastRun;
    if (activity) {
      const type = itemState.type?.toLocaleLowerCase();
      if (confirm(`Are you sure you want to abort this ${type}?`)) {
        this.timelineService.cancelActivityRun(activity.id);
      }
    }
  }

  openCancelItemDialog(itemState: ItemState) {
    const type = itemState.type?.toLocaleLowerCase();
    if (confirm(`Are you sure you want to cancel this ${type}?`)) {
      this.timelineService.cancelItem(itemState);
    }
  }

  openDeleteItemDialog(itemState: ItemState) {
    const type = itemState.type?.toLocaleLowerCase();
    if (confirm(`Are you sure you want to delete this ${type}?`)) {
      this.timelineService.deleteItem(itemState);
    }
  }

  startItem(item: ItemState) {
    this.timelineService.startItem(item);
  }

  setItemSuccessful(item: ItemState) {
    this.timelineService.setItemSuccessful(item);
  }

  setItemFailed(item: ItemState) {
    this.timelineService.setItemFailed(item);
  }

  toggleRow(itemState: ItemState) {
    if (this.selectedItem$.value === itemState) {
      this.selectedItem$.next(null);
    } else {
      this.selectedItem$.next(itemState);
    }
  }

  onResizerMouseDown(mouseEvent: MouseEvent) {
    mouseEvent.preventDefault();

    const minWidth = 28;
    const maxWidth = window.innerWidth * 0.75;

    const onMouseMove = (e: MouseEvent) => {
      let newWidth = window.innerWidth - e.clientX;
      newWidth = Math.min(maxWidth, Math.max(minWidth, newWidth));

      // Update the CSS variable on the root (or a specific parent element)
      this.el.nativeElement.style.setProperty(
        '--y-timeline-items-detail-width',
        `${newWidth}px`,
      );
      this.prefs.setNumber(PREF_DETAIL_ITEMS_DETAIL_WIDTH, newWidth);
    };

    const onMouseUp = () => {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  }

  mayStartTimelineActivities() {
    return (
      this.authService.getUser()!.hasSystemPrivilege('ControlTimeline') &&
      this.authService.getUser()!.hasSystemPrivilege('ControlActivities')
    );
  }

  mayControlActivities() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlActivities');
  }

  mayControlTimeline() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlTimeline');
  }
}
