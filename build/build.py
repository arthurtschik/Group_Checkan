#!/usr/bin/env python3
"""Builds Group-Checkan.apk without the Android SDK/Gradle.

Needs: JDK, android.jar (compile classpath), dalvik-dx jar, apksig jar (paths via env or args).
"""
import hashlib, os, shutil, struct, subprocess, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
from axml import Node, a, encode_xml, encode_arsc, config, T_STRING, T_DEC, T_HEX, T_BOOL, T_REF

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TOOLS = os.environ["TOOLS"]
ANDROID_JAR = os.path.join(TOOLS, "android.jar")
DX_JAR = os.path.join(TOOLS, "dalvik-dx-16.0.1.jar")
APKSIG_JAR = os.path.join(TOOLS, "apksig-2.3.0.jar")
OUT = os.path.join(ROOT, "out")

# Build tools are pinned: a tampered tool must not end up in a signed release.
TOOL_SHA256 = {
    ANDROID_JAR: "6cea1df3efb77103ac3e2beb9bf4718964b0e0869ab16d39d29d5cbae1c147ad",   # API 34 stubs (compile only)
    DX_JAR: "1e4b645628e3bdb097b5331d669e177ef235a551582a8c646dbe36865e541907",        # Maven Central
    APKSIG_JAR: "9637078c0016244e4be0941836295365a7e2e5b164c59cb7885783c40460bfee",    # Maven Central
}
# SHA-256 of the release signing certificate (also pinned in MainActivity for in-app updates).
RELEASE_CERT_SHA256 = "4CC4B3A74564A3E258F853824E19205F1ECF7CFAF2FDAB9D236CB0FB8D958E04"
GEN = os.path.join(OUT, "gen")

PKG = "de.anwesenheit.app"
VERSION_CODE, VERSION_NAME = 18, "2.7"
MIN_SDK, TARGET_SDK = 24, 34

# android attribute ids (from android.R.attr)
R = dict(theme=0x01010000, label=0x01010001, icon=0x01010002, name=0x01010003, exported=0x01010010,
         launchMode=0x0101001d, screenOrientation=0x0101001e, configChanges=0x0101001f,
         minSdkVersion=0x0101020c, versionCode=0x0101021b, versionName=0x0101021c,
         windowSoftInputMode=0x0101022b, targetSdkVersion=0x01010270, allowBackup=0x01010280,
         hardwareAccelerated=0x010102d3, drawable=0x01010199, roundIcon=0x0101052c,
         dataExtractionRules=0x0101063e, authorities=0x01010018, grantUriPermissions=0x0101001b,
         usesCleartextTraffic=0x010104ec)
THEME_MATERIAL_LIGHT_NOACTIONBAR = 0x01030240

# app resource ids (must match encode_arsc layout below)
DRAWABLE_FG, DRAWABLE_BG, MIPMAP_LAUNCHER = 0x7f010000, 0x7f010001, 0x7f020000
XML_EXTRACTION_RULES = 0x7f030000


def run(cmd):
    print("$", " ".join(cmd[:6]), "..." if len(cmd) > 6 else "")
    subprocess.run(cmd, check=True)


