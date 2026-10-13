#!/usr/bin/env python3
"""Run against the external Release/R8 fixture on a dedicated test phone."""
import subprocess,time,json,re,sys
from pathlib import Path
adb='/Users/paopaoime/Library/Android/sdk/platform-tools/adb'
pkg=sys.argv[1] if len(sys.argv)>1 else 'com.paopaoime.entryvalidation'
work=Path(sys.argv[2] if len(sys.argv)>2 else 'tmp/oem-entry-callback-20261012/device-bridges');work.mkdir(parents=True,exist_ok=True)
component=pkg+'/com.paopaoime.company.EntryValidationActivity'
def run(*args):return subprocess.check_output([adb,*args],text=True)
def command(action,id='yes'):
 return run('shell','am','start','-n',component,'--es','action',action,'--es','drawer',id)
def records():
 pid=run('shell','pidof',pkg).strip()
 log=run('logcat','-b','main','-d','--pid='+pid,'-s','EntryValidation:I')
 return [json.loads(l[l.index('{'):]) for l in log.splitlines() if '{"event"' in l]
results=[]
for action in ['toolbar','status','background']:
 for id in ['yes','no','throws']:
  command('reset');time.sleep(.9)
  stamp=run('shell','date',r'+%m-%d\ %H:%M:%S.000').strip()
  command(action,id);time.sleep(1.1)
  result=records()[-1]
  ime=run('shell','dumpsys','input_method')
  activity=run('shell','dumpsys','activity','activities')
  assert result['callbacks']==1 and result['factories']==0 and result['drawerId']==id and result['mainThread'] and result['serviceContext'],result
  assert 'mInputShown=true' in ime and 'mCurId='+pkg+'/' in ime
  assert any('topResumedActivity=' in l and 'EntryValidationActivity' in l for l in activity.splitlines())
  pid=run('shell','pidof',pkg).strip();log=run('logcat','-b','main','-d','-T',stamp,'--pid='+pid)
  assert not any(s in log for s in ['Blocked custom drawer','SYNTHETIC_HOST_EXCEPTION','FATAL EXCEPTION','requestHideSelf','Toast.show'])
  name=action+'-'+id;(work/(name+'.log')).write_text(log);(work/(name+'-ime.txt')).write_text(ime)
  result.update(case=name,imeShown=True,noNewActivity=True);results.append(result);print('PASS',name,flush=True)
(work/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
