# <img src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="48"> MP Manager

A free dual pane, Material Design file manager for Android with focus on APKs and the goal to be an open source alternative to MT Manager

<img src="./images/Ss1.png" width="200"> <img src="./images/Ss2.png" width="200">

## Features

### File Manager

<details><summary>Dual pane navigation</summary>

Browse two folders side by side. This makes it easy to move or copy files from one pane to the other.

A separate home folder can be set for each pane.

There are back and forward buttons, button to sync both panes to the same folder, new file/folder button, parent folder button.

<!-- TODO: Add video
![Dual pane navigation](./images/navigation.mp4)
-->
</details>

<details><summary>Bookmarks and history</summary>
<img src="./images/bookmarks.png" align="right" width="200">

Add any folder to bookmarks and manage them from a bottom drawer. The drawer has tabs for bookmarks and navigation history and opens by swiping up on the bottom bar.

Bookmarks can be deleted by long pressing on one.
</details>

<details><summary>Filtering, sorting and hidden files</summary>
<img src="./images/filter.png" align="right" width="200"> Filter on the right pane
<img src="./images/sort.png" align="right" width="200"> Sorting

<img src="./images/hidefiles.png" align="right" width="200"> File hiding

Filter the current folder as you type. Sort by name, size, date or type, reverse order, and choose whether a sort applies only to the current folder or everywhere. You can hide files from the list, show or hide system hidden files such as dot folders, and edit the list of manually hidden files later.
</details>

<details><summary>Advanced search</summary>
<img src="./images/search.png" align="right" width="200">

Search the current folder by file name and optionally recurse into subfolders. Advanced options include match case, regular expressions, searching for text inside file contents, and minimum or maximum file size. Recent searches are saved for quick reuse.
</details>

<details><summary>File operations</summary>

Create files and folders, rename, copy, move and delete with progress reporting.

Extract, add files in ZIP, APK, auto sign option in APK

Rename several files at once using templates with prefix, suffix, numbering and find/replace.

<!-- TODO: Add screenshots/videos
![Multi rename dialog](./images/multi-rename.jpg)
![Compress dialog](./images/compress.jpg)
-->
</details>

<details><summary>File properties and sharing</summary>

View type, size and last modified date, and copy any value to the clipboard with a long press. Share files or open them with another app.

<!-- TODO: Add screenshots/videos -->
</details>

### Media

<details><summary>Built-in audio and video player</summary>

Play audio and video files without leaving the app. A mini player dialog with artwork, seek bar and playback controls can play in the background or expand into a full player.

<!-- TODO: Add screenshots/videos
![Mini player](./images/mini-player.jpg)
![Full player](./images/full-player.mp4)
-->
</details>

<details><summary>Image viewer</summary>

Open images with swipe between pictures in directory. EXIF metadata is shown for supported files, images can be deleted or shared from the viewer.

<!-- TODO: Add screenshots/videos
![Image viewer](./images/image-viewer.jpg)
-->
</details>

### APK Tools

<details><summary>APK information and install</summary>
<img src="./images/apkdialog.png" align="right" width="200">

Tap an APK to see its icon, name, version code and name, package name, signature schemes used (V1, V2, V3, V4) and whether it is protected. You can view files inside the APK and more features outlined below.

Installing both regular and split APKS is supported.

<!-- TODO: Add screenshots/videos
![APK info dialog](./images/apk-info.jpg)
-->
</details>

<details><summary>Sign APK and split APKs</summary>

Sign APKs and split APKs with your own or default (Debug) key. Signing supports JKS and PKCS12 keystores as well as PK8/PEM keys, and new keys can be generated inside the app.

Automatic signing after modifying an APK can be toggled and configured.

Biometrics can be used as alternative to entering password every time.
<!-- TODO: Add screenshots/videos
![Sign settings](./images/sign-settings.jpg)
-->
</details>

<details><summary>Decompile, build and protect</summary>
<img src="./images/decomp.png" align="right" width="200"> Decompiling

