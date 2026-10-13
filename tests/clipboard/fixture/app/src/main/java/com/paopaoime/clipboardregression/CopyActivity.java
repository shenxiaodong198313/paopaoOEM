package com.paopaoime.clipboardregression;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
public class CopyActivity extends Activity {
 public EditText editor;
 public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(24,80,24,24);
  TextView label=new TextView(this);label.setText("剪贴板工具栏回归 · 仅本地测试");layout.addView(label);
  editor=new EditText(this);editor.setHint("测试输入框");editor.setMinLines(3);layout.addView(editor,new LinearLayout.LayoutParams(-1,220));
  Button copy=new Button(this);copy.setText("复制测试文本");copy.setOnClickListener(v->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("test","复制不改变工具栏")));layout.addView(copy);
  setContentView(layout);editor.requestFocus();
 }
}
