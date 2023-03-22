import { Pipe, PipeTransform } from '@angular/core';
import { User } from '../User';

@Pipe({ name: 'mayAccessAdminArea' })
export class MayAccessAdminAreaPipe implements PipeTransform {

  transform(user: User): boolean {
    if (!user) {
      return false;
    }
    return user.hasSystemPrivilege('web.AccessAdminArea');
  }
}
