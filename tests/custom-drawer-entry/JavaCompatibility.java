package com.paopaoime.company;
import com.paopaoime.aikeyboard.sdk.core.drawer.OemCustomDrawerSpec;
import com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerContentFactory;
public final class JavaCompatibility {
    public static OemCustomDrawerSpec create(OemDrawerContentFactory factory) {
        return new OemCustomDrawerSpec("java_old", "Old Java constructor", 0, null, factory);
    }
}
