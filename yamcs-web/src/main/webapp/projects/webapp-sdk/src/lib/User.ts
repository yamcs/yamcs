import { ObjectPrivilege, UserInfo } from './client';

export class User {

  constructor(private userInfo: UserInfo) {
  }

  getName() {
    return this.userInfo.name;
  }

  getEmail() {
    return this.userInfo.email;
  }

  getDisplayName() {
    return this.userInfo.displayName;
  }

  isSuperuser() {
    return this.userInfo.superuser || false;
  }

  getGroups() {
    return this.userInfo.groups || [];
  }

  getRoles() {
    return this.userInfo.roles || [];
  }

  getSystemPrivileges() {
    return this.userInfo.systemPrivileges || [];
  }

  getObjectPrivileges(): ObjectPrivilege[] {
    return this.userInfo.objectPrivileges || [];
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
        for (const expression of p.objects) {
          if (object.match(expression)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  hasAnyObjectPrivilegeOfType(type: string) {
    if (this.userInfo.superuser) {
      return true;
    }
    for (const p of this.getObjectPrivileges()) {
      if (p.type === type) {
        return true;
      }
    }
    return false;
  }

  getClearance() {
    return this.userInfo.clearance;
  }
}
