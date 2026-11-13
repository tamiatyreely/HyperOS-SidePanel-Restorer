# HyperOS SidePanel Restorer — diagnostic build

Target device/build supplied by the user:
- Device: `shennong`
- HyperOS: `OS4.0.0.12.XNBCNXM`
- MIUI UI version: `V816`

## Current status

This is a **diagnostic LSPosed build**, not yet the gesture-restoration release. APK string inspection confirmed that the Security Center APK contains the native VideoToolBox/GameBooster service classes and strings related to the sidebar line/tips. String presence alone does not reveal method signatures, call order, gesture eligibility checks, or the video-toolbox app allowlist.

This build logs the exact declared method signatures and runtime calls for selected native methods in `com.miui.securitycenter`. It does not change touch input, show UI, force a sidebar, or modify any app allowlist. This avoids breaking Game Turbo or unrelated gestures while the actual gate is being identified.

## Build

Open the project in Android Studio with JDK 17. Build the `app` module. Enable the resulting APK in LSPosed and scope it to:
- `com.miui.securitycenter`
- `com.android.systemui`
- `miui.systemui.plugin`

The method inspection currently runs in `com.miui.securitycenter` only.

## Collect logs

Reboot after enabling the module, open a video app that is already listed in Video Toolbox, rotate to landscape, and try the top-left inward swipe several times. Then export the LSPosed log and send entries beginning with `[SidePanelRestorer]`.

The next build can then hook the confirmed native eligibility/gesture path and reuse the native top-left handle and sidebar launcher. Do not treat this diagnostic APK as the final feature module.
