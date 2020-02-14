import { Pipe, PipeTransform } from '@angular/core';
import { User } from '../User';

@Pipe({ name: 'superuser' })
export class SuperuserPipe implements PipeTransform {

  transform(user: User): boolean {
    if (!user) {
      return false;
    }
    return user.isSuperuser();
  }
}
