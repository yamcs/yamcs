/**
 * Returns a flat list of files. The DataTransfer object may contain
 * both files and directories.
 *
 * The returned files have an extra property '_fullPath' which contains
 * the full path starting from the top selected folder.
 */
export async function listDroppedFiles(dataTransfer: any): Promise<any[]> {
  const droppedFiles: any[] = [];
  const items = dataTransfer.items;
  if (items && items.length && (items[0].webkitGetAsEntry || items[0].getAsEntry)) {

    // Convert all items to entries
    // (important to do this _before_ recursing on subtrees)
    const entries = [];
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.webkitGetAsEntry) {
        entries.push(item.webkitGetAsEntry());
      } else {
        entries.push(item.getAsEntry());
      }
    }
    for (const entry of entries) {
      const moreFiles = await listFilesUnderEntry(entry);
      droppedFiles.push(...moreFiles);
    }
  } else {
    for (let i = 0; i < dataTransfer.files.length; i++) {
      const item = dataTransfer.files.item(i);
      droppedFiles.push(item);
    }
  }
  return droppedFiles;
}

export async function listFilesUnderEntry(entry: any, path = ''): Promise<any[]> {
  const droppedFiles: any[] = [];
  const fullPath = path ? `${path}/${entry.name}` : entry.name;
  if (entry.isFile) {
    const droppedFile = await getFile(entry);
    droppedFile._fullPath = fullPath;
    droppedFiles.push(droppedFile);
  } else if (entry.isDirectory) {
    const directoryEntries = await readEntries(entry);
    for (const directoryEntry of directoryEntries) {
      const moreFiles = await listFilesUnderEntry(directoryEntry, fullPath);
      droppedFiles.push(...moreFiles);
    }
  }
  return droppedFiles;
}

function getFile(entry: any): Promise<any> {
  return new Promise<any>((resolve, reject) => {
    entry.file((file: any) => {
      resolve(file);
    }, (err: any) => {
      reject(err);
    });
  });
}

function readEntries(directory: any): Promise<any> {
  return new Promise<any[]>((resolve, reject) => {
    directory.createReader().readEntries((results: any) => {
      resolve(results);
    }, (err: any) => reject(err));
  });
}
