import os
import subprocess
import glob
import zipfile
import shutil

MOD_VERSION = "1.0.1"
project_root = os.path.dirname(os.path.abspath(__file__))
build_dir = os.path.join(project_root, "build")
libs_out_dir = os.path.join(build_dir, "libs")
out_classes = os.path.join(build_dir, "classes")
out_jar = os.path.join(libs_out_dir, f"LingGachaAddon-{MOD_VERSION}+1.21.1-neoforge.jar")
root_jar = os.path.join(project_root, f"LingGachaAddon-{MOD_VERSION}+1.21.1-neoforge.jar")

if os.path.exists(out_classes):
    shutil.rmtree(out_classes)
os.makedirs(out_classes, exist_ok=True)
os.makedirs(libs_out_dir, exist_ok=True)

# Clean up any existing JAR files in build/libs and project root
for old_jar in glob.glob(os.path.join(libs_out_dir, "LingGachaAddon*.jar")):
    try: os.remove(old_jar)
    except Exception: pass
for old_jar in glob.glob(os.path.join(project_root, "LingGachaAddon*.jar")):
    try: os.remove(old_jar)
    except Exception: pass

# 1. Collect CP with valid zipfile check and Java 21 class version constraint
def is_valid_jar(p):
    if not p.endswith(".jar"): return False
    try:
        if os.path.getsize(p) < 100: return False
        with zipfile.ZipFile(p, 'r') as z:
            if z.testzip() is not None:
                return False
            for name in z.namelist():
                if name.endswith('.class'):
                    data = z.read(name)[:8]
                    if len(data) >= 8 and data[:4] == b'\xca\xfe\xba\xbe':
                        major = int.from_bytes(data[6:8], 'big')
                        if major > 65:  # Exclude classes newer than Java 21 (major version 65)
                            return False
                    break
            return True
    except Exception:
        return False

user_home = os.path.expanduser("~")
gradle_cache = os.path.join(user_home, ".gradle", "caches")
curseforge_libs = os.path.join(user_home, "curseforge", "minecraft", "Install", "libraries")

cp_jars = []

# Add ling_q_shop dependency
qshop_jars = [
    os.path.join(user_home, "Desktop", "Minecraft my mod", "ling_q_shop-neoforge-1.21.1", "ling_q_shop-v1.3-neoforge-1.21.1.jar"),
    os.path.join(user_home, "Desktop", "Minecraft my mod", "ling_q_shop-v1.3-neoforge-1.21.1.jar"),
    os.path.join(user_home, "curseforge", "minecraft", "Instances", "Ars Sky Island", "mods", "ling_q_shop-v1.3-neoforge-1.21.1.jar"),
    os.path.join(user_home, "curseforge", "minecraft", "Instances", "Ars Sky Island", "mods", "FtbQshop-1.1.jar")
]
for qj in qshop_jars:
    if os.path.exists(qj) and is_valid_jar(qj):
        cp_jars.append(qj.replace("\\", "/"))
        break

# Priority 1.21.1 NeoForge and vanilla client jars
priority_jars = [
    os.path.join(curseforge_libs, "net", "neoforged", "fancymodloader", "loader", "4.0.44", "loader-4.0.44.jar"),
    os.path.join(curseforge_libs, "net", "neoforged", "fancymodloader", "earlydisplay", "4.0.44", "earlydisplay-4.0.44.jar"),
    os.path.join(curseforge_libs, "net", "neoforged", "neoforge", "21.1.244", "neoforge-21.1.244-client.jar"),
    os.path.join(curseforge_libs, "net", "neoforged", "neoforge", "21.1.244", "neoforge-21.1.244-universal.jar"),
    os.path.join(curseforge_libs, "net", "minecraft", "client", "1.21.1-20240808.144430", "client-1.21.1-20240808.144430-extra.jar"),
    os.path.join(curseforge_libs, "net", "minecraft", "client", "1.21.1-20240808.144430", "client-1.21.1-20240808.144430-srg.jar"),
    os.path.join(curseforge_libs, "net", "minecraft", "client", "1.21.1", "client-1.21.1-official.jar")
]

for pj in priority_jars:
    if os.path.exists(pj) and is_valid_jar(pj):
        cp_jars.append(pj.replace("\\", "/"))

# Scan libraries excluding old forge jars and incompatible versions
if os.path.exists(curseforge_libs):
    for root, _, files in os.walk(curseforge_libs):
        root_lower = root.lower()
        if "minecraftforge" in root_lower: continue
        if "fancymodloader" in root_lower and ("11." in root_lower or "12." in root_lower): continue
        if "neoforge" in root_lower and ("26." in root_lower or "22." in root_lower): continue
        for f in files:
            if f.endswith(".jar"):
                full = os.path.join(root, f)
                if is_valid_jar(full) and full.replace("\\", "/") not in cp_jars:
                    cp_jars.append(full.replace("\\", "/"))

# Scan gradle caches for mod dependencies
if os.path.exists(gradle_cache):
    for root, _, files in os.walk(os.path.join(gradle_cache, "modules-2")):
        for f in files:
            if f.endswith(".jar") and not f.endswith("-sources.jar") and not f.endswith("-javadoc.jar"):
                if "ftb" in f.lower() or "architectury" in f.lower():
                    full = os.path.join(root, f)
                    if is_valid_jar(full) and full.replace("\\", "/") not in cp_jars:
                        cp_jars.append(full.replace("\\", "/"))

classpath = ";".join(cp_jars)

# 2. Collect Java files
java_files = []
for root, _, files in os.walk(os.path.join(project_root, "com")):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f).replace("\\", "/"))

