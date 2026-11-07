#!/usr/bin/env python3
"""Automates Subvox recording via ADB + uiautomator.

Uses resource-id selectors (com.example.subvox:id/recordButton) rather than
visible text, so the soft keyboard and the Record/Stop label change do not
break the script.

Citations:
- https://developer.android.com/tools/adb#uiautomator (dump --compressed)
- https://github.com/bajutsu-e2e/bajutsu (resource-id selector pattern)
"""
import subprocess, time, re, sys, json, pathlib

PKG = "com.example.subvox"
AUDIT = pathlib.Path("dist/audit"); AUDIT.mkdir(parents=True, exist_ok=True)
MD = AUDIT / "audit.md"; lines = ["# Subvox Automation Audit\n"]

def log(m):
    print(m); lines.append(m + "\n")

def run(cmd, timeout=30):
    kw = dict(stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    p = subprocess.Popen(cmd, **kw)
    try: out, err = p.communicate(timeout=timeout)
    except subprocess.TimeoutExpired: p.kill(); out, err = p.communicate()
    return out or "", err or "", p.returncode

def adb_devices():
    out, _, _ = run(["adb", "devices", "-l"])
    for line in (out or "").splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            return parts[0]
    return None

serial = adb_devices()
if not serial: print("[FATAL] no device"); sys.exit(2)
log(f"Device: {serial}\n")

run(["adb", "-s", serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP"])
run(["adb", "-s", serial, "shell", "wm", "dismiss-keyguard"])
run(["adb", "-s", serial, "shell", "am", "start", "-n", f"{PKG}/.MainActivity"])
time.sleep(2)

def screenshot(name):
    path = AUDIT / name
    with open(path, "wb") as f:
        p = subprocess.Popen(["adb", "-s", serial, "exec-out", "screencap", "-p"],
                             stdout=f, stderr=subprocess.PIPE)
        p.communicate(timeout=30)
    log(f"![{name}]({name})")

def tap_by_id(res_id, retries=5):
    pattern = rf'resource-id="{re.escape(res_id)}".*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    for _ in range(retries):
        run(["adb", "-s", serial, "shell", "uiautomator", "dump", "--compressed", "/sdcard/window_dump.xml"])
        xml, _, _ = run(["adb", "-s", serial, "exec-out", "cat", "/sdcard/window_dump.xml"])
        m = re.search(pattern, xml)
        if m:
            l, t, r, b = int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4))
            run(["adb", "-s", serial, "shell", "input", "tap", str((l+r)//2), str((t+b)//2)])
            return True
        time.sleep(1)
    return False

def clear_and_type(text):
    run(["adb", "-s", serial, "shell", "input", "keyevent", "KEYCODE_MOVE_END"])
    run(["adb", "-s", serial, "shell", "input", "keyevent"] + ["67"] * 20)
    safe = text.replace(" ", "%s").replace("'", "'\\''")
    run(["adb", "-s", serial, "shell", "input", "text", safe])

plan = ["one", "one", "two", "two"]
for i, word in enumerate(plan, 1):
    log(f"\n## Take {i}: {word}")
    if not tap_by_id(f"{PKG}:id/inputLabel"): log("  [FAIL] inputLabel"); continue
    time.sleep(0.5)
    clear_and_type(word)
    time.sleep(0.5)
    screenshot(f"take_{i:02d}_01_typed.png")
    run(["adb", "-s", serial, "shell", "input", "keyevent", "4"])  # dismiss keyboard
    if tap_by_id(f"{PKG}:id/recordButton"):
        log("  Tapped: Record Reference"); time.sleep(0.5)
        screenshot(f"take_{i:02d}_02_recording.png")
    else: log("  [FAIL] recordButton")
    time.sleep(2)
    if tap_by_id(f"{PKG}:id/recordButton"):
        log("  Tapped: Stop & Save"); time.sleep(0.5)
        screenshot(f"take_{i:02d}_03_saved.png")
    else: log("  [FAIL] recordButton (stop)")

out, err, rc = run(["adb", "-s", serial, "shell", "run-as", PKG, "cat", "files/subvox_dataset.json"])
if rc == 0 and out.strip().startswith("["):
    pathlib.Path("dist/subvox_dataset.json").write_text(out, encoding="utf-8")
    records = json.loads(out); names = sorted({r["label"] for r in records})
    log(f"\n## Dataset: {len(records)} takes, {len(names)} labels ({', '.join(names)})")
else:
    log(f"\n[FAIL] pull dataset: {(err or out).strip()[:200]}")

MD.write_text("".join(lines), encoding="utf-8")
print(f"[OK] audit written to {MD}")
