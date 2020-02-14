import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'ago' })
export class AgoPipe implements PipeTransform {

  transform(value: string): string | null {
    if (!value) {
      return value;
    }
    return this.timeSince(new Date(value));
  }

  private timeSince(date: Date) {
    const seconds = Math.floor((new Date().getTime() - date.getTime()) / 1000);
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
