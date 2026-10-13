#!/usr/bin/env python3
"""Run actual smart-reply taps on a configured external Release/R8 fixture."""
import subprocess,time,json,sys
from pathlib import Path
adb='/Users/paopaoime/Library/Android/sdk/platform-tools/adb'
pkg=sys.argv[1] if len(sys.argv)>1 else 'com.paopaoime.entryvalidation'
work=Path(sys.argv[2]);work.mkdir(parents=True,exist_ok=True)
copyPkg='com.paopaoime.clipboardregression'
copyComponent=copyPkg+'/.CopyActivity'
SOURCE='客户复制的测试原文：今天工作有点累。'
x,y=map(str,sys.argv[3:5]);component=pkg+'/com.paopaoime.company.EntryValidationActivity'
def run(*args):return subprocess.check_output([adb,*args],text=True)
def cmd(action,*extra):
 run('shell','am','start','-n',component,'--es','action',action,*extra);time.sleep(1.1)
def records():
 pid=run('shell','pidof',pkg).strip();log=run('logcat','-b','main','-d','--pid='+pid,'-s','EntryValidation:I')
 return [json.loads(l[l.index('{'):]) for l in log.splitlines() if '{"event"' in l]
def screenshot(name):
 (work/(name+'.png')).write_bytes(subprocess.check_output([adb,'exec-out','screencap','-p']))
results=[]
for mode in ['true','false','throws','unregister']:
 cmd('quick_setup','--es','mode',mode)
 run('shell','am','start','-n',copyComponent,'--ez','show','true','--ez','clear','true');time.sleep(1.7)
 run('shell','am','start','-n',copyComponent,'--es','copy',SOURCE);time.sleep(1.4);screenshot(mode+'-before')
 stamp=run('shell','date',r'+%m-%d\ %H:%M:%S.000').strip()
 run('shell','input','tap',x,y);time.sleep(1.3)
 value=records()[-1]
 assert value['event']=='quick_callback' and value['quickCalls']==1 and value['quickMain'] and value['quickSource'] and value['quickHost']==copyPkg,value
 assert value['modelCalls']==0 and value['editor']=='',value
 ime=run('shell','dumpsys','input_method');act=run('shell','dumpsys','activity','activities')
 assert 'mInputShown=true' in ime and 'mCurId='+pkg+'/' in ime
 assert any('topResumedActivity=' in l and copyPkg in l for l in act.splitlines())
 pid=run('shell','pidof',pkg).strip();log=run('logcat','-b','main','-d','-T',stamp,'--pid='+pid)
 assert not any(s in log for s in ['SYNTHETIC_QUICK_REPLY_EXCEPTION','FATAL EXCEPTION','Toast.show','requestHideSelf','MaterialHubActivity'])
 screenshot(mode+'-after');(work/(mode+'.log')).write_text(log);(work/(mode+'-ime.txt')).write_text(ime)
 results.append(dict(value,case=mode,imeShown=True,noNewActivity=True));print('PASS smart-reply UI',mode,flush=True)
cmd('quick_setup','--es','mode','true');cmd('quick_bridge','--ez','nullHost','true')
value=records()[-1];assert value['quickCalls']==1 and value['quickMain'] and value['quickSource'] and value['quickHost'] is None,value
results.append(dict(value,case='null-host'));print('PASS null host',flush=True)
(work/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
