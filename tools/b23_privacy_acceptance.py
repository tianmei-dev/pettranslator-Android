"""One host-driven acceptance pass; production withdrawal really terminates its process."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/b2.3-privacy-consent/device"
ADB = r"C:\Users\ASUS\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "RF8M41CKM3M"
PKG = "com.example.pettranslator"
POLICY = "files/datastore/privacy_consent.preferences_pb"
PET = "files/datastore/pet_preferences.preferences_pb"
SENTINEL = "files/b23_privacy_data_sentinel.txt"
OUT.mkdir(parents=True, exist_ok=True)
events = []
sequence = 0


def adb(*args, check=True, binary=False):
    result = subprocess.run([ADB, "-s", DEVICE, *args], capture_output=True, timeout=40)
    if check and result.returncode:
        raise RuntimeError(f"adb {args[:3]} failed: {result.stderr.decode(errors='replace')[:250]}")
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace").strip()


def note(case, result="PASS", **details):
    events.append(dict(case=case, result=result, **details))
    (OUT / "acceptance.json").write_text(json.dumps(events, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{case}: {result}", flush=True)


def dump(label):
    global sequence
    sequence += 1
    adb("shell", "uiautomator", "dump", "/sdcard/Download/b23-state.xml")
    path = OUT / f"{sequence:02d}-{label}.xml"
    adb("pull", "/sdcard/Download/b23-state.xml", str(path))
    return ET.parse(path).getroot()


def visible_nodes(ui, text):
    matches = []
    parents = {child: parent for parent in ui.iter() for child in parent}
    for node in ui.iter("node"):
        if (node.get("text") != text and node.get("content-desc") != text) or node.get("package") != PKG:
            continue
        target = node
        while target.get("clickable") != "true" and target in parents:
            target = parents[target]
        bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", target.get("bounds", ""))
        if bounds:
            x1, y1, x2, y2 = map(int, bounds.groups())
            if x2 > x1 and y2 > y1:
                matches.append(((x1 + x2) // 2, (y1 + y2) // 2))
    return matches


def wait_page(text, label):
    deadline = time.monotonic() + 15
    while True:
        ui = dump(label)
        if any(n.get("text") == text and n.get("package") == PKG for n in ui.iter("node")):
            return ui
        if time.monotonic() >= deadline:
            raise AssertionError(f"Expected page text not reached: {text}")


def tap(ui, text):
    points = visible_nodes(ui, text)
    assert points, f"Clickable text not visible: {text}"
    x, y = max(points, key=lambda point: point[1])
    adb("shell", "input", "tap", str(x), str(y))


def screenshot(name, expected_text):
    ui = wait_page(expected_text, "capture-check")
    assert any(n.get("package") == PKG for n in ui.iter("node"))
    remote = f"/sdcard/Download/b23-{name}.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(OUT / f"{name}.png"))


def private_file(path):
    exists = adb("shell", "run-as", PKG, "ls", path, check=False)
    return adb("exec-out", "run-as", PKG, "cat", path, binary=True) if exists == path else None


def logcat():
    return adb("logcat", "-d", "-v", "threadtime", "-s", "PrivacyConsent:I", "AndroidRuntime:E")


def logs_for_pid(pid):
    return "\n".join(line for line in logcat().splitlines()
                     if len(line.split()) > 3 and line.split()[2] == pid)


def cold_start():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    return adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")


def await_exit():
    deadline = time.monotonic() + 12
    while adb("shell", "pidof", PKG, check=False):
        if time.monotonic() > deadline:
            raise AssertionError("App did not terminate after withdrawal")
        time.sleep(0.2)


def permission_snapshot():
    text = adb("shell", "dumpsys", "package", PKG)
    return re.findall(r"android.permission.(?:RECORD_AUDIO|WRITE_EXTERNAL_STORAGE): granted=(?:true|false)", text)


try:
    permissions_before = permission_snapshot()
    pet_before = private_file(PET)
    assert private_file(SENTINEL) is None, "Refusing to overwrite an existing sentinel"
    previous_consent = private_file(POLICY)
    assert previous_consent is None or b"Accepted" not in previous_consent, "Initial consent already accepted; no reset permitted"
    adb("shell", f"run-as {PKG} sh -c 'printf B23-preserve-business-data > {SENTINEL}'")
    sentinel_before = private_file(SENTINEL)

    cold_start()
    ui = wait_page("隐私政策说明", "first-consent")
    assert not any(n.get("text") == "开始录音翻译" for n in ui.iter("node"))
    screenshot("first-consent", "隐私政策说明")
    note("P05-first-consent", persistent_consent_preexisting=previous_consent is not None)
    tap(ui, "阅读隐私政策")
    ui = wait_page("隐私政策", "policy-reader")
    assert any("【开发阶段草稿｜正式发布前需审核完善】" in n.get("text", "") for n in ui.iter("node"))
    screenshot("policy-before-consent", "隐私政策")
    tap(ui, "返回")
    ui = wait_page("隐私政策说明", "return-to-consent")
    tap(ui, "不同意")
    ui = wait_page("你尚未同意隐私政策，暂时无法进入应用。你仍可阅读政策、重新同意，或退出应用。", "denied")
    assert b"Denied" in private_file(POLICY)
    segment = logs_for_pid(adb("shell", "pidof", PKG))
    assert "yamnet_runtime_created" not in segment and "yamnet_model_loaded" not in segment
    note("P06-before-agreement", normal_business_absent=True, yamnet_initialization_absent=True, denied_persisted=True)
    tap(ui, "同意并继续")
    ui = wait_page("开始录音翻译", "agreed-home")
    accepted = private_file(POLICY)
    assert b"Accepted" in accepted and b"2026-11-16-draft-1" in accepted
    (OUT / "accepted-before.pb").write_bytes(accepted)
    cold_start()
    ui = wait_page("开始录音翻译", "accepted-cold-start")
    assert not any(n.get("text") == "隐私政策说明" for n in ui.iter("node"))
    note("P07-agree-and-restart", restored_without_prompt=True)

    tap(ui, "我的")
    ui = wait_page("系统权限管理", "profile")
    tap(ui, "隐私政策授权撤销")
    ui = wait_page("撤销隐私政策授权", "revoke-confirm")
    screenshot("revoke-confirmation", "撤销隐私政策授权")
    note("P01-confirmation")
    tap(ui, "取消")
    ui = wait_page("系统权限管理", "cancelled")
    assert private_file(POLICY) == accepted
    note("P02-cancel", consent_bytes_unchanged=True)
    tap(ui, "隐私政策授权撤销")
    ui = wait_page("撤销隐私政策授权", "revoke-again")
    withdraw_pid = adb("shell", "pidof", PKG)
    tap(ui, "确认撤销")
    await_exit()
    revoked = private_file(POLICY)
    assert b"Revoked" in revoked and b"Accepted" not in revoked
    (OUT / "revoked.pb").write_bytes(revoked)
    withdrawal_log = logs_for_pid(withdraw_pid)
    order = [withdrawal_log.index(event) for event in (
        "consent_gate_closed", "consent_revoked_persisted", "controlled_work_stopped", "privacy_exit_process")]
    assert order == sorted(order)
    note("P03-P04-withdraw-and-exit", revoked_persisted=True, process_terminated=True)

    cold_start()
    ui = wait_page("隐私政策说明", "revoked-cold-start")
    assert not any(n.get("text") == "开始录音翻译" for n in ui.iter("node"))
    screenshot("revoked-cold-start", "隐私政策说明")
    new_segment = logs_for_pid(adb("shell", "pidof", PKG))
    assert "yamnet_runtime_created" not in new_segment and "yamnet_model_loaded" not in new_segment
    note("P05-P06-after-withdrawal", normal_business_absent=True, yamnet_initialization_absent=True)
    tap(ui, "同意并继续")
    wait_page("开始录音翻译", "reaccepted-home")
    assert b"Accepted" in private_file(POLICY)
    assert private_file(SENTINEL) == sentinel_before
    pet_after = private_file(PET)
    if pet_before is not None:
        assert pet_after == pet_before
    else:
        assert pet_after is None or b"pet_type" not in pet_after
    assert permission_snapshot() == permissions_before
    note("P08-data-preserved", pet_preferences_sha256=hashlib.sha256(pet_before).hexdigest() if pet_before else None,
         persistent_sentinel_unchanged=True, android_permissions_unchanged=True)
    adb("shell", "run-as", PKG, "rm", SENTINEL)
    note("COMPLETE", result="PASS", remaining_failure_scenarios="JVM tests", real_native_inflight_not_exercised=True)
except Exception as error:
    note("STOPPED", result="FAIL", error=str(error))
    raise
finally:
    (OUT / "privacy-logcat.txt").write_text(logcat(), encoding="utf-8")
