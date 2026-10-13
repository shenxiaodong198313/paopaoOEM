#!/usr/bin/env python3
"""Export the complete OEM SDK delivery on macOS/Linux.

This is the all-modules equivalent of export-sdk.ps1. The Maven repository and
public documentation remain the single sources of truth.
"""

import argparse
import hashlib
import json
import shutil
from datetime import datetime
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


SDK_ROOT = Path(__file__).resolve().parent
OEM_ROOT = SDK_ROOT.parent
SOURCE_REPO = OEM_ROOT / "android/build/oem-sdk-repo"
CATALOG = SDK_ROOT / "catalog/oem-sdk-catalog.json"


def digest(path: Path, algorithm: str) -> str:
    value = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version-tag", default="current")
    args = parser.parse_args()

    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    sdk_version = catalog["sdkVersion"]
    gradle_properties = (OEM_ROOT / "android/gradle.properties").read_text(encoding="utf-8")
    if f"oemSdkVersion={sdk_version}" not in gradle_properties.splitlines():
        raise RuntimeError("Catalog version does not match Gradle's oemSdkVersion")
    if not SOURCE_REPO.is_dir():
        raise RuntimeError(f"Missing published Maven repository: {SOURCE_REPO}")

    artifacts = []
    seen = set()
    for module in catalog["modules"]:
        for coordinate in module["artifacts"]:
            if coordinate in seen:
                continue
            seen.add(coordinate)
            group, artifact, version = coordinate.split(":")
            directory = SOURCE_REPO / group.replace(".", "/") / artifact / version
            primary = directory / f"{artifact}-{version}.aar"
            if not primary.is_file():
                raise RuntimeError(f"Missing published AAR: {coordinate}")
            for extension in ("pom", "module"):
                if not (directory / f"{artifact}-{version}.{extension}").is_file():
                    raise RuntimeError(f"Missing {extension}: {coordinate}")
            artifacts.append({
                "module": module["id"],
                "group": group,
                "artifact": artifact,
                "version": version,
                "file": primary.name,
                "md5": digest(primary, "md5"),
                "sha256": digest(primary, "sha256"),
            })

    target_repo = SDK_ROOT / "repo"
    target_docs = SDK_ROOT / "docs"
    for target in (target_repo, target_docs):
        if target.exists():
            shutil.rmtree(target)
    shutil.copytree(SOURCE_REPO, target_repo)
    shutil.copytree(
        OEM_ROOT / "website/oem",
        target_docs,
        ignore=shutil.ignore_patterns("*.apk", "oem-sdk-delivery-*.zip"),
    )
    for external_doc in (OEM_ROOT / "Doc").glob("外部版-*.md"):
        shutil.copy2(external_doc, target_docs / external_doc.name)

    manifest = {
        "schemaVersion": 1,
        "sdkName": catalog["sdkName"],
        "sdkVersion": sdk_version,
        "generatedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
        "requestedModules": ["all"],
        "resolvedModules": [module["id"] for module in catalog["modules"]],
        "modules": [
            {key: module[key] for key in ("id", "name", "capability", "required")}
            for module in catalog["modules"]
        ],
        "artifacts": artifacts,
        "missingArtifacts": [],
    }
    manifest_path = SDK_ROOT / "oem-sdk-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    readme_path = SDK_ROOT / "DELIVERY_README.txt"
    readme_path.write_text(
        f"OEM SDK delivery package\n\nSDK version: {sdk_version}\n\n"
        "Contents:\n- repo/: local Maven repository for customer integration\n"
        "- docs/: customer-facing integration documents\n"
        "- oem-sdk-manifest.json: full module list, artifact MD5/SHA256\n",
        encoding="utf-8",
    )

    output = SDK_ROOT / f"oem-sdk-delivery-{args.version_tag}.zip"
    with ZipFile(output, "w", compression=ZIP_DEFLATED, compresslevel=6, allowZip64=True) as archive:
        for path in (target_repo, target_docs):
            for file in sorted(path.rglob("*")):
                if file.is_file():
                    archive.write(file, file.relative_to(SDK_ROOT))
        for file in (manifest_path, readme_path):
            archive.write(file, file.name)
    print(f"Exported {len(artifacts)} AARs to {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
