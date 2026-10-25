"""Own-package local-transport evidence on the explicitly selected fixture emulator.

Requires already-installed debug/test APKs and an already-enabled, scheduling-
disabled fixture. Never enables/disables Backup Manager: disabling it can wipe
all registered transports. Never targets cloud, all packages, initialization,
global cancellation or a full-system restore. Transport state is journaled under
ignored build/ before selection and restored in finally.
"""

import json
import os
from pathlib import Path
import re
import subprocess


PACKAGE = "in.daasavaani.app"
LOCAL = "com.android.localtransport/.LocalTransport"
TEST = f"{PACKAGE}.SystemBackupTransportTest#originalLogicalStateAndLocalControlSurviveReplacement"
ROOT = Path(__file__).resolve().parent.parent


def main():
    serial = os.environ.get("ANDROID_SERIAL")
    if serial != "emulator-5556":
        raise RuntimeError("Select only the authorized emulator-5556 fixture device")
    adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb.exe")

    def run(*args, quiet=False):
        result = subprocess.run([adb, "-s", serial, *args], capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=180)
        output = result.stdout + result.stderr
        if not quiet:
            print(output.strip(), flush=True)
        if result.returncode:
            raise RuntimeError("Owned emulator command failed")
        return output

    def shell(*args):
        return run("shell", *args)

    def phase(value):
        result = shell("am", "instrument", "-w", "-r", "-e", "class", TEST,
                       "-e", "systemBackupTransportPhase", value,
                       f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner")
        if "OK (1 test)" not in result or "FAILURES" in result:
            raise RuntimeError(f"Transport fixture phase {value} did not pass")

    if run("emu", "avd", "name").splitlines()[0] != "daasavaani_fixture_api36_fresh":
        raise RuntimeError("Fixture AVD identity mismatch")
    if shell("getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Not an emulator")
    transports = shell("bmgr", "list", "transports")
    original = re.search(r"^\s*\*\s+(\S+)", transports, re.MULTILINE)
    if not original or LOCAL not in transports:
        raise RuntimeError("Missing original or local transport")
    enabled = shell("bmgr", "enabled")
    if "currently enabled" not in enabled:
        raise RuntimeError("Local transport check blocked: Backup Manager is disabled; "
                           "toggling it back off can wipe all registered transports")
    if "Framework scheduling is disabled" not in run("shell", "dumpsys", "backup", quiet=True):
        raise RuntimeError("Local transport check requires an already scheduling-disabled fixture")
    phase("preflight")
    keys = ("backup_transport",)
    settings = {key: shell("settings", "get", "secure", key).strip() for key in keys}
    state_path = ROOT / "build" / "system-backup-transport-state.json"
    state_path.parent.mkdir(exist_ok=True)
    state_path.write_text(json.dumps({"serial": serial, "avd": "daasavaani_fixture_api36_fresh",
                                     "transport": original.group(1),
                                     "settings": settings}, indent=2) + "\n", encoding="utf-8")
    try:
        phase("seed")
        shell("bmgr", "transport", LOCAL)
        result = shell("bmgr", "backupnow", PACKAGE)
        if f"Package {PACKAGE} with result: Success" not in result or "Backup finished with result: Success" not in result:
            raise RuntimeError("Real framework full backup did not succeed")
        phase("mutate")
        sets = shell("bmgr", "list", "sets")
        tokens = re.findall(r"^\s*([0-9a-fA-F]+)\s*:", sets, re.MULTILINE)
        if len(tokens) != 1:
            raise RuntimeError("Expected one unambiguous local restore set")
        result = shell("bmgr", "restore", tokens[0], PACKAGE)
        if "restoreFinished: 0" not in result:
            raise RuntimeError("Real package-scoped framework restore did not succeed")
        phase("verify")
        print("Real API36 local-transport backup/restore and ordinary no-clear preservation passed.")
    finally:
        # Attempt every restoration even if cleanup itself fails, then report all failures.
        failures = []
        for action in (
            lambda: phase("cleanup"),
            lambda: shell("bmgr", "wipe", LOCAL, PACKAGE),
            lambda: shell("bmgr", "transport", original.group(1)),
        ):
            try:
                action()
            except (RuntimeError, subprocess.TimeoutExpired) as error:
                failures.append(str(error))
        for key, value in settings.items():
            current = shell("settings", "get", "secure", key).strip()
            if current != value:
                if value == "null":
                    shell("settings", "delete", "secure", key)
                else:
                    shell("settings", "put", "secure", key, value)
            if shell("settings", "get", "secure", key).strip() != value:
                failures.append(f"Could not restore exact secure setting {key}")
        final = shell("bmgr", "list", "transports")
        if not re.search(rf"^\s*\*\s+{re.escape(original.group(1))}\s*$", final, re.MULTILINE):
            failures.append("Could not restore original transport")
        if "currently enabled" not in shell("bmgr", "enabled"):
            failures.append("Backup enabled state changed unexpectedly")
        if failures:
            raise RuntimeError("; ".join(failures) + f"; recovery state: {state_path}")
        print("Exact original transport restored; only own-package local backup wiped.")


if __name__ == "__main__":
    main()
