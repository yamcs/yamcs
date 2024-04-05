import { Pipe, PipeTransform } from '@angular/core';
import { YamcsService } from '@yamcs/webapp-sdk';

@Pipe({
  standalone: true,
  name: 'ago',
})
export class AgoPipe implements PipeTransform {

  constructor(private yamcs: YamcsService) {
  }

  transform(value: string, useMissionTime = true): string | null {
    if (!value) {
      return value;
    }
    const date = new Date(value);
    const relto = useMissionTime ? this.yamcs.getMissionTime() : new Date();
    if (relto.getTime() > date.getTime()) {
      return this.timeSince(new Date(value), relto);
    } else if (relto.getTime() === date.getTime()) {
      return 'now';
    } else {
      return this.timeFromNow(new Date(value), relto);
    }
  }

  private timeSince(date: Date, relto: Date) {
    const seconds = Math.floor((relto.getTime() - date.getTime()) / 1000);
    let interval = Math.floor(seconds / 31536000);

    if (interval > 1) {
      return interval + ' years ago';
    }
    interval = Math.floor(seconds / 2592000);
    if (interval > 1) {
      return interval + ' months ago';
    }
    interval = Math.floor(seconds / 86400);
    if (interval > 1) {
      return interval + ' days ago';
    }
    interval = Math.floor(seconds / 3600);
    if (interval > 1) {
      return interval + ' hours ago';
    }
    interval = Math.floor(seconds / 60);
    if (interval > 1) {
      return interval + ' minutes ago';
    }

    interval = Math.floor(seconds);
    if (interval > 60) {
      return 'about a minute ago';
    } else {
      return 'less than a minute ago';
    }
  }

  private timeFromNow(date: Date, relto: Date) {
    const seconds = Math.floor((date.getTime() - relto.getTime()) / 1000);
    let interval = Math.floor(seconds / 31536000);

    if (interval > 1) {
      return interval + ' years';
    }
    interval = Math.floor(seconds / 2592000);
    if (interval > 1) {
      return interval + ' months';
    }
    interval = Math.floor(seconds / 86400);
    if (interval > 1) {
      return interval + ' days';
    }
    interval = Math.floor(seconds / 3600);
    if (interval > 1) {
      return interval + ' hours';
    }
    interval = Math.floor(seconds / 60);
    if (interval > 1) {
      return interval + ' minutes';
    }

    interval = Math.floor(seconds);
    if (interval > 60) {
      return 'about a minute';
    } else {
      return 'less than a minute';
    }
  }
}