def manifest():
    return Node("manifest", [
        a("package", T_STRING, PKG),
        a("versionCode", T_DEC, VERSION_CODE, R["versionCode"]),
        a("versionName", T_STRING, VERSION_NAME, R["versionName"]),
    ], [
        Node("uses-sdk", [a("minSdkVersion", T_DEC, MIN_SDK, R["minSdkVersion"]),
                          a("targetSdkVersion", T_DEC, TARGET_SDK, R["targetSdkVersion"])]),
        Node("uses-permission", [a("name", T_STRING, "android.permission.VIBRATE", R["name"])]),
        # network is used only for the update check/download from GitHub Releases (see MainActivity)
        Node("uses-permission", [a("name", T_STRING, "android.permission.INTERNET", R["name"])]),
        Node("uses-permission", [a("name", T_STRING, "android.permission.REQUEST_INSTALL_PACKAGES", R["name"])]),
        Node("application", [
            a("label", T_STRING, "Group Checkan", R["label"]),
            a("icon", T_REF, MIPMAP_LAUNCHER, R["icon"]),
            a("roundIcon", T_REF, MIPMAP_LAUNCHER, R["roundIcon"]),
            a("theme", T_REF, THEME_MATERIAL_LIGHT_NOACTIONBAR, R["theme"]),
            a("allowBackup", T_BOOL, False, R["allowBackup"]),             # no cloud/adb backup
            a("dataExtractionRules", T_REF, XML_EXTRACTION_RULES, R["dataExtractionRules"]),  # Android 12+: also no device transfer
            a("hardwareAccelerated", T_BOOL, True, R["hardwareAccelerated"]),
            a("usesCleartextTraffic", T_BOOL, False, R["usesCleartextTraffic"]),  # HTTPS only
        ], [
            Node("provider", [
                a("name", T_STRING, PKG + ".ApkProvider", R["name"]),
                a("authorities", T_STRING, PKG + ".apk", R["authorities"]),
                a("exported", T_BOOL, False, R["exported"]),
                a("grantUriPermissions", T_BOOL, True, R["grantUriPermissions"]),
            ]),
            Node("activity", [
                a("name", T_STRING, PKG + ".MainActivity", R["name"]),
                a("exported", T_BOOL, True, R["exported"]),
                a("launchMode", T_DEC, 2, R["launchMode"]),            # singleTask
                a("configChanges", T_HEX, 0x1FB0, R["configChanges"]),  # orientation|screenSize|uiMode|keyboard...|density
                a("windowSoftInputMode", T_HEX, 0x10, R["windowSoftInputMode"]),  # adjustResize
            ], [
                Node("intent-filter", [], [
                    Node("action", [a("name", T_STRING, "android.intent.action.MAIN", R["name"])]),
                    Node("category", [a("name", T_STRING, "android.intent.category.LAUNCHER", R["name"])]),
                ])
            ])
        ])
    ])


def extraction_rules():
    """Excludes all app data from cloud backup and device-to-device transfer (Android 12+)."""
    domains = ["root", "file", "database", "sharedpref", "external"]
    ex = lambda: [Node("exclude", [a("domain", T_STRING, d), a("path", T_STRING, ".")]) for d in domains]
    return Node("data-extraction-rules", [], [Node("cloud-backup", [], ex()), Node("device-transfer", [], ex())])


def adaptive_icon():
    return Node("adaptive-icon", [], [
        Node("background", [a("drawable", T_REF, DRAWABLE_BG, R["drawable"])]),
        Node("foreground", [a("drawable", T_REF, DRAWABLE_FG, R["drawable"])]),
    ])


def check_tools():
    for path, want in TOOL_SHA256.items():
        got = hashlib.sha256(open(path, "rb").read()).hexdigest()
        if got != want:
            sys.exit(f"Checksum mismatch for {path}: {got} – refusing to build")


