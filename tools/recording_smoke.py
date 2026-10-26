"""Production UI + OS permission/interrupt smoke check on a dedicated emulator only.
Requires a fresh install (RECORD_AUDIO not yet granted); does not export audio.
"""
import argparse, json, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--adb',required=True)
parser.add_argument('--out',required=True)
a=parser.parse_args()
assert a.serial.startswith('emulator-'), 'Only a dedicated emulator is supported.'
out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
pkg='com.example.pettranslator'
results={}
height=int(re.findall(r'\d+x(\d+)', subprocess.run([a.adb,'-s',a.serial,'shell','wm','size'],capture_output=True,text=True).stdout)[-1])
def adb(*args,check=True):
 p=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,encoding='utf-8',errors='replace')
 if check and p.returncode: raise RuntimeError(p.stderr+p.stdout)
 return p.stdout.strip()
def ui():
 adb('shell','uiautomator','dump','/sdcard/a121-ui.xml')
 return ET.fromstring(adb('shell','cat','/sdcard/a121-ui.xml'))
def tap(node):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
 time.sleep(.3)
def click(text):
 for _ in range(7):
  root=ui()
  for n in root.iter('node'):
   if n.get('text')==text:
    coords=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
    if len(coords)==4 and coords[3]>coords[1] and coords[1]>60:
     tap(n);return
  adb('shell','input','swipe','350',str(int(height*.75)),'350',str(int(height*.35)),'350')
 raise AssertionError('Cannot find '+text)
def deny(allow_no_dialog=False):
 for n in ui().iter('node'):
  if n.get('resource-id','').endswith(('permission_deny_button','permission_deny_and_dont_ask_again_button')):
   tap(n);return
 if allow_no_dialog and '前往应用设置' in [n.get('text') for n in ui().iter('node')]: return
 raise AssertionError('No system permission denial button')
def capture(name):
 adb('shell','screencap','-p','/sdcard/a121-check.png')
 adb('pull','/sdcard/a121-check.png',str(out/name))
def files():
 raw=adb('shell','run-as',pkg,'ls','cache/pet_recordings',check=False)
 return [x for x in raw.splitlines() if re.fullmatch(r'capture_[\w-]+\.pcm',x)]
def enter():
 if '听听毛孩子的声音' not in [n.get('text') for n in ui().iter('node')]: click('开始录音翻译')
def begin():
 click('开始录音');time.sleep(.6)
 assert files(), 'Capture did not create a private PCM file'
def ready():
 texts=[n.get('text','') for n in ui().iter('node')]
 assert '开始录音' in texts, 'Interrupted capture did not return to READY'
 assert '录音完成' not in texts, 'Interrupted capture was incorrectly completed'
 assert not files(), 'Interrupted PCM was not deleted'
adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','wm','dismiss-keyguard')
adb('shell','am','start','-W','-n',pkg+'/.MainActivity')
enter();capture('cat-ready.png');click('开始录音');deny()
assert '需要麦克风权限，才能听见毛孩子的声音' in [n.get('text') for n in ui().iter('node')]
results['first_denial_explained']=True
click('开始录音');deny(allow_no_dialog=True)
assert '前往应用设置' in [n.get('text') for n in ui().iter('node')]
results['permanent_denial_settings_entry']=True
capture('permission-denied.png')
adb('shell','pm','grant',pkg,'android.permission.RECORD_AUDIO')
begin();adb('shell','input','keyevent','KEYCODE_SLEEP');time.sleep(.5)
adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','wm','dismiss-keyguard');time.sleep(.4)
ready();results['lock_cancelled_and_deleted']=True
begin();adb('shell','am','force-stop',pkg)
results['process_kill_left_orphan']=bool(files())
adb('shell','run-as',pkg,'touch','cache/pet_recordings/capture_orphan_probe.pcm')
adb('shell','am','start','-W','-n',pkg+'/.MainActivity');time.sleep(.5)
assert not files();results['next_process_removed_orphans']=True
enter();begin()
adb('shell','pm','revoke',pkg,'android.permission.RECORD_AUDIO');time.sleep(.3)
results['permission_revocation_killed_process']=not bool(adb('shell','pidof',pkg,check=False))
adb('shell','am','start','-W','-n',pkg+'/.MainActivity');time.sleep(.5)
assert not files();results['revocation_restart_clean']=True
(out/'checks.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(results,ensure_ascii=False))
