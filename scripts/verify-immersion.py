"""Verify unsigned Everyone 5.0.2 immersion APK without exposing private keys."""
import json
import pathlib
import sys
import zipfile

if len(sys.argv) != 4:
    raise SystemExit("usage: verify-immersion.py BASE.apk UNSIGNED.apk BUILD_DIR")
base, output, build = map(pathlib.Path, sys.argv[1:])
old_dir, new_dir = build / "old-smali", build / "new-smali"
old = {str(p.relative_to(old_dir)): p.read_bytes() for p in old_dir.rglob("*.smali")}
new = {str(p.relative_to(new_dir)): p.read_bytes() for p in new_dir.rglob("*.smali")}

def in_scope(name):
    prefix = "org/openenglish/reader/"
    targets = ("JourneyActivity", "JourneyImmersionActivity", "JourneySubtitles",
               "JourneyCantonese", "JourneyPracticeAudio", "JourneyToneContour", "JourneyToneAudio")
    return name.startswith(tuple(prefix + target for target in targets))

unchanged = [name for name in old if not in_scope(name)]
for name in unchanged:
    assert new.get(name) == old[name], f"Unexpected modification: {name}"

added = [name for name in new if name not in old]
assert added and all(in_scope(name) for name in added), f"Unexpected classes: {added}"
assert any(name.endswith("/JourneyImmersionActivity.smali") for name in added)
assert any(name.endswith("/JourneySubtitles.smali") for name in added)
assert any(name.endswith("/JourneyCantonese.smali") for name in added)
assert any(name.endswith("/JourneyPracticeAudio.smali") for name in added)
assert any(name.endswith("/JourneyToneContour.smali") for name in added)
assert any(name.endswith("/JourneyToneAudio.smali") for name in added)
assert any(name.endswith("/JourneyActivity.smali") for name in old)
assert any(name.endswith("/JourneyActivity.smali") for name in new)

with zipfile.ZipFile(base) as old_apk, zipfile.ZipFile(output) as new_apk:
    existing = set(old_apk.namelist()) - {x for x in old_apk.namelist() if x.startswith("META-INF/")}
    produced = set(new_apk.namelist())
    assert existing == produced, "APK entry set changed"
    preserved = existing - {"classes.dex", "AndroidManifest.xml"}
    for name in preserved:
        assert old_apk.read(name) == new_apk.read(name), f"Unexpected APK entry change: {name}"
    assert old_apk.read("classes.dex") != new_apk.read("classes.dex")
    assert old_apk.read("AndroidManifest.xml") != new_apk.read("AndroidManifest.xml")

report = {
    "version_name": "5.0.2",
    "version_code": 61,
    "previous_other_classes_unchanged": len(unchanged),
    "added_immersion_classes": len(added),
    "preserved_archive_entries": len(preserved),
    "original_certificate_required": True,
    "apk_signed": False,
    "verified_on_android_phone": False,
}
(build / "immersion-verification.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
print(json.dumps(report, ensure_ascii=False))
