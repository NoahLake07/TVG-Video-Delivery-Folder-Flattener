# Video Folder Flattener

**Version:** 1.0  
**Developed for:** The Video Goat LLC (now operating as Goat Legacy Media LLC)

**Download JAR:** [VideoFolderFlattener-1.0.jar](https://example.com/downloads/VideoFolderFlattener-1.0.jar)  
*(replace this link with the actual hosted JAR location)*

---

## Overview

During digitization and client project prep, some incoming drives and archives contain nested subfolders of video files only (for example, camera exports, Final Cut “Original Media” folders, or client-provided dumps). These nested structures slow down technician processing and complicate project intake.

The **Video Folder Flattener** solves this by:

1. Scanning a root directory for subfolders that contain only video files.
2. Previewing candidate folders in a checklist table, so technicians can uncheck false matches.
3. Moving video files up to the root directory.
4. Removing the now-empty subfolders, leaving a clean, flattened directory of videos ready for processing.

This tool ensures faster project ingest and standardized media structures across Goat Legacy Media workflows.

---

## When to Use

Technicians should run this tool in the following situations:

- After importing media from external drives, SD cards, or client-provided folders that have unnecessary folder nesting.
- When flattening a client’s raw video delivery before reorganization in NAS or Dropbox.
- During digitization cleanup, where subfolders (e.g., `Tape001/`, `Tape002/`) only contain single video exports.

**Do not use this tool** on project folders that contain edits, audio, metadata, or mixed media. It is intended only for flattening video-only subfolders.

---

## How to Use

1. **Launch the JAR**

   ```bash
   java -jar VideoFolderFlattener-1.0.jar
   ```

2. **Choose a Root Folder**
    - Use the file chooser to select the main directory you want to flatten.
    - The program will scan only the immediate subfolders of that directory.

3. **Review Matches**
    - The tool lists subfolders that contain only video files.
    - Each candidate folder shows:
        - Folder path
        - Number of video files
        - Total size
    - By default, all matches are checked. Uncheck any folder you do not want flattened.

4. **Move and Clean**
    - Click **Move Selected**.
    - All video files are relocated into the root folder.
    - Empty subfolders are automatically deleted.
    - If duplicate filenames exist, the program safely renames them (for example, `clip.mp4` → `clip (1).mp4`).

5. **Check the Log**
    - A log window at the bottom shows moved files and deleted folders.
    - Errors (such as permission issues or hidden files) are displayed.

---

## Example Workflow

**Before running:**
```
/Client_Drive/
  /Camera1/
    A001.mp4
    A002.mp4
  /Camera2/
    B001.mp4
    B002.mp4
```

**After running:**
```
/Client_Drive/
  A001.mp4
  A002.mp4
  B001.mp4
  B002.mp4
```

Subfolders are removed, leaving a clean video collection.

---

## Requirements

- Java 11 or higher installed.
- Read/write access to the target directory.

---

## Notes for Technicians

- This tool is part of Goat Legacy Media’s Media Technician Toolkit.
- Use it only on staging or ingest directories. Do not run inside client project folders unless explicitly instructed.
- Files are moved (not copied). Always work on a backup or staging copy if unsure.

---

## Changelog

**v1.0 (Initial Release)**
- Detects video-only subfolders.
- Checkbox preview and selection.
- Moves files safely with auto-renaming.
- Deletes empty folders after flattening.
- Provides detailed log of operations.

---

## License

This software is proprietary to **Goat Legacy Media LLC**.

- Use is restricted to employees, contractors, and authorized partners of Goat Legacy Media.
- Redistribution outside of the company is prohibited without written authorization.
- This tool is provided “as-is,” without warranty of any kind.
- It is intended strictly for internal operational use as part of the Media Technician Toolkit.  
