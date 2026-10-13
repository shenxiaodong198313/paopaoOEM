package com.paopaoime.company;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.InsetDrawable;
import android.os.*;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.ViewAnimator;
import org.fcitx.fcitx5.android.data.theme.*;
import org.fcitx.fcitx5.android.input.*;
import org.fcitx.fcitx5.android.input.keyboard.*;
import org.fcitx.fcitx5.android.input.status.KeyboardSelectionPreset;
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge;
import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Java avoids references to Kotlin helper methods optimized out of the R8 host. */
public class SkinChecks {
 private final Instrumentation inst=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
 private Context getTargetContext(){return inst.getTargetContext();}
 private void runOnMainSync(Runnable r){inst.runOnMainSync(r);}
 private android.app.UiAutomation getUiAutomation(){return inst.getUiAutomation();}
 private Activity startActivitySync(Intent i){return inst.startActivitySync(i);}

 private final ArrayList<String> passed=new ArrayList<>();
 private File output(){File f=new File(getTargetContext().getExternalFilesDir(null),"skin-verification");f.mkdirs();return f;}
 private void write(String n,String t)throws Exception{try(FileOutputStream f=new FileOutputStream(new File(output(),n))){f.write(t.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private <T>T main(Callable<T> call)throws Exception{if(Looper.myLooper()==Looper.getMainLooper())return call.call();AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{value.set(call.call());}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new RuntimeException(error.get());return value.get();}
 private List<View> descendants(View v){ArrayList<View> all=new ArrayList<>();all.add(v);if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)all.addAll(descendants(group.getChildAt(i)));}return all;}
 private List<View> views()throws Exception{return main(()->{ArrayList<View> all=new ArrayList<>();for(View v:WindowInspector.getGlobalWindowViews())all.addAll(descendants(v));return all;});}
 private BaseKeyboard board()throws Exception{for(View v:views())if(v instanceof BaseKeyboard&&v.isShown())return(BaseKeyboard)v;throw new IllegalStateException("No keyboard");}
 private void waitFor(String name,Callable<Boolean> condition)throws Exception{for(int i=0;i<150;i++){try{if(condition.call())return;}catch(Exception ignored){}Thread.sleep(100);}throw new AssertionError(name);}
 private FcitxInputMethodService service()throws Exception{Context c=board().getContext();while(c instanceof ContextWrapper&&!(c instanceof FcitxInputMethodService))c=((ContextWrapper)c).getBaseContext();return(FcitxInputMethodService)c;}
 private KeyView key(String id)throws Exception{return main(()->{for(View v:descendants(board()))if(v instanceof KeyView){KeyView k=(KeyView)v;if(k.getSkinKeyId().equals(id)||k.getSkinKeyIds().contains(id))return k;}throw new IllegalStateException("Missing key "+id);});}
 private void tap(View v)throws Exception{float[] p=main(()->{int[] xy=new int[2];v.getLocationOnScreen(xy);return new float[]{xy[0]+v.getWidth()/2f,xy[1]+v.getHeight()/2f};});long now=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,p[0],p[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{check(getUiAutomation().injectInputEvent(e,true),"inject failed");}finally{e.recycle();}Thread.sleep(70);}Thread.sleep(200);}
 private void shot(String name)throws Exception{Thread.sleep(450);try(FileOutputStream f=new FileOutputStream(new File(output(),name+".png"))){getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,f);}}
 private void mode(InputMethodSettingsBridge.DarkModeOption mode)throws Exception{main(()->{InputMethodSettingsBridge.INSTANCE.setDarkModeOption(mode);return null;});}
 private void geometry(String label)throws Exception{main(()->{
  BaseKeyboard b=board();ViewAnimator bar=null;for(View v:views())if(v instanceof ViewAnimator&&v.isShown()&&v.getBackground() instanceof InsetDrawable){bar=(ViewAnimator)v;break;}check(bar!=null,"toolbar surface missing");
  int buttons=0;for(View v:descendants(bar))if(v instanceof org.fcitx.fcitx5.android.input.bar.ui.ToolButton&&v.isShown()){
   android.widget.ImageView icon=((org.fcitx.fcitx5.android.input.bar.ui.ToolButton)v).getImage();int expectedIcon=Math.round(23*b.getContext().getResources().getDisplayMetrics().density);
   check(Math.abs(icon.getWidth()-expectedIcon)<=1&&Math.abs(icon.getHeight()-expectedIcon)<=1&&icon.getPaddingLeft()==0&&icon.getPaddingRight()==0,"toolbar icon bounds "+v.getContentDescription()+" "+icon.getWidth()+"x"+icon.getHeight()+" expected "+expectedIcon+" padding "+icon.getPaddingLeft()+","+icon.getPaddingRight());
   android.graphics.drawable.Drawable bg=v.getBackground();
   // The shared standard allows a transparent toolbar button without a background drawable.
   if(bg!=null){
   Bitmap idle=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);bg.draw(new android.graphics.Canvas(idle));
   for(int y=0;y<idle.getHeight();y++)for(int x=0;x<idle.getWidth();x++)check(android.graphics.Color.alpha(idle.getPixel(x,y))==0,"persistent toolbar background");idle.recycle();}buttons++;
  }check(!label.endsWith("-qwerty")||buttons>=3,"idle toolbar buttons missing: "+label+" count="+buttons);
  int[] p=new int[2],bp=new int[2];b.getLocationOnScreen(p);bar.getLocationOnScreen(bp);android.graphics.Rect bounds=((InsetDrawable)bar.getBackground()).getDrawable().getBounds();int dx=Math.round((SkinLabStandard.INSTANCE.landscape(b.getContext())?10:6)*b.getContext().getResources().getDisplayMetrics().density);
  check(p[0]+dx==bp[0]+bounds.left&&p[0]+b.getWidth()-dx==bp[0]+bounds.right,label+" toolbar alignment");
  StringBuilder rows=new StringBuilder();for(View v:descendants(b))if(v instanceof KeyView){KeyView k=(KeyView)v;check(k.getWidth()>0&&k.getHeight()>0,"empty key");rows.append(k.getSkinKeyIds()).append(' ').append(k.getWidth()).append('x').append(k.getHeight()).append('\n');}
  if(b.getSkinLayoutId().equals("t9")||b.getSkinLayoutId().equals("number")){View surface=null;for(View v:descendants(b))if(v.getParent()==b&&v.getClass()==View.class&&v.getBackground()!=null){surface=v;break;}check(surface!=null,"sidebar surface missing");KeyView first=key(b.getSkinLayoutId()+"/r1c2"),last=key(b.getSkinLayoutId()+"/r3c2");int[] sp=new int[2],fp=new int[2],lp=new int[2];surface.getLocationOnScreen(sp);first.getLocationOnScreen(fp);last.getLocationOnScreen(lp);int dy=Math.round((SkinLabStandard.INSTANCE.landscape(b.getContext())?4f:5f)*b.getContext().getResources().getDisplayMetrics().density);check(sp[1]==fp[1]+dy&&sp[1]+surface.getHeight()==lp[1]+last.getHeight()-dy,"sidebar alignment");}
  write(label+"-geometry.txt",rows.toString());return null;});}
 public void checks()throws Exception{
  BundledSystemThemes themes=BundledSystemThemes.INSTANCE;ThemeManager tm=ThemeManager.INSTANCE;
  check(tm.getDefaultTheme().getName().equals(BundledSystemThemes.LIGHT),"Default is not porcelain");
  check(tm.getActiveTheme().getName().equals(BundledSystemThemes.LIGHT),"Fresh host active theme");passed.add("fresh host active porcelain");
  SharedPreferences p=getTargetContext().getSharedPreferences("oem_skin_migration_checks",0);
  for(String old:new String[]{"MaterialLight","PixelLight","MaterialDark","PixelDark","custom_keep"}){p.edit().clear().putString("normal_mode_theme",old).putBoolean("follow_system_dark_mode",true).commit();themes.migratePreferences(p);String expected=old.endsWith("Light")?BundledSystemThemes.LIGHT:old.endsWith("Dark")?BundledSystemThemes.DARK:old;check(expected.equals(p.getString("normal_mode_theme",null)),"Migration "+old);check(p.getBoolean("follow_system_dark_mode",false),"follow lost");themes.migratePreferences(p);check(expected.equals(p.getString("normal_mode_theme",null)),"Not idempotent");}
  p.edit().clear().putString("light_mode_theme","custom_light").putString("dark_mode_theme","custom_dark").commit();themes.migratePreferences(p);check("custom_light".equals(p.getString("light_mode_theme",null))&&"custom_dark".equals(p.getString("dark_mode_theme",null)),"custom modes lost");
  p.edit().clear().commit();themes.migratePreferences(p);check(BundledSystemThemes.LIGHT.equals(p.getString("normal_mode_theme",null))&&!p.getBoolean("follow_system_dark_mode",true),"fresh defaults");
  p.edit().clear().putString("normal_mode_theme","PixelLight").commit();themes.migratePreferences(p);check(p.getBoolean("follow_system_dark_mode",false),"old implicit follow lost");passed.add("fresh defaults, legacy migration, custom modes, implicit follow, idempotence");
  main(()->{for(boolean dark:new boolean[]{false,true,false}){InputMethodSettingsBridge.INSTANCE.setDarkModeOption(InputMethodSettingsBridge.DarkModeOption.FollowSystem);Configuration c=new Configuration(getTargetContext().getResources().getConfiguration());c.uiMode=(c.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);tm.onSystemPlatteChange(c);check(tm.getActiveTheme().getName().equals(dark?BundledSystemThemes.DARK:BundledSystemThemes.LIGHT),"system mode");}for(Theme.Custom t:themes.getAll()){for(Theme.Custom.CustomBackground a:t.allImageAssets())check(a.toDrawable()!=null&&a.getCroppedFilePath().contains("/user_de/"),"Direct Boot asset");tm.deleteTheme(t.getName());check(tm.getTheme(t.getName())!=null,"Builtin deleted");boolean blocked=false;try{tm.saveTheme(t);}catch(IllegalArgumentException expected){blocked=true;}check(blocked,"Builtin overwritten");}return null;});passed.add("system transitions, Direct Boot assets, immutable builtins");
  InputCheckActivity host=(InputCheckActivity)startActivitySync(new Intent(getTargetContext(),InputCheckActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));tap(host.getEditor());waitFor("Keyboard missing",()->board()!=null);
  for(boolean dark:new boolean[]{false,true}){String label=dark?BundledSystemThemes.DARK:BundledSystemThemes.LIGHT;mode(dark?InputMethodSettingsBridge.DarkModeOption.Dark:InputMethodSettingsBridge.DarkModeOption.Light);Thread.sleep(700);main(()->{service().selectFullKeyboardIme(KeyboardSelectionPreset.ENGLISH);return null;});waitFor("Qwerty missing",()->board() instanceof TextKeyboard);geometry(label+"-qwerty");shot(label+"-qwerty");
   final FcitxInputMethodService ime=service();
   String panelLabel=getTargetContext().getString(org.fcitx.fcitx5.android.R.string.status_area);View panelButton=null;
   for(View v:views())if(v.isShown()&&panelLabel.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())){panelButton=v;break;}
   check(panelButton!=null,"tool panel button missing");tap(panelButton);Thread.sleep(450);shot(label+"-tool-panel");
   boolean voiceLabel=false;int panelLabels=0;for(View v:views())if(v.isShown()&&v instanceof android.widget.TextView){android.widget.TextView text=(android.widget.TextView)v;if("语音输入".contentEquals(text.getText()))voiceLabel=true;if(text.getParent() instanceof androidx.constraintlayout.widget.ConstraintLayout && text.getLayout()!=null){panelLabels++;check(text.getLayout().getLineBottom(text.getLayout().getLineCount()-1)<=text.getHeight()-text.getPaddingTop()-text.getPaddingBottom()+2,"clipped tool panel text: "+text.getText());}}
   check(voiceLabel==org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.INSTANCE.enabled(getTargetContext()),"voice tile presence");check(panelLabels>2,"tool panel labels missing");
   String backLabel=getTargetContext().getString(org.fcitx.fcitx5.android.R.string.back_to_keyboard);View back=null;for(View v:views())if(v.isShown()&&backLabel.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())){back=v;break;}check(back!=null,"panel back button missing");tap(back);waitFor("keyboard restore after panel",()->board()!=null);

   main(()->{host.getEditor().setText("");return null;});tap(key("letter:a"));waitFor("English input failed",()->main(()->host.getEditor().getText().toString().equalsIgnoreCase("a")));
   main(()->{host.getEditor().setText("");service().selectFullKeyboardIme(KeyboardSelectionPreset.PINYIN);return null;});Thread.sleep(600);for(char c:"nihao".toCharArray())tap(key("letter:"+c));shot(label+"-candidates");tap(key("role:space"));waitFor("Chinese input failed",()->main(()->host.getEditor().getText().toString().contains("你好")));
   tap(key("role:number_mode"));waitFor("Number missing",()->board() instanceof NumberKeyboard);geometry(label+"-number");shot(label+"-number");tap(key("number/r2c3"));waitFor("Number input failed",()->main(()->host.getEditor().getText().toString().endsWith("5")));
   main(()->{service().selectT9Keyboard();return null;});waitFor("T9 missing",()->board() instanceof T9Keyboard);geometry(label+"-t9");shot(label+"-t9");passed.add(label+": QWERTY/T9/number, toolbar/sidebar alignment, English/Chinese/numeric input");
  }
  mode(InputMethodSettingsBridge.DarkModeOption.Light);main(()->{service().selectFullKeyboardIme(KeyboardSelectionPreset.ENGLISH);return null;});shot("final-light");passed.add("Release/R8 consumer completed");
 }
}
