# Optimize and Update App Logo

This plan outlines the steps to replace the current app logo (`q_logo.png`) with the new optimized version provided by the user. The goal is to move from a raster PNG to a Vector Drawable (XML) to improve scaling, reduce app size, and support modern Android adaptive icon features.

## User Review Required

> [!IMPORTANT]
> I will be converting the provided image into a Vector Drawable. Please verify that the visual representation in the final step matches your expectations. If you have the original SVG file, providing that would ensure 100% accuracy.

## Proposed Changes

### [Resource Assets]

#### [NEW] [ic_launcher_foreground.xml](file:///C:/Users/Praveen%20Kumar/Desktop/Quantum%20PDF%20Viewer/android-app/app/src/main/res/drawable/ic_launcher_foreground.xml)
Create a new Vector Drawable based on the provided logo. This replaces the raster `q_logo.png` as the primary foreground layer.

#### [MODIFY] [ic_launcher.xml](file:///C:/Users/Praveen%20Kumar/Desktop/Quantum%20PDF%20Viewer/android-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)
Update the foreground drawable to point to the new `ic_launcher_foreground` vector.

#### [MODIFY] [ic_launcher_round.xml](file:///C:/Users/Praveen%20Kumar/Desktop/Quantum%20PDF%20Viewer/android-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml)
Update the round icon to use the new vector foreground.

#### [DELETE] [q_logo.png](file:///C:/Users/Praveen%20Kumar/Desktop/Quantum%20PDF%20Viewer/android-app/app/src/main/res/drawable/q_logo.png)
Remove the legacy PNG asset to save space.

## Verification Plan

### Automated Tests
- None applicable for static resource changes.

### Manual Verification
- Use the **Compose Preview** tool (if applicable) or the IDE's drawable preview to verify the `ic_launcher_foreground.xml` looks correct.
- Deploy to a device/emulator to verify the launcher icon appears correctly in the app drawer and on the home screen.
