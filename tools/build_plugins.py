#!/usr/bin/env python3
"""Build the plugin catalog: every theme folder with a plugin.json becomes an installable plugin.

    python3 tools/build_plugins.py            # validate only
    python3 tools/build_plugins.py --out dist # validate, then write dist/<id>-<version>.zip and dist/catalog.json

A plugin folder (themes/<id>/) holds:
    plugin.json      {"name": "...", "version": "1.0.0", "description": "...", "author": "..."}
    config.json      the theme overlay, keys as in themes/README.md
    wallpaper.jpg    optional
    wallpapers/      optional, a rotating collection (jpg, jpeg, png, webp)
    preview.jpg      optional, shown in the catalog, not installed
    README.md        optional, not installed

The zip holds only the files the launcher writes (the same ones tools/theme.sh pushes), and the launcher
checks every entry again (ThemeFiles.path) before writing it. catalog.json names each zip relative to its
own URL, so the catalog works from any release or mirror. .github/workflows/plugins.yml runs this on every
push to main and uploads dist/ to the "plugins" release.
"""
import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEMES = ROOT / "themes"
ID = re.compile(r"^[a-z0-9][a-z0-9-]{0,39}$")
VERSION = re.compile(r"^\d+(\.\d+){0,2}$")
IMAGE = re.compile(r"^[A-Za-z0-9_.-]+\.(jpg|jpeg|png|webp)$", re.IGNORECASE)
PREVIEW = ("preview.jpg", "preview.png")
NOT_INSTALLED = {"plugin.json", "README.md", *PREVIEW}
MAX_ZIP = 20 * 1024 * 1024
# Fixed timestamp, so an unchanged plugin builds the same bytes and the same SHA-256.
EPOCH = (2020, 1, 1, 0, 0, 0)


class Invalid(Exception):
    pass


def text(meta, key, limit, required=True):
    value = meta.get(key, "")
    if not isinstance(value, str) or (required and not value.strip()):
        raise Invalid(f"plugin.json: '{key}' must be a non-empty string")
    value = value.strip()
    if len(value) > limit:
        raise Invalid(f"plugin.json: '{key}' is over {limit} characters")
    return value


def installed_files(folder):
    """The (zip name, path) pairs a plugin installs; anything else in the folder is an error."""
    files = []
    for path in sorted(folder.rglob("*")):
        if path.is_dir():
            continue
        rel = path.relative_to(folder).as_posix()
        if rel in NOT_INSTALLED:
            continue
        if rel in ("config.json", "wallpaper.jpg"):
            files.append((rel, path))
        elif rel.startswith("wallpapers/") and rel.count("/") == 1 and IMAGE.match(path.name) \
                and not path.name.startswith("."):
            files.append((rel, path))
        else:
            raise Invalid(f"unexpected file {rel}: a plugin holds config.json, wallpaper.jpg, "
                          "wallpapers/<image>, preview.jpg, plugin.json and README.md")
    return files


def load(folder):
    if not ID.match(folder.name):
        raise Invalid("folder name must be 1-40 lowercase letters, digits or '-', starting with a letter or digit")
    try:
        meta = json.loads((folder / "plugin.json").read_text(encoding="utf-8"))
    except ValueError as e:
        raise Invalid(f"plugin.json is not valid JSON: {e}")
    if not isinstance(meta, dict):
        raise Invalid("plugin.json must be an object")
    version = text(meta, "version", 20)
    if not VERSION.match(version):
        raise Invalid("plugin.json: 'version' must look like 1, 1.2 or 1.2.3")
    config = folder / "config.json"
    if not config.is_file():
        raise Invalid("config.json is missing")
    try:
        if not isinstance(json.loads(config.read_text(encoding="utf-8")), dict):
            raise Invalid("config.json must be a JSON object")
    except ValueError as e:
        raise Invalid(f"config.json is not valid JSON: {e}")
    return {
        "id": folder.name,
        "name": text(meta, "name", 40),
        "version": version,
        "description": text(meta, "description", 200, required=False),
        "author": text(meta, "author", 60, required=False),
        "files": installed_files(folder),
        "preview": next((folder / p for p in PREVIEW if (folder / p).is_file()), None),
    }


def write_zip(plugin, dest):
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
        for name, path in plugin["files"]:
            info = zipfile.ZipInfo(name, EPOCH)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, path.read_bytes())
    if dest.stat().st_size > MAX_ZIP:
        raise Invalid(f"zip is {dest.stat().st_size // 1024} KB, over the {MAX_ZIP // 1024 // 1024} MB limit")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--out", type=Path, help="write the zips and catalog.json here")
    args = parser.parse_args()

    plugins, errors = [], []
    for folder in sorted(p for p in THEMES.iterdir() if (p / "plugin.json").is_file()):
        try:
            plugins.append(load(folder))
        except Invalid as e:
            errors.append(f"themes/{folder.name}: {e}")
    for e in errors:
        print(f"error: {e}", file=sys.stderr)
    if errors:
        return 1
    print(f"{len(plugins)} plugin(s): " + ", ".join(f"{p['id']} {p['version']}" for p in plugins))
    if not args.out:
        return 0

    args.out.mkdir(parents=True, exist_ok=True)
    catalog = []
    for p in plugins:
        zip_name = f"{p['id']}-{p['version']}.zip"
        try:
            write_zip(p, args.out / zip_name)
        except Invalid as e:
            print(f"error: themes/{p['id']}: {e}", file=sys.stderr)
            return 1
        data = (args.out / zip_name).read_bytes()
        entry = {k: p[k] for k in ("id", "name", "version", "description", "author")}
        entry.update(file=zip_name, size=len(data), sha256=hashlib.sha256(data).hexdigest())
        if p["preview"]:
            preview_name = f"{p['id']}-{p['preview'].name}"
            (args.out / preview_name).write_bytes(p["preview"].read_bytes())
            entry["preview"] = preview_name
        catalog.append(entry)
    (args.out / "catalog.json").write_text(
        json.dumps({"spec": 1, "plugins": catalog}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {args.out}/catalog.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
