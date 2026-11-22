#!/usr/bin/env python3
"""Verify settings discovery/composites using disposable, non-Android build fixtures."""
import argparse
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile


def run(wrapper, fixture, expected, *args):
    command = [str(wrapper), "-p", str(fixture), "projects", "--console=plain", "--offline", *args]
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode != 0:
        raise AssertionError(f"Gradle fixture failed:\n{result.stdout}")
    for project in expected:
        if project not in result.stdout:
            raise AssertionError(f"Missing {project}:\n{result.stdout}")
    return result.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("workspace", type=Path)
    parser.add_argument("--java-home", type=Path)
    args = parser.parse_args()
    workspace = args.workspace.resolve()
    if args.java_home:
        os.environ["JAVA_HOME"] = str(args.java_home.resolve())
    wrapper = workspace / ("gradlew.bat" if os.name == "nt" else "gradlew")
    with tempfile.TemporaryDirectory(prefix="mp-plugin-settings-") as directory:
        fixture = Path(directory)
        shutil.copy2(workspace / "settings.gradle", fixture / "settings.gradle")
        for module in re.findall(r"'(:[A-Za-z0-9:_-]+)'", (fixture / "settings.gradle").read_text(encoding="utf-8")):
            module_dir = fixture.joinpath(*module.lstrip(":").split(":"))
            module_dir.mkdir(parents=True, exist_ok=True)
            (module_dir / "build.gradle").write_text("", encoding="utf-8")
        (fixture / "build.gradle").write_text("", encoding="utf-8")
        gradle_dir = fixture / "gradle"
        gradle_dir.mkdir()
        shutil.copy2(workspace / "gradle/gradle-daemon-jvm.properties", gradle_dir / "gradle-daemon-jvm.properties")
        modules = {
            "plugins/zulu/build.gradle": "",
            "plugins/alpha/build.gradle.kts": "",
            "plugin-root/build.gradle.kts": "",
        }
        for name, source in modules.items():
            path = fixture / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
        (fixture / "plugins/not-a-module").mkdir()
        output = run(wrapper, fixture, ["Project ':plugins:alpha'", "Project ':plugins:zulu'", "Project ':plugin-root'"])
        if "Project ':plugins:not-a-module'" in output:
            raise AssertionError("Directory without a build script was incorrectly included")
        if output.index("Project ':plugins:alpha'") > output.index("Project ':plugins:zulu'"):
            raise AssertionError("Plugin ordering is not deterministic")
        print("PASS auto-discovery: Groovy, Kotlin, root legacy prefix, sorted order, non-module ignored")
        external = fixture / "external"
        external.mkdir()
        (external / "settings.gradle").write_text("rootProject.name = 'external-fixture'\n", encoding="utf-8")
        (external / "build.gradle").write_text("", encoding="utf-8")
        run(wrapper, fixture, ["Included build ':external'"], f"-PmpPluginBuilds={external},{external}")
        print("PASS external composite build discovery and deduplication")
        result = subprocess.run([str(wrapper), "-p", str(fixture), "projects", "--console=plain", "--offline",
                                 f"-PmpPluginBuilds={fixture / 'missing'}"],
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if result.returncode == 0 or "mpPluginBuilds: not a directory" not in result.stdout:
            raise AssertionError(f"Missing external build was not rejected clearly:\n{result.stdout}")
        print("PASS invalid composite path rejected")


if __name__ == "__main__":
    main()
