export class MessageDialog {

  openInformation(shell: any, title: string, message: string) {
    alert(message);
  }

  openConfirm(shell: any, title: string, message: string) {
    return confirm(message);
  }

  openError(shell: any, title: string, message: string) {
    alert(message);
  }
}
