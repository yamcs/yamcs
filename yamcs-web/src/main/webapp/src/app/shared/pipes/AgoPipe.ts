import { Pipe, PipeTransform } from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';

@Pipe({ name: 'ago' })
export class AgoPipe implements PipeTransform {

  constructor(private yamcs: YamcsService) {
  }

  transform(value: string): string | null {
    if (!value) {
      return value;
    }
    return this.timeSince(new Date(value));
  }

  private timeSince(date: Date) {
    const relto = this.yamcs.getMissionTime();
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
}
