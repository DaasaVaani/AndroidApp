"""Synthetic own-package ADB full-backup lifecycle evidence, not cloud/transport proof.

Uses the platform's confirmation UI without passwords, account changes, transport
changes or Backup Manager toggles. Requires already-installed debug/test APKs.
"""

import io
import os
from pathlib import Path
import re
import subprocess
import tarfile
import time
import xml.etree.ElementTree as ET


PACKAGE = "in.daasavaani.app"
ROOT = Path(__file__).resolve().parent.parent


def main():
    serial = os.environ.get("ANDROID_SERIAL")
    if serial != "emulator-5556":
        raise RuntimeError("Select only the authorized emulator-5556 fixture")
    adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb.exe")

    def command(*args, timeout=30):
        result = subprocess.run([adb, "-s", serial, *args], capture_output=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError("Owned fixture command failed")
        return result.stdout.decode("utf-8", errors="strict")

    def shell(*args):
        return command("shell", *args)

    def phase(value):
        output = command("shell", "am", "instrument", "-w", "-r", "-e", "class",
                         f"{PACKAGE}.SystemBackupTransportTest#originalLogicalStateAndLocalControlSurviveReplacement",
                         "-e", "systemBackupTransportPhase", value,
                         f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner", timeout=90)
        print(output.strip(), flush=True)
        if "OK (1 test)" not in output or "FAILURES" in output:
            raise RuntimeError(f"Own-package fixture {value} failed")

    def confirm(process):
        deadline = time.monotonic() + 30
        labels = {shell("cmd", "overlay", "lookup", "com.android.backupconfirm",
                        f"com.android.backupconfirm:string/{key}").strip().casefold()
                  for key in ("allow_backup_button_label", "allow_restore_button_label")}
        observed = []
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError("Platform ADB operation ended without confirmation")
            hierarchy = command("exec-out", "uiautomator", "dump", "/dev/tty")
            start = hierarchy.find("<?xml")
            end = hierarchy.rfind("</hierarchy>")
            if start >= 0 and end >= 0:
                tree = ET.fromstring(hierarchy[start:end + len("</hierarchy>")])
                observed = [(node.get("package"), node.get("resource-id"), node.get("class"), node.get("enabled"))
                            for node in tree.iter("node") if node.get("clickable") == "true"]
                allow = [node for node in tree.iter("node")
                         if node.get("package") == "com.android.backupconfirm" and
                         (node.get("resource-id") == "com.android.backupconfirm:id/button_allow" or
                          (node.get("class") == "android.widget.Button" and
                           node.get("text", "").casefold() in labels)) and
                         node.get("enabled") == "true"]
                if len(allow) == 1:
                    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", allow[0].get("bounds", ""))
                    if not bounds:
                        raise RuntimeError("Unexpected confirmation bounds")
                    left, top, right, bottom = map(int, bounds.groups())
                    # Never populate any password field. Only synthetic, unencrypted test data.
                    shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                    return
            time.sleep(0.25)
        raise RuntimeError(f"Platform confirmation not observed within 30 seconds; clickable metadata={observed}")

    def cancel_confirmation():
        hierarchy = command("exec-out", "uiautomator", "dump", "/dev/tty")
        start = hierarchy.find("<?xml")
        end = hierarchy.rfind("</hierarchy>")
        if start < 0 or end < 0:
            return
        tree = ET.fromstring(hierarchy[start:end + len("</hierarchy>")])
        for node in tree.iter("node"):
            if (node.get("package") == "com.android.backupconfirm" and
                    node.get("resource-id") == "com.android.backupconfirm:id/button_deny" and
                    node.get("enabled") == "true"):
                bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
                if bounds:
                    left, top, right, bottom = map(int, bounds.groups())
                    shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                return

    if command("emu", "avd", "name").splitlines()[0] != "daasavaani_fixture_api36_fresh":
        raise RuntimeError("Unexpected fixture AVD")
    if shell("getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Not the fixture emulator")
    original_enabled = shell("bmgr", "enabled")
    original_transports = shell("bmgr", "list", "transports")
    phase("preflight")
    archive = ROOT / "build" / "system-backup-adb.ab"
    archive.parent.mkdir(exist_ok=True)
    process = None
    try:
        phase("seed")
        with archive.open("wb") as output:
            process = subprocess.Popen([adb, "-s", serial, "exec-out", "bu", "backup",
                                        "-noapk", "-noobb", "-noshared", "-nowidgets",
                                        "-noall", "-nosystem", "-nocompress", PACKAGE],
                                       stdout=output, stderr=subprocess.PIPE)
            confirm(process)
            _, errors = process.communicate(timeout=90)
            if process.returncode:
                raise RuntimeError("Package-scoped platform ADB backup failed")
        raw = archive.read_bytes()
        header = b"ANDROID BACKUP\n5\n0\nnone\n"
        if not raw.startswith(header):
            raise RuntimeError("Expected unencrypted uncompressed Android backup v5")
        with tarfile.open(fileobj=io.BytesIO(raw[len(header):]), mode="r:") as tar:
            names = tar.getnames()
            expected = [f"apps/{PACKAGE}/_manifest",
                        f"apps/{PACKAGE}/f/system-personal-v1.json.gz"]
            if names != expected or not all(item.isfile() for item in tar.getmembers()):
                raise RuntimeError("Framework archive has unexpected files/domains")
            if tar.getmember(expected[1]).size <= 0:
                raise RuntimeError("Framework archive has no logical snapshot")
        print("Actual framework archive: manifest + one personal logical snapshot; no DB/WAL/content/control.")
        phase("mutate")
        # The supported host command opens the archive in binary mode and waits for
        # remote completion. shell/exec-in can truncate or SIGHUP the remote restore.
        process = subprocess.Popen([adb, "-s", serial, "restore", str(archive)],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        confirm(process)
        process.communicate(timeout=90)
        if process.returncode:
            raise RuntimeError("Package-scoped platform ADB restore failed")
        phase("verify")
        print("Actual API36 BackupAgent/restore-file/finished lifecycle and ordinary no-clear preservation passed.")
    finally:
        try:
            cancel_confirmation()
        finally:
            if process is not None and process.poll() is None:
                process.kill()
                process.communicate(timeout=10)
            try:
                phase("cleanup")
            finally:
                if shell("bmgr", "enabled") != original_enabled or shell("bmgr", "list", "transports") != original_transports:
                    raise RuntimeError("Backup Manager state changed unexpectedly")
                print("Backup Manager enabled state and transport selection unchanged.")


if __name__ == "__main__":
    main()
