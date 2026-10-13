package com.paopaoime.clipboardregression;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.accessibility.*;
import android.view.inputmethod.InputMethodManager;
import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the selected real IME from an unrelated, network-free host app. */
public class ClipboardInstrumentation extends Instrumentation {
    private CopyActivity host;
    private final List<String> passed = new ArrayList<>();
    private String label;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        label = args.getString("label", "keyboard");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(info);
            host = (CopyActivity) startActivitySync(new Intent(getTargetContext(), CopyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            show();
            String original = keyboard();
            check(!original.isEmpty(), "No accessible keyboard content");
            shot("before");
            copy("CLIPBOARD_REGRESSION_VISIBLE", false);
            unchanged(original, "copy while keyboard visible");
            copy("CLIPBOARD_REGRESSION_REPEATED", false);
            unchanged(original, "repeated copy");
            hide();
            copy("CLIPBOARD_REGRESSION_BEFORE_SHOW", false);
            show();
            unchanged(original, "copy before showing keyboard");
            copy("CLIPBOARD_REGRESSION_SENSITIVE", true);
            unchanged(original, "sensitive copy");
            hide(); show();
            unchanged(original, "hide and reopen with fresh clipboard");
            shot("after");
            main(() -> { host.editor.onTextContextMenuItem(android.R.id.paste); return null; });
            check(main(() -> host.editor.getText().toString()).equals("CLIPBOARD_REGRESSION_SENSITIVE"), "Manual paste changed or lost copied text");
            passed.add("manual host paste preserved");
            write("result.txt", "PASS " + passed);
            result.putString("stream", "\nPASS " + passed + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
            try { write("result.txt", "FAIL after " + passed + "\n" + trace); shot("failure"); } catch (Exception ignored) { }
            result.putString("stream", "\nFAIL after " + passed + "\n" + trace);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private <T> T main(Callable<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMainSync(() -> { try { value.set(action.call()); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new RuntimeException(error.get());
        return value.get();
    }
    private void show() throws Exception {
        main(() -> { host.editor.requestFocus(); ((InputMethodManager)host.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(host.editor, InputMethodManager.SHOW_IMPLICIT); return null; });
        for (int i=0; i<60; i++) { if (root()!=null) { Thread.sleep(1000); return; } Thread.sleep(200); }
        throw new AssertionError("Keyboard did not open");
    }
    private void hide() throws Exception {
        main(() -> { ((InputMethodManager)host.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(host.editor.getWindowToken(), 0); return null; });
        for (int i=0; i<50; i++) { if (root()==null) return; Thread.sleep(100); }
        throw new AssertionError("Keyboard did not hide");
    }
    private void copy(String text, boolean sensitive) throws Exception {
        main(() -> {
            ClipData clip = ClipData.newPlainText("regression", text);
            if (sensitive) { PersistableBundle extras=new PersistableBundle(); extras.putBoolean("android.content.extra.IS_SENSITIVE", true); clip.getDescription().setExtras(extras); }
            ((ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(clip);
            return null;
        });
        Thread.sleep(1200);
    }
    private AccessibilityNodeInfo root() {
        for (AccessibilityWindowInfo w:getUiAutomation().getWindows())
            if (w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD) return w.getRoot();
        return null;
    }
    private void collect(AccessibilityNodeInfo node, StringBuilder text) {
        if(node==null) return;
        if(node.isVisibleToUser()) {
            if(node.getText()!=null) text.append("text:").append(node.getText()).append('\n');
            if(node.getContentDescription()!=null) text.append("description:").append(node.getContentDescription()).append('\n');
        }
        for(int i=0;i<node.getChildCount();i++) collect(node.getChild(i),text);
    }
    private String keyboard() {
        AccessibilityNodeInfo node=root(); check(node!=null,"Keyboard disappeared");
        StringBuilder text=new StringBuilder(); collect(node,text); return text.toString();
    }
    private void unchanged(String before, String scenario) throws Exception {
        String after=keyboard();
        write(scenario.replace(' ','-')+".txt",after);
        check(before.equals(after), scenario+" changed the keyboard:\nBEFORE\n"+before+"AFTER\n"+after);
        check(main(() -> host.editor.getText().length())==0, "Copy modified editor");
        passed.add(scenario);
    }
    private File dir() { File f=new File(getTargetContext().getExternalFilesDir(null), "clipboard-"+label); f.mkdirs(); return f; }
    private void write(String name,String content) throws Exception { try(FileOutputStream out=new FileOutputStream(new File(dir(),name))) { out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } }
    private void shot(String name) throws Exception { try(FileOutputStream out=new FileOutputStream(new File(dir(),name+".png"))) { getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out); } }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
