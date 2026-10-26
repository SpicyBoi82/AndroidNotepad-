# Notepad 95 for Android

An Android Java app styled after Windows 95 Notepad: navy title bar, grey classic menus, white plain-text editing area, and a line/column status bar.

## Plain system text edition (v1.1)
- The editor, menu labels, window title and status bar all use Android's built-in monospaced system font, normal weight.
- No rich-text styling or special font files. The title controls and menu checkmarks use simple ASCII.
- Text editing preserves plain UTF-8 content with no typography transformations added by the app.

## Features
- New, Open, Save and Save As via Android's document picker (Storage Access Framework).
- UTF-8 plain text with support for a UTF-8 BOM on opening.
- Cut, Copy, Paste, Delete, Select All.
- Find / Find Next, Word Wrap toggle.
- Restores the last editing draft across app restarts.
- Physical keyboard shortcuts: Ctrl+N, Ctrl+O, Ctrl+S and Ctrl+F.
- No network permission, no ads, no third-party runtime libraries.

## Build
1. Unzip the project and open the **Win95Notepad** folder in Android Studio.
2. Let Android Studio install the Android SDK 35 / Gradle plugin dependencies if prompted.
3. Select **Build > Build APK(s)**.
4. The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.
5. Copy it to the phone and install it (approve installation from your file manager if requested).

Gradle plugin 8.7.3, Java 17, compile/target SDK 35, minimum Android 6.0 (API 23).

## Notes
- This is a plain-text editor, not a byte-perfect replica of Notepad or a rich-text editor.
- The classic window control symbols in the title bar are decorative, since Android manages actual windows.
- Undo uses Android's available keyboard/editor undo behavior; a dedicated in-app undo stack is not included in this first version.
- Android's secure system file picker is intentionally used for access to local and cloud text files.
- Source project provided uncompiled: no Android SDK was available in the creation environment to test or package an APK.

## Building an APK using GitHub Actions (no local Android SDK needed)
1. Create a new GitHub repository and upload the **contents** of the Win95Notepad folder to its root (including the hidden `.github` directory).
2. Open the repository's **Actions** tab and choose **Build Android APK**.
3. Click **Run workflow**. After it completes, download **Notepad95-Android-debug-APK** from the run's Artifacts section.
4. Unzip the downloaded artifact to obtain `app-debug.apk` and install it on Android.

This is a debug-signed APK for personal testing. It is not a Play Store release build. The code has not been compiled or tested in the source-generation environment.