print(f"Compiling {len(java_files)} Java files for Java 21 with {len(cp_jars)} valid classpath JARs...")

def find_javac():
    # 1. If JAVA_HOME is specifically Java 21, use it
    if "JAVA_HOME" in os.environ and os.environ["JAVA_HOME"]:
        jh = os.environ["JAVA_HOME"]
        if "21" in jh:
            cand = os.path.join(jh, "bin", "javac.exe" if os.name == "nt" else "javac")
            if os.path.exists(cand):
                return cand

    # 2. Prioritize dedicated JDK 21 installation locations on Windows
    for glob_pattern in [
        r"C:\Program Files\Eclipse Adoptium\jdk-21*\bin\javac.exe",
        r"C:\Program Files\Java\jdk-21*\bin\javac.exe",
        r"C:\Program Files\Microsoft\jdk-21*\bin\javac.exe",
        r"C:\Program Files\BellSoft\LibericaJDK-21*\bin\javac.exe",
        r"C:\Program Files\Zulu\zulu-21*\bin\javac.exe"
    ]:
        matches = glob.glob(glob_pattern)
        if matches:
            return matches[0]

    # 3. Fallback to generic JAVA_HOME
    if "JAVA_HOME" in os.environ and os.environ["JAVA_HOME"]:
        cand = os.path.join(os.environ["JAVA_HOME"], "bin", "javac.exe" if os.name == "nt" else "javac")
        if os.path.exists(cand):
            return cand

    # 4. Fallback to system PATH
    which_javac = shutil.which("javac")
    if which_javac:
        return which_javac

    return r"C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin\javac.exe"

args_txt = os.path.join(project_root, "args.txt")
with open(args_txt, "w", encoding="utf-8") as f:
    f.write("-encoding\nUTF-8\n")
    f.write("-source\n21\n")
    f.write("-target\n21\n")
    f.write("-Xlint:deprecation\n")
    f.write("-Xlint:unchecked\n")
    f.write("-proc:none\n")
    f.write("-sourcepath\n")
    f.write(f'"{project_root.replace(os.sep, "/")}"\n')
    f.write("-d\n")
    f.write(f'"{out_classes.replace(os.sep, "/")}"\n')
    f.write("-cp\n")
    f.write(f'"{classpath};{project_root.replace(os.sep, "/")}"\n')
    for jf in java_files:
        f.write(f'"{jf}"\n')

# 3. Invoke Javac using argfile
javac_bin = find_javac()
print(f"Using Java compiler: {javac_bin}")
javac_cmd = [
    javac_bin,
    "@" + args_txt.replace("\\", "/")
]

res = subprocess.run(javac_cmd, capture_output=True, text=True)
if res.stdout:
    print(res.stdout)
if res.stderr:
    if res.returncode == 0:
        print("[Compiler Warnings/Notes]:\n" + res.stderr)
    else:
        print("[Compiler Errors]:\n" + res.stderr)

if os.path.exists(args_txt):
    os.remove(args_txt)

if res.returncode != 0:
    print(f"Compilation failed with exit code {res.returncode}")
    exit(1)

print("Compilation successful!")

# 4. Packaging JAR
with zipfile.ZipFile(out_jar, "w", zipfile.ZIP_DEFLATED) as z:
    # Add classes
    for root, _, files in os.walk(out_classes):
        for f in files:
            full = os.path.join(root, f)
            rel = os.path.relpath(full, out_classes)
            z.write(full, rel)
    # Add META-INF
    meta_dir = os.path.join(project_root, "META-INF")
    if os.path.exists(meta_dir):
        for f in os.listdir(meta_dir):
            full = os.path.join(meta_dir, f)
            z.write(full, os.path.join("META-INF", f))
    # Add assets
    assets_dir = os.path.join(project_root, "assets")
    if os.path.exists(assets_dir):
        for root, _, files in os.walk(assets_dir):
            for f in files:
                full = os.path.join(root, f)
                rel = os.path.relpath(full, project_root)
                z.write(full, rel)
    # Add data
    data_dir = os.path.join(project_root, "data")
    if os.path.exists(data_dir):
        for root, _, files in os.walk(data_dir):
            for f in files:
                full = os.path.join(root, f)
                rel = os.path.relpath(full, project_root)
                z.write(full, rel)
    # Add pack.mcmeta
    pack_mc = os.path.join(project_root, "pack.mcmeta")
    if os.path.exists(pack_mc):
        z.write(pack_mc, "pack.mcmeta")
    # Add logo.png
    logo_file = os.path.join(project_root, "logo.png")
    if os.path.exists(logo_file):
        z.write(logo_file, "logo.png")

# Copy to root jar for easy release
shutil.copyfile(out_jar, root_jar)
print(f"Build complete! Output JAR: {out_jar}")
print(f"Release JAR: {root_jar}")

# Auto-deploy to CurseForge instance (if present)
cf_mods_dir = os.path.join(user_home, "curseforge", "minecraft", "Instances", "Ars Sky Island", "mods")
if os.path.exists(cf_mods_dir):
    try:
        # Clean any older LingGachaAddon versions in CurseForge to avoid duplicate mod crash
        for old_cf_jar in glob.glob(os.path.join(cf_mods_dir, "LingGachaAddon*.jar")):
            try: os.remove(old_cf_jar)
            except Exception: pass

        deployed_jar = os.path.join(cf_mods_dir, f"LingGachaAddon-{MOD_VERSION}+1.21.1-neoforge.jar")
        shutil.copyfile(out_jar, deployed_jar)
        print(f"Auto-deployed to CurseForge: {deployed_jar}")
    except Exception as e:
        print(f"CurseForge deploy skipped: {e}")


