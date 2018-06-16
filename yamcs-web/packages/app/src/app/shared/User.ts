import { UserInfo } from '@yamcs/client';

export class User {

  constructor(private userInfo: UserInfo) {
  }

  getUsername() {
    return this.userInfo.login;
  }

  isSuperuser() {
    return this.userInfo.superuser || false;
  }

  getClientConnections() {
    return this.userInfo.clientInfo || [];
  }

  getSystemPrivileges() {
    return this.userInfo.systemPrivilege || [];
  }

  getObjectPrivileges() {
    return this.userInfo.objectPrivilege || [];
  }

  hasSystemPrivilege(privilege: string) {
    if (this.userInfo.superuser) {
      return true;
    }
    return this.getSystemPrivileges().indexOf(privilege) !== -1;
  }

  hasObjectPrivilege(type: string, object: string) {
    if (this.userInfo.superuser) {
      return true;
    }
    for (const p of this.getObjectPrivileges()) {
      if (p.type === type) {
        for (const expression of p.object) {
          if (object.match(expression)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
