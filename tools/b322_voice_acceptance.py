"""One B3.2.2 automated device round; no user speech or subjective listening claim."""
from pathlib import Path
import hashlib,json,re,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/b3.2.2-voice-conversion/device';OUT.mkdir(parents=True,exist_ok=True)
ADB=r'C:\Users\ASUS\AppData\Local\Android\Sdk\platform-tools\adb.exe'
SERIAL=sys.argv[1] if len(sys.argv)>1 else '89UX0H4KU';PKG='com.example.pettranslator';MIC='android.permission.RECORD_AUDIO'

def adb(*args,check=True,timeout=30,binary=False):
    p=subprocess.run([ADB,'-s',SERIAL,*args],capture_output=True,timeout=timeout)
    if check and p.returncode:raise RuntimeError(p.stderr.decode(errors='replace')[-600:])
    return p.stdout if binary else p.stdout.decode('utf-8',errors='replace').strip()
def permission():
    data=adb('shell','dumpsys','package',PKG)
    m=re.search(r'android\.permission\.RECORD_AUDIO: granted=(true|false), flags=\[([^\]]*)\]',data)
    assert m,'Permission record missing'
    return {'granted':m[1]=='true','flags':sorted(f.strip() for f in m[2].split('|') if f.strip())}
def stores():
    listing=adb('shell','run-as',PKG,'ls','files/datastore',check=False)
    result={}
    for name in listing.splitlines():
        if not re.fullmatch(r'[\w.\-]+\.preferences_pb',name):continue
        data=adb('exec-out','run-as',PKG,'cat',f'files/datastore/{name}',binary=True)
        result[name]=hashlib.sha256(data).hexdigest()
    return result

assert not (OUT/'instrumentation.txt').exists(),'This device round already ran; do not repeat'
original=None;before=None;changed_permission=False
try:
    state=adb('shell','dumpsys','window','policy')
    assert 'showing=false' in state,'Device must be unlocked before acceptance'
    adb('shell','am','force-stop',PKG)
    original=permission();before=stores()
    (OUT/'before.json').write_text(json.dumps({'microphone':original,'datastore_sha256':before},indent=2),encoding='utf-8')
    if not original['granted']:
        assert not {'USER_FIXED','SYSTEM_FIXED','POLICY_FIXED','ONE_TIME'} & set(original['flags']),'Do not override user/policy-fixed permission'
        adb('shell','pm','grant',PKG,MIC);changed_permission=True
    result=adb('shell','am','instrument','-w','-r','-e','class',f'{PKG}.VoiceConversionDeviceTest',f'{PKG}.test/androidx.test.runner.AndroidJUnitRunner',timeout=240)
    (OUT/'instrumentation.txt').write_text(result,encoding='utf-8')
    pull=adb('pull',f'/sdcard/Android/data/{PKG}/files/b322-evidence',str(OUT),check=False)
    (OUT/'pull.txt').write_text(pull,encoding='utf-8')
    print('Instrumentation result:', 'PASS' if 'OK (1 test)' in result and 'FAILURES!!!' not in result else 'INCOMPLETE_OR_FAILED',flush=True)
    assert 'OK (1 test)' in result and 'FAILURES!!!' not in result,'Acceptance incomplete; no automatic rerun'
finally:
    adb('shell','am','force-stop',PKG,check=False)
    if changed_permission:adb('shell','pm','revoke',PKG,MIC)
    after=permission() if original else None
    after_stores=stores() if before is not None else None
    report={'original_microphone':original,'restored_microphone':after,'permission_exactly_restored':original==after,'datastore_before':before,'datastore_after':after_stores,'datastore_unchanged':before==after_stores,'app_data_not_cleared':True,'manual_speech_and_listening_tested':False}
    (OUT/'state-protection.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    logs=adb('logcat','-d','-v','threadtime','-s','VoiceTalkCapture:I','VoiceConversionPlayback:I','B322Device:I','AndroidRuntime:E',check=False)
    (OUT/'logcat.txt').write_text(logs,encoding='utf-8')
    print('Permission and existing DataStore restoration:',report['permission_exactly_restored'] and report['datastore_unchanged'],flush=True)
    assert report['permission_exactly_restored'] and report['datastore_unchanged'],'Acceptance state protection needs attention'