All functions from [REAndroid APKEditor](https://github.com/REAndroid/APKEditor) are available: Decompile an APK, Build an APK from a decompiled folder, merge (AntiSplit), Refactor obfuscated resource names and Protect.

<!-- TODO: Add screenshots/videos
![Decompile options](./images/decompile.jpg)
![Build options](./images/build.jpg)
-->
</details>

<details><summary>Quick edit APK attributes</summary>
<img src="./images/quick-edit.png" align="right" width="200">

Change the launcher icon, app name, install location, version code and name, min SDK and target SDK quickly in a dialog. Every activity and property in the manifest can also be edited from a tree view, including disabling entries.
</details>

<details><summary>APK optimization and cloning</summary>
<img src="./images/clone.png" align="right" width="200"> Clone

Optimize APKs by removing chosen files, with a default list of common tracker and metadata files that can be edited. You can [clone an APK](https://github.com/developer-krushna/ApkCloner) with a new package name.

</details>

<details><summary>Dex editing</summary>
<img src="./images/multidex.png" align="right" width="200"> Dex selection
<img src="./images/dexe.png" align="right" width="200"> Dex Editor

Edit dex files with [DEX Editor Pro](https://github.com/developer-krushna/Dex-Editor-Android) by developer-krushna. When editing a dex file inside an APK you can choose which dex files to load. Saving asks whether to add the modified file back into the APK and sign it, and a .bak backup is created upon modifying an APK.
</details>

### Editing and Comparing

<details><summary>Text editor</summary>
<img src="./images/axml.png" align="right" width="200">

A full text editor based on [Sora Editor](https://github.com/Rosemoe/sora-editor) with a customizable bottom bar, regex find and replace, and many editor features.

Binary Android XML (AXML) files can be decoded for editing and re-encoded on save automatically.

</details>

<details><summary>Compare tools</summary>
<img src="./images/compared.png" align="right" width="200">

<img src="./images/diff.png" align="right" width="200">

Compare two text files, two ZIP/APK files, or two resources.arsc files. Select one item in each pane and the matching compare option appears in the file menu.
</details>

### APK Extractor

<details><summary>Extract and share APK parts</summary>

Extract APKs in batch and pull out specific parts: the app icon, resources.arsc, classes.dex, AndroidManifest.xml, base.apk, splits and native libs, as well as the launch activity. Split APKs can be merged into a single APK before extracting, and anything can be shared directly.
</details>

### FTP

<details><summary>FTP server</summary>
<img src="./images/ftps.png" align="right" width="200">

Use FTP server with custom port, username and password. The server keeps a notification while running so it can be stopped easily. Connection settings can be saved as profiles, and the device IP can be copied or shared.

</details>

<details><summary>FTP client</summary>
<img src="./images/ftpc.png" align="right" width="200">

Connect to an FTP server and browse remote folders in either pane, with the same navigation controls as local files. Files can be uploaded from the device, and connection details can be saved as profiles (to connect to multiple devices easily).

</details>

### Utilities

<details><summary>Screen color picker</summary>
<img src="./images/colorpicker.png" align="right" width="200">

[Use a floating overlay to find out colors anywhere on the screen.](https://github.com/codehasan/ScreenColorPicker)
</details>

<details><summary>Layout inspector</summary>

[Inspect the view hierarchy of any app through a floating overlay window.](https://github.com/AbdurazaaqMohammed/Layout-Inspector)

</details>

<details><summary>Command Helper</summary>
<img src="./images/cmdhp.png" align="right" width="200"> Profile Creation
<img src="./images/cmdh.png" align="right" width="200"> Generated Command

Command Helper is a simple but powerful tool. It allows you to create templates for commands that can then be quickly applied to any file you select.

It can generate commands for several files at once, preview them, copy or run them directly in Termux.

* In this way you can quickly run command line tools like dex2c etc. using MP Manager

</details>

<details><summary>Appearance and storage info</summary>
<img src="./images/sidebar.png" align="right" width="200"> Generated Command

Choose between system, light, dark and black theme all with Material theme and Dynamic Colors. The sidebar shows mounted storages with used and free space available.
</details>

# Todo

This app still has lots of work to do and probably many bugs to fix but you can try it

* Fix when decode AXML with resources.arsc to get full resource references re encoding is breaking
* Add patcher to support multiple patch formats like APK Editor and Lucky Patcher
* Add root and Shizuku file management
* Add APK optimize improvements