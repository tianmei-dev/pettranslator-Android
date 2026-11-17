"""Single acceptance round. Isolate and restore only the dedicated privacy state file."""
from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/b2.3.1-startup/device'
OUT.mkdir(parents=True, exist_ok=True)
ADB = r'C:\Users\ASUS\AppData\Local\Android\Sdk\platform-tools\adb.exe'
DEVICE = 'RF8M41CKM3M'
PKG = 'com.example.pettranslator'
POLICY = 'files/datastore/privacy_consent.preferences_pb'
BACKUP = 'files/b231-original-consent.preferences_pb'
PET = 'files/datastore/pet_preferences.preferences_pb'
events, seq = [], 0
isolated = False


def adb(*args, check=True, binary=False, timeout=40):
    r = subprocess.run([ADB, '-s', DEVICE, *args], capture_output=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError(f'adb {args[:3]} failed: {r.stderr.decode(errors="replace")[:200]}')
    return r.stdout if binary else r.stdout.decode('utf-8', errors='replace').strip()


def private_file(path):
    # Some adbd exec-out services return 0 even when cat reports ENOENT on stdout.
    status = adb('shell', f"run-as {PKG} sh -c 'if test -f {path}; then echo B231_EXISTS; else echo B231_ABSENT; fi'")
    if status == 'B231_ABSENT':
        return None
    assert status == 'B231_EXISTS', f'Unable to establish file existence: {path}'
    r = subprocess.run([ADB, '-s', DEVICE, 'exec-out', 'run-as', PKG, 'cat', path], capture_output=True, timeout=15)
    if r.returncode:
        raise RuntimeError(f'Unable to read verified private file: {path}')
    return r.stdout


def note(case, **details):
    events.append(dict(case=case, **details))
    (OUT / 'acceptance.json').write_text(json.dumps(events, ensure_ascii=False, indent=2), encoding='utf-8')
    print(case, flush=True)


def page(text, name):
    global seq
    end = time.monotonic() + 15
    while True:
        seq += 1
        adb('shell', 'uiautomator', 'dump', '/sdcard/Download/b231-state.xml')
        path = OUT / f'{seq:02d}-{name}.xml'
        adb('pull', '/sdcard/Download/b231-state.xml', str(path))
        ui = ET.parse(path).getroot()
        if any(n.get('package') == PKG and n.get('text') == text for n in ui.iter('node')):
            return ui
        if time.monotonic() >= end:
            raise AssertionError(f'Page not reached: {text}')


def tap(ui, text):
    parents = {child: parent for parent in ui.iter() for child in parent}
    for node in ui.iter('node'):
        if node.get('text') != text or node.get('package') != PKG:
            continue
        while node.get('clickable') != 'true' and node in parents:
            node = parents[node]
        box = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if box:
            x1, y1, x2, y2 = map(int, box.groups())
            if x2 > x1 and y2 > y1:
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                return
    raise AssertionError(f'Clickable text not found: {text}')


def cold_start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    return adb('shell', 'am', 'start', '-W', '-n', f'{PKG}/.MainActivity')


try:
    adb('shell', 'am', 'force-stop', PKG)
    original = private_file(POLICY)
    pet_before = private_file(PET)
    assert private_file(BACKUP) is None, 'Refusing to replace an existing backup'
    if original is not None:
        (OUT / 'original-consent.pb').write_bytes(original)
        adb('shell', 'run-as', PKG, 'mv', POLICY, BACKUP)
    isolated = True
    note('privacy_state_isolated', original_present=original is not None)

    cold_start()
    ui = page('隐私政策与用户协议', 'first-unconsented')
    assert not any(n.get('text') in ('开始录音翻译', '正在读取隐私授权状态…') for n in ui.iter('node'))
    tap(ui, '不同意并退出')
    end = time.monotonic() + 12
    while adb('shell', 'pidof', PKG, check=False):
        if time.monotonic() > end:
            raise AssertionError('Decline did not exit the app')
        time.sleep(.2)
    denied = private_file(POLICY)
    assert denied is not None and b'Denied' in denied and b'Accepted' not in denied
    note('decline_persisted_and_exited', passed=True)

    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                 f'{PKG}.StartupFlowTest', f'{PKG}.test/androidx.test.runner.AndroidJUnitRunner', timeout=180)
    (OUT / 'instrumentation.txt').write_text(result, encoding='utf-8')
    adb('pull', f'/sdcard/Android/data/{PKG}/files/b231-startup-evidence', str(OUT))
    assert 'OK (1 test)' in result and 'FAILURES!!!' not in result, 'Concentrated UI flow failed; see instrumentation.txt'
    note('links_first_agreement_hot_and_recreation', passed=True)

    launch = cold_start()
    (OUT / 'cold-launch.txt').write_text(launch, encoding='utf-8')
    cold_pid = adb('shell', 'pidof', PKG)
    # Capture immediately after launch; an XML dump could consume the entire two-second splash.
    adb('shell', 'screencap', '-p', '/sdcard/Download/b231-cold-splash.png')
    adb('pull', '/sdcard/Download/b231-cold-splash.png', str(OUT / 'cold-splash.png'))
    ui = page('开始录音翻译', 'cold-home')
    assert not any(n.get('text') == '隐私政策与用户协议' for n in ui.iter('node'))
    logs = adb('logcat', '-d', '-v', 'threadtime', '-s', 'StartupPresentation:I', 'PrivacyConsent:I', 'AndroidRuntime:E')
    (OUT / 'startup-logcat.txt').write_text(logs, encoding='utf-8')
    per_pid = '\n'.join(line for line in logs.splitlines() if len(line.split()) > 3 and line.split()[2] == cold_pid)
    durations = re.findall(r'splash_finished durationMs=(\d+)', per_pid)
    assert per_pid.count('splash_visible') == 1 and len(durations) == 1
    assert 2000 <= int(durations[0]) <= 3500
    note('authorized_cold_start', passed=True, duration_ms=int(durations[0]), custom_splash_count=1,
         system_splash_not_included_in_duration=True)
except Exception as error:
    note('STOPPED', passed=False, error=str(error))
    raise
finally:
    if isolated:
        adb('shell', 'am', 'force-stop', PKG)
        if private_file(POLICY) is not None:
            adb('shell', 'run-as', PKG, 'rm', POLICY)
        if original is not None:
            adb('shell', 'run-as', PKG, 'mv', BACKUP, POLICY)
        restored = private_file(POLICY) == original
        pet_after = private_file(PET)
        business_unchanged = pet_after == pet_before if pet_before is not None else (pet_after is None or b'pet_type' not in pet_after)
        note('original_state_restored', consent_bytes_exact=restored, pet_preferences_preserved=business_unchanged)
        assert restored and business_unchanged
