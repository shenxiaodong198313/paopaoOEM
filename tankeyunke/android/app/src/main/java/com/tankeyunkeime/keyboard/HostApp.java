package com.tankeyunkeime.keyboard;

import android.app.Application;
import com.paopaoime.aikeyboard.sdk.core.OemHostRuntime;
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk;
import com.paopaoime.aikeyboard.sdk.core.OemLicenseSnapshot;
import java.util.Collections;
import org.fcitx.fcitx5.android.FcitxApplication;

public class HostApp extends FcitxApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        OemHostRuntime.INSTANCE.initialize(this, true);
        OemImeSdk.INSTANCE.initializeFromManifest(
                this,
                (context, request) -> OemLicenseSnapshot.Companion.active(null, "active", Collections.emptySet(), null),
                null,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
