package com.paopaoime.clipboardregression;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
public class CopyActivity extends Activity {
 public EditText editor;
 public TextView message;
 public int sendActions;
 public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(24,80,24,24);
  TextView label=new TextView(this);label.setText("临时剪贴板快捷回复 · 本地测试宿主");layout.addView(label);
  editor=new EditText(this);editor.setHint("测试输入框");editor.setMinLines(3);
  editor.setOnEditorActionListener((v, action, event)->{sendActions++;return true;});
  layout.addView(editor,new LinearLayout.LayoutParams(-1,220));
  message=new TextView(this);message.setText("客户复制的测试原文：今天工作有点累。");message.setTextIsSelectable(true);layout.addView(message);
  Button copy=new Button(this);copy.setText("复制测试文本");copy.setOnClickListener(v->copy("客户复制的测试原文：今天工作有点累。"));layout.addView(copy);
  setContentView(layout);editor.requestFocus();apply(getIntent());
 }
 @Override public void onNewIntent(Intent intent) {super.onNewIntent(intent);setIntent(intent);apply(intent);}
 private void apply(Intent intent) {
  new Handler(Looper.getMainLooper()).postDelayed(()->{
   if(intent.getBooleanExtra("clear",false)) editor.setText("");
   if(intent.hasExtra("copy")) copy(intent.getStringExtra("copy"));
   android.util.Log.i("ExternalCopy", "editor="+editor.getText()+";sendActions="+sendActions);
   if(intent.getBooleanExtra("show",false)) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);
  },700);
 }
 private void copy(String text) {((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("quick reply test",text));}
}
