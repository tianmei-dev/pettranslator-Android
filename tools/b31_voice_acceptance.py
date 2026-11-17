"""One device round; restore only QA-modified consent, pet choice and microphone grant."""
from pathlib import Path
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/b3.1-voice-talk/device'
OUT.mkdir(parents=True, exist_ok=True)
ADB = r'C:\Users\ASUS\AppData\Local\Android\Sdk\platform-tools\adb.exe'
DEVICE = 'RF8M41CKM3M'
PKG = 'com.example.pettranslator'
MIC = 'android.permission.RECORD_AUDIO'
STORES = ['files/datastore/privacy_consent.preferences_pb', 'files/datastore/pet_preferences.preferences_pb']


def adb(*args, check=True, timeout=40):
    r = subprocess.run([ADB, '-s', DEVICE, *args], capture_output=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError(r.stderr.decode(errors='replace')[:300])
    return r.stdout.decode('utf-8', errors='replace').strip()


def private_file(path):
    status = adb('shell', f"run-as {PKG} sh -c 'if test -f {path}; then echo B31_EXISTS; else echo B31_ABSENT; fi'")
    if status == 'B31_ABSENT':
        return None
    assert status == 'B31_EXISTS', f'Unrecognized file status: {path}'
    r = subprocess.run([ADB, '-s', DEVICE, 'exec-out', 'run-as', PKG, 'cat', path], capture_output=True, timeout=15)
    assert r.returncode == 0
    return r.stdout


def permission():
    package = adb('shell', 'dumpsys', 'package', PKG)
    match = re.search(r'android\.permission\.RECORD_AUDIO: granted=(true|false), flags=\[([^\]]*)\]', package)
    return dict(granted=bool(match and match[1] == 'true'), flags=match[2].split('|') if match else [])


backups = {}
original_permission = None
prepared_permission = False
try:
    adb('shell', 'am', 'force-stop', PKG)
    original_permission = permission()
    original_permission['flags'] = [f.strip() for f in original_permission['flags'] if f.strip()]
    assert not {'POLICY_FIXED', 'SYSTEM_FIXED', 'ONE_TIME'} & set(original_permission['flags']), 'Cannot safely override policy/one-time microphone grant'
    assert original_permission['granted'] and 'USER_SET' in original_permission['flags'] and 'USER_FIXED' not in original_permission['flags'], 'This device pass requires an existing user-set microphone grant for reversible OS-dialog testing'
    (OUT / 'permission-before.json').write_text(json.dumps(original_permission, indent=2), encoding='utf-8')
    for index, path in enumerate(STORES):
        original = private_file(path)
        backup = f'files/b31-original-store-{index}.pb'
        assert private_file(backup) is None, 'Existing backup must not be overwritten'
        if original is not None:
            adb('shell', 'run-as', PKG, 'cp', path, backup)
            assert private_file(backup) == original
        backups[path] = (original, backup)
    prepared_permission = True
    adb('shell', 'pm', 'revoke', PKG, MIC)
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                 f'{PKG}.VoiceTalkDeviceTest,{PKG}.VoiceTalkIsolatedUiTest',
                 f'{PKG}.test/androidx.test.runner.AndroidJUnitRunner', timeout=240)
    (OUT / 'instrumentation.txt').write_text(result, encoding='utf-8')
    adb('pull', f'/sdcard/Android/data/{PKG}/files/b31-voice-evidence', str(OUT))
    assert 'OK (2 tests)' in result and 'FAILURES!!!' not in result, 'Voice acceptance incomplete; no rerun'
    print('One voice acceptance round PASS', flush=True)
finally:
    adb('shell', 'am', 'force-stop', PKG)
    restored = {}
    for path, (original, backup) in backups.items():
        if private_file(path) is not None:
            adb('shell', 'run-as', PKG, 'rm', path)
        if original is not None:
            adb('shell', 'run-as', PKG, 'mv', backup, path)
        restored[path] = private_file(path) == original
    permission_restored = None
    if prepared_permission:
        adb('shell', 'pm', 'grant' if original_permission['granted'] else 'revoke', PKG, MIC)
        after = permission()
        after_flags = {f.strip() for f in after['flags']}
        permission_restored = after['granted'] == original_permission['granted'] and all(
            (flag in after_flags) == (flag in original_permission['flags']) for flag in ('USER_SET', 'USER_FIXED'))
    logs = adb('logcat', '-d', '-v', 'threadtime', '-s', 'VoiceTalkCapture:I', 'B31Voice:I', 'AndroidRuntime:E')
    (OUT / 'voice-logcat.txt').write_text(logs, encoding='utf-8')
    (OUT / 'state-restoration.json').write_text(json.dumps(dict(stores=restored,
        microphone_grant_and_user_flags_restored=permission_restored, app_data_not_cleared=True), indent=2), encoding='utf-8')
    assert all(restored.values()) and (not prepared_permission or permission_restored), 'QA state restoration failed'
    print('Original stores and microphone grant/user flags restored', flush=True)
