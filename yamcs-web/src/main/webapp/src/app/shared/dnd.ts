export interface FileWithFullPath extends File {
  _fullPath: string;
}

/**
 * Returns a flat list of files. The DataTransfer object may contain
 * both files and directories.
 *
 * The returned files have an extra property '_fullPath' which contains
 * the full path starting from the top selected folder.
 */
export async function listDroppedFiles(dataTransfer: DataTransfer): Promise<FileWithFullPath[]> {
  const droppedFiles: FileWithFullPath[] = [];
  const items = dataTransfer.items;
  if (items && items.length && (items[0] as any).webkitGetAsEntry) {

    // Convert all items to entries
    // (important to do this _before_ recursing on subtrees)
    const entries: FileSystemEntry[] = [];
    for (let i = 0; i < items.length; i++) {
      const entry = items[i].webkitGetAsEntry();
      if (entry) {
        entries.push(entry);
      }
    }

    for (const entry of entries) {
      const moreFiles = await listFilesUnderEntry(entry);
      droppedFiles.push(...moreFiles);
    }
  } else {
    for (let i = 0; i < dataTransfer.files.length; i++) {
      const item = dataTransfer.files.item(i);
      droppedFiles.push(item as FileWithFullPath);
    }
  }
  return droppedFiles;
}

export async function listFilesUnderEntry(entry: FileSystemEntry, path = '') {
  const droppedFiles: FileWithFullPath[] = [];
  const fullPath = path ? `${path}/${entry.name}` : entry.name;
  if (entry.isFile) {
    const fileEntry = entry as FileSystemFileEntry;
    const droppedFile = await getFile(fileEntry) as FileWithFullPath;
    droppedFile._fullPath = fullPath;
    droppedFiles.push(droppedFile);
  } else if (entry.isDirectory) {
    const directoryEntry = entry as FileSystemDirectoryEntry;
    const directoryEntries = [];

    const directoryReader = directoryEntry.createReader();
    let batch = await readEntries(directoryReader);
    while (batch.length) { // Empty array means no more batches (batch is about 100 entries)
      directoryEntries.push(...batch);
      batch = await readEntries(directoryReader);
    }

    for (const directoryEntry of directoryEntries) {
      const moreFiles = await listFilesUnderEntry(directoryEntry, fullPath);
      droppedFiles.push(...moreFiles);
    }
  }
  return droppedFiles;
}

function getFile(entry: FileSystemFileEntry) {
  return new Promise<File>((resolve, reject) => {
    entry.file(file => {
      resolve(file);
    }, err => reject(err));
  });
}

function readEntries(reader: FileSystemDirectoryReader) {
  return new Promise<FileSystemEntry[]>((resolve, reject) => {
    reader.readEntries(results => {
      resolve(results);
    }, err => reject(err));
  });
}
