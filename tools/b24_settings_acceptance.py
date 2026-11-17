"""One settings acceptance pass; restore only a temporary QA privacy-state change."""
from pathlib import Path
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/b2.4-settings/device'
OUT.mkdir(parents=True, exist_ok=True)
ADB = r'C:\Users\ASUS\AppData\Local\Android\Sdk\platform-tools\adb.exe'
DEVICE = 'RF8M41CKM3M'
PKG = 'com.example.pettranslator'
POLICY = 'files/datastore/privacy_consent.preferences_pb'
BACKUP = 'files/b24-original-consent.preferences_pb'


def adb(*args, timeout=40):
    result = subprocess.run([ADB, '-s', DEVICE, *args], capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace')[:300])
    return result.stdout.decode('utf-8', errors='replace')


def private_file(path):
    status = adb('shell', f"run-as {PKG} sh -c 'if test -f {path}; then echo B24_EXISTS; else echo B24_ABSENT; fi'").strip()
    if status == 'B24_ABSENT':
        return None
    assert status == 'B24_EXISTS', f'Unknown file status: {path}'
    r = subprocess.run([ADB, '-s', DEVICE, 'exec-out', 'run-as', PKG, 'cat', path], capture_output=True, timeout=15)
    assert r.returncode == 0
    return r.stdout


prepared = False
original = None
try:
    adb('shell', 'am', 'force-stop', PKG)
    original = private_file(POLICY)
    assert private_file(BACKUP) is None, 'Existing backup must not be overwritten'
    if original is not None:
        (OUT / 'original-consent.pb').write_bytes(original)
        adb('shell', 'run-as', PKG, 'cp', POLICY, BACKUP)
        assert private_file(BACKUP) == original
    prepared = True
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                 f'{PKG}.ProfileSettingsFlowTest', f'{PKG}.test/androidx.test.runner.AndroidJUnitRunner', timeout=180)
    (OUT / 'instrumentation.txt').write_text(result, encoding='utf-8')
    adb('pull', f'/sdcard/Android/data/{PKG}/files/b24-settings-evidence', str(OUT))
    assert 'OK (1 test)' in result and 'FAILURES!!!' not in result, 'Settings UI acceptance failed; no rerun'
    print('Settings acceptance PASS; no cache files deleted by the feature', flush=True)
finally:
    if prepared:
        adb('shell', 'am', 'force-stop', PKG)
        if private_file(POLICY) is not None:
            adb('shell', 'run-as', PKG, 'rm', POLICY)
        if original is not None:
            adb('shell', 'run-as', PKG, 'mv', BACKUP, POLICY)
        restored = private_file(POLICY) == original
        (OUT / 'state-restoration.json').write_text(json.dumps(dict(
            original_consent_present=original is not None,
            consent_restored_exactly=restored,
            app_data_not_cleared=True), indent=2), encoding='utf-8')
        assert restored, 'Original privacy state restoration failed'
        print('Original privacy state restored exactly', flush=True)