def main():
    check_tools()
    ks = os.environ.get("KEYSTORE", os.path.join(ROOT, "build", "release.p12"))
    kpass = os.environ.get("KEYSTORE_PASS")
    if not kpass:
        sys.exit("KEYSTORE_PASS is not set")
    if not os.path.exists(ks):
        # Never create a new key silently: an APK with a different key cannot update installed apps.
        sys.exit(f"Signing key {ks} not found. Restore the release key (see README) – a new key would break all updates.")
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(GEN)
    classes = os.path.join(OUT, "classes")
    os.makedirs(classes)

    # 1) Java -> class files -> dex
    srcs = [os.path.join(dp, f) for dp, _, fs in os.walk(os.path.join(ROOT, "app", "src")) for f in fs if f.endswith(".java")]
    run(["javac", "-proc:none", "-nowarn", "-Xlint:-options", "-source", "8", "-target", "8", "-encoding", "UTF-8",
         "-bootclasspath", ANDROID_JAR, "-d", classes] + srcs)
    run(["java", "-cp", DX_JAR, "com.android.dx.command.Main", "--dex", f"--min-sdk-version={MIN_SDK}",
         "--output=" + os.path.join(GEN, "classes.dex"), classes])

    # 2) icons
    icon_dir = os.path.join(GEN, "icons")
    os.makedirs(icon_dir)
    run(["node", os.path.join(ROOT, "build", "icons.js"), icon_dir])

    # 3) binary xml + resource table
    files = {
        "AndroidManifest.xml": encode_xml(manifest()),
        "res/mipmap-anydpi-v26/ic_launcher.xml": encode_xml(adaptive_icon()),
        "res/xml/data_extraction_rules.xml": encode_xml(extraction_rules()),
    }
    for name, path in [("res/drawable-xxxhdpi-v4/ic_launcher_fg.png", "ic_launcher_fg.png"),
                       ("res/drawable-xxxhdpi-v4/ic_launcher_bg.png", "ic_launcher_bg.png"),
                       ("res/mipmap-xxxhdpi-v4/ic_launcher.png", "ic_launcher.png")]:
        files[name] = open(os.path.join(icon_dir, path), "rb").read()
    CONFIG_DENSITY, CONFIG_VERSION = 0x0100, 0x0400
    files["resources.arsc"] = encode_arsc(0x7f, PKG, [
        ("drawable", ["ic_launcher_fg", "ic_launcher_bg"], CONFIG_DENSITY,
         [(config(density=640, sdk=4), {0: "res/drawable-xxxhdpi-v4/ic_launcher_fg.png",
                                        1: "res/drawable-xxxhdpi-v4/ic_launcher_bg.png"})]),
        ("mipmap", ["ic_launcher"], CONFIG_DENSITY | CONFIG_VERSION,
         [(config(density=640, sdk=4), {0: "res/mipmap-xxxhdpi-v4/ic_launcher.png"}),
          (config(density=0xFFFE, sdk=26), {0: "res/mipmap-anydpi-v26/ic_launcher.xml"})]),
        ("xml", ["data_extraction_rules"], 0,
         [(config(), {0: "res/xml/data_extraction_rules.xml"})]),
    ])
    files["classes.dex"] = open(os.path.join(GEN, "classes.dex"), "rb").read()
    asset_root = os.path.join(ROOT, "app", "assets")
    for dp, _, fs in os.walk(asset_root):
        for f in fs:
            p = os.path.join(dp, f)
            files["assets/" + os.path.relpath(p, asset_root).replace(os.sep, "/")] = open(p, "rb").read()

    # 4) zip with 4-byte alignment of stored entries
    unsigned = os.path.join(OUT, "unsigned.apk")
    order = ["AndroidManifest.xml", "classes.dex", "resources.arsc"] + sorted(k for k in files if k not in
                                                                            ("AndroidManifest.xml", "classes.dex", "resources.arsc"))
    with zipfile.ZipFile(unsigned, "w") as z:
        for name in order:
            data = files[name]
            stored = name == "resources.arsc" or name.endswith(".png")
            zi = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
            if stored:
                off = z.fp.tell() + 30 + len(name.encode())
                zi.extra = b"\0" * ((4 - off % 4) % 4)
            z.writestr(zi, data)

    # 5) sign (APK Signature Scheme v2), then verify against the pinned release certificate
    signer_dir = os.path.join(OUT, "signer")
    os.makedirs(signer_dir)
    run(["javac", "-proc:none", "-nowarn", "-cp", APKSIG_JAR, "-d", signer_dir, os.path.join(ROOT, "build", "Sign.java")])
    final = os.path.join(ROOT, "Group-Checkan.apk")
    run(["java", "--add-exports=java.base/sun.security.x509=ALL-UNNAMED", "--add-exports=java.base/sun.security.pkcs=ALL-UNNAMED", "--add-exports=java.base/sun.security.util=ALL-UNNAMED", "-cp", APKSIG_JAR + os.pathsep + signer_dir, "Sign", ks, "anwesenheit", unsigned, final, RELEASE_CERT_SHA256])
    print("APK:", final, os.path.getsize(final), "bytes")
    print("APK SHA-256:", hashlib.sha256(open(final, "rb").read()).hexdigest())


if __name__ == "__main__":
    main()
