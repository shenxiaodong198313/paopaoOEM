package com.paopaoime.company;
public class InputCheckActivity extends android.app.Activity {
    public android.widget.EditText editor;
    public android.widget.EditText getEditor() { return editor; }
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32,100,32,32);
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText("OEM 离线语音验证（仅本地输入）"); layout.addView(label);
        editor = new android.widget.EditText(this); editor.setHint("测试输入框"); editor.setMinLines(4);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        layout.addView(editor,new android.widget.LinearLayout.LayoutParams(-1,400));setContentView(layout);
        editor.requestFocus();getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
}
