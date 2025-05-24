import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {
  BaseComponent,
  Notification,
  Preferences,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { AgoPipe } from '../../shared/pipes/ago.pipe';

const MAX_NOTIFICATIONS = 10;
const PREF_KEY = 'notifications';

interface NotificationState {
  runtimeId: string;
  seq: number;
  lastUpdated: number;
}

@Component({
  selector: 'app-notification-widget',
  templateUrl: './notification-widget.component.html',
  styleUrl: './notification-widget.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AgoPipe, WebappSdkModule],
})
export class NotificationWidgetComponent
  extends BaseComponent
  implements OnInit
{
  private destroyRef = inject(DestroyRef);
  private prefs = inject(Preferences);

  private runtimeId?: string;
  notifications = signal<Notification[]>([]);
  unread = computed(() => {
    const notifications = this.notifications();
    const isOpen = this.isOpen();

    if (isOpen) {
      return 0;
    } else {
      const state = this.getLocalState();
      if (this.runtimeId && state?.runtimeId === this.runtimeId) {
        return notifications.filter((x) => x.seq > state.seq).length;
      } else {
        return notifications.length;
      }
    }
  });

  isOpen = signal(false);

  constructor() {
    super();

    // If the notification panel is open while receiving updated
    // notifications, the amount of unread notifications should
    // not increase.
    effect(() => {
      const notifications = this.notifications();
      const isOpen = this.isOpen();
      if (isOpen) {
        let maxSeq = -1;
        for (const notification of notifications) {
          maxSeq = Math.max(maxSeq, notification.seq);
        }
        if (maxSeq >= 0) {
          this.rememberLastRead(maxSeq);
        }
      }
    });
  }

  ngOnInit(): void {
    this.yamcs.yamcsClient
      .getNotifications()
      .then((response) => {
        this.runtimeId = response.runtimeId;
        this.notifications.set(response.notifications ?? []);
        const subscription =
          this.yamcs.yamcsClient.createNotificationSubscription(
            {},
            (notification) => this.onNotification(notification),
          );
        this.destroyRef.onDestroy(() => subscription.cancel());
      })
      .catch((err) => this.messageService.showError(err));
  }

  /**
   * If the notification's tag matches an already known tag, than
   * we update the prior notification with the newest state.
   */
  private onNotification(notification: Notification) {
    let notifications = [...this.notifications()];
    let newTag = true;
    for (let i = 0; i < notifications.length; i++) {
      if (notifications[i].tag === notification.tag) {
        notifications[i] = notification;
        newTag = false;
        break;
      }
    }
    if (newTag) {
      notifications = [notification, ...notifications].slice(
        0,
        MAX_NOTIFICATIONS,
      );
    }

    this.notifications.set(notifications);
  }

  openPanel() {
    this.isOpen.set(true);

    const notifications = this.notifications();
    if (notifications.length) {
      this.rememberLastRead(notifications[0].seq);

      // Update unread counter
      this.notifications.set([...this.notifications()]);
    }
  }

  private rememberLastRead(seq: number) {
    if (this.runtimeId) {
      const state: NotificationState = {
        runtimeId: this.runtimeId,
        seq,
        lastUpdated: Date.now(),
      };
      this.prefs.setObject(PREF_KEY, state);
    }
  }

  goToUrl(notification: Notification) {
    if (notification.url) {
      // Over '/', to enforce component reinitalization if already on that URL
      this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
        this.router.navigateByUrl(notification.url!);
      });
    }
  }

  private getLocalState() {
    return this.prefs.getObject<NotificationState>(PREF_KEY);
  }

  closePanel() {
    this.isOpen.set(false);
  }
}
