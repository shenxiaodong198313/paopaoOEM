# FAST_LLM ColorOS regression

This fixture consumes the Maven delivery repository. It does not depend on SDK source modules and does not send chat messages or invoke a remote AI model. Private capture helpers are invoked by instrumentation to exercise failures and cancellation without the generation/network phase.

Prepare a separate consumer under `OEM/company/capture-regression`:

```powershell
./prepare.ps1 -UseWorkingAars
```

`-UseWorkingAars` replaces only material and automation AARs in the **test copy** with the freshly built Release/R8 artifacts. This is a local regression build, not an SDK publication. Omit the switch to test an exported delivery as-is. Build the two SDK AARs before using the switch.

In the prepared project, run:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.paopaoime.captureregression.test/com.paopaoime.captureregression.CaptureInstrumentation
```

Pass criteria: `PASS` lists all checks and instrumentation finishes with code `-1`. The device stores `files/capture.png` and `files/result.txt` in the test app. The native instrumentation runner requires no additional JUnit download.

The fixture covers normal restoration, exceptions, cancellation during preparation, timeout after retraction, cancellation after moving the panel behind the chat, neon restoration, empty screenshots, host provider exceptions, and real injected swipes, return to the same panel task, and preservation of the chat Activity. It uses a synthetic chat Activity; customer RMX3843 and real WeChat still require their own acceptance run.
