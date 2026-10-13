#!/usr/bin/env python3
"""Verify null handler restores SDK generation and draft-only fill on the test phone."""
import subprocess,time,json,sys
from pathlib import Path
adb='/Users/paopaoime/Library/Android/sdk/platform-tools/adb'
pkg=sys.argv[1];work=Path(sys.argv[2]);work.mkdir(parents=True,exist_ok=True)
x,y=map(str,sys.argv[3:5]);resultX,resultY=map(str,sys.argv[5:7])
copyPkg='com.paopaoime.clipboardregression';component=pkg+'/com.paopaoime.company.EntryValidationActivity'
def run(*a):return subprocess.check_output([adb,*a],text=True)
run('shell','am','start','-n',component,'--es','action','quick_setup','--es','mode','default');time.sleep(1)
run('shell','am','start','-n',copyPkg+'/.CopyActivity','--ez','show','true','--ez','clear','true');time.sleep(1.7)
run('shell','am','start','-n',copyPkg+'/.CopyActivity','--es','copy','客户复制的测试原文：今天工作有点累。');time.sleep(1.4)
run('shell','input','tap',x,y);time.sleep(3)
pid=run('shell','pidof',pkg).strip();log=run('logcat','-b','main','-d','--pid='+pid,'-s','EntryValidation:I')
value=[json.loads(l[l.index('{'):]) for l in log.splitlines() if '{"event"' in l][-1]
assert value['event']=='model' and value['modelCalls']==1 and value['quickCalls']==0,value
(work/'model.log').write_text(log)
(work/'drawer.png').write_bytes(subprocess.check_output([adb,'exec-out','screencap','-p']))
run('shell','input','tap',resultX,resultY);time.sleep(2)
run('shell','am','start','-n',copyPkg+'/.CopyActivity');time.sleep(1)
cpid=run('shell','pidof',copyPkg).strip();log=run('logcat','-b','main','-d','--pid='+cpid,'-s','ExternalCopy:I')
assert 'editor=DEFAULT_QUICK_REPLY_DRAFT;sendActions=0' in log.splitlines()[-1],log
(work/'result.log').write_text(log)
(work/'editor.png').write_bytes(subprocess.check_output([adb,'exec-out','screencap','-p']))
(work/'result.json').write_text(json.dumps({'defaultModelCalls':1,'entryCallbackCalls':0,'draft':'DEFAULT_QUICK_REPLY_DRAFT','sendActions':0},indent=2)+'\n')
print('PASS default SDK generation and draft fill; zero send actions')
