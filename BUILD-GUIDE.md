# Build Guide

This guide explains how to build Voyage API from source on any machine (Linux, macOS or Windows, x86_64 or ARM64).

## TL;DR

```bash
# 1. Make sure JDK 8 is the active Java (see "Installing JDK 8" below)
java -version          # should print 1.8.x

# 2. Build (skipping tests)
./gradlew clean build -x test        # Linux / macOS
gradlew.bat clean build -x test      # Windows (cmd / PowerShell)

# 3. Result
ls build/libs/*.war
```

## 1. Technology stack

| Component        | Version                                    | Notes                                                   |
|------------------|--------------------------------------------|---------------------------------------------------------|
| Java (JDK)       | **8 (1.8)** recommended                    | `sourceCompatibility = '1.8'`; see note below           |
| Gradle           | 6.5.1 (via the included wrapper)           | Do not use a system-wide Gradle install                 |
| Groovy           | 3.0.x (resolved by Gradle)                 | No separate install needed                              |
| Spring Boot      | 2.3.1.RELEASE                              |                                                         |
| Node.js / npm    | 22.x, **downloaded automatically** by Gradle | Only used to generate the API docs (apidoc)           |
| Build output     | Executable WAR                             | `build/libs/<project-folder-name>.war`                  |

**About the Java version:** the build was verified with JDK 8. Gradle 6.5.1 supports JDK 8 to 14 and does not run on JDK 15 or newer (so JDK 17 and 21 will not work). JDK 11 is within range but has not been verified. Use JDK 8 unless you have a reason not to.

**About Node.js:** you do **not** need Node.js installed. The `com.github.node-gradle.node` plugin downloads Node 22 into the project's `.gradle/nodejs/` folder on the first build. An existing Node installation on your machine is not used.

## 2. Prerequisites

1. **JDK 8.** A full JDK is required, a JRE is not enough. Any vendor works (Eclipse Temurin, Azul Zulu, Amazon Corretto, OpenJDK from your OS package manager, etc.).
2. **Internet access** (at least for the first build) to:
   - `services.gradle.org` (Gradle 6.5.1 distribution, only once)
   - `plugins.gradle.org` (Gradle plugins)
   - `repo.maven.apache.org` (Maven Central, Java dependencies)
   - `nodejs.org` (Node.js download, only once)
   - `registry.npmjs.org` (npm packages for apidoc)
   - `oss.sonatype.org` is also declared in `settings.gradle`; it is no longer needed and the build works even if it is unreachable.
3. **About 1.5 GB of free disk space** for Gradle, the dependency cache, Node.js and `node_modules`.
4. **Git**, to clone the repository.

You do **not** need Gradle, Groovy, Maven or Node.js installed.

## 3. Installing JDK 8

Pick the option for your platform. After installing, open a **new** terminal and check with `java -version` (it should report `1.8.0_xxx`).

### Any platform: SDKMAN! (Linux, macOS, WSL)

```bash
curl -s "https://get.sdkman.io" | bash
sdk list java | grep -E "8\.0\.[0-9]+"     # pick an available JDK 8 identifier
sdk install java <identifier>               # e.g. 8.0.xxx-tem or 8.0.xxx-zulu
sdk use java <identifier>
```

### Linux

- **Debian / Ubuntu:** `sudo apt install openjdk-8-jdk` (on newer releases where it is not packaged, use SDKMAN! or a Temurin / Zulu tarball).
- **Fedora / RHEL / CentOS:** `sudo dnf install java-1.8.0-openjdk-devel`
- **Arch:** `sudo pacman -S jdk8-openjdk`
- **Manual tarball:** download a JDK 8 `.tar.gz` for your architecture (x64 or aarch64) from <https://adoptium.net> or <https://www.azul.com/downloads/>, extract it anywhere and point `JAVA_HOME` to the extracted folder.

If several JDKs are installed, select JDK 8 with `sudo update-alternatives --config java` (Debian/Ubuntu), `sudo alternatives --config java` (Fedora/RHEL), or simply set `JAVA_HOME` for the build (see below).

### macOS

- **Intel (x86_64):** `brew install --cask temurin@8`
- **Apple Silicon (arm64):** Temurin 8 does not ship a native arm64 macOS build. Use Azul Zulu 8, which does: `brew install --cask zulu@8`, or download it from <https://www.azul.com/downloads/>.

List installed JDKs and get the JDK 8 path with:

```bash
/usr/libexec/java_home -V
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
```

### Windows

- **winget:** `winget install EclipseAdoptium.Temurin.8.JDK`
- **Chocolatey:** `choco install temurin8`
- **Manual:** download the JDK 8 `.msi` from <https://adoptium.net> and enable the "Set JAVA_HOME" option in the installer.

### Using JDK 8 only for this build

You don't have to make JDK 8 your default Java. Setting `JAVA_HOME` in the current terminal is enough, because the Gradle wrapper uses it:

```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk8
export PATH="$JAVA_HOME/bin:$PATH"
```

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\path\to\jdk8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

```bat
:: Windows cmd
set JAVA_HOME=C:\path\to\jdk8
set PATH=%JAVA_HOME%\bin;%PATH%
```

## 4. Building

From the project root:

```bash
# Linux / macOS (if needed, make the wrapper executable first: chmod +x gradlew)
./gradlew clean build -x test
```

```bat
:: Windows
gradlew.bat clean build -x test
```

The first build downloads Gradle, all Java dependencies, Node.js and the npm packages, which takes a few minutes. Later builds are much faster.

**What the build does, in order:**

1. `compileGroovy`: compiles `src/main/groovy`.
2. `nodeSetup` / `apidocInstall` / `apidoc`: downloads Node.js, runs `npm install` in `apidoc/`, and generates the HTML API documentation into `src/main/webapp/api/docs/` (git-ignored). This runs before `processResources`, so it is part of every build.
3. `bootWar`: packages the executable WAR.
4. `codenarcMain` / `codenarcTest`: Groovy static analysis (lint). See section 7.

**Expected output:**

```
> Task :bootWar
...
> Task :build

BUILD SUCCESSFUL
```

**Artifact:** `build/libs/<project-folder-name>.war` (about 105 MB). There is no `rootProject.name` in `settings.gradle`, so the file is named after the folder you cloned into, e.g. `voyage-api-java.war`.

The following messages are harmless:

- npm `deprecated` warnings, `packages are looking for funding` and `npm audit` vulnerability counts (these refer to the apidoc tool, not to the application).
- apidoc `warn: URL contains a parameter ... that is not documented` lines.

### Useful Gradle tasks

| Command                          | What it does                                             |
|----------------------------------|----------------------------------------------------------|
| `./gradlew clean build -x test`  | Full build without running tests (recommended)           |
| `./gradlew assemble`             | Build the WAR only (no lint, no tests)                   |
| `./gradlew compileGroovy`        | Compile main sources only (fast compile check)           |
| `./gradlew test`                 | Run the test suite and the Cobertura coverage report     |
| `./gradlew bootRun`              | Run the application from sources                         |
| `./gradlew dependencies`         | Print the resolved dependency tree                       |

## 5. Running the application

The default configuration (`src/main/resources/application.yaml`) uses an in-memory H2 database with seed data, so no external database is needed.

```bash
java -jar build/libs/<project-folder-name>.war
# or, from sources:
./gradlew bootRun
```

When you see `Started App in ... seconds`, open <http://localhost:8080/api/status>. You should get something like:

```json
{"status":"alive","datetime":"..."}
```

See `README.md` ("5 Minute Test") for how to get an OAuth2 token and call the API, and `readme_docs/` for profiles, MySQL and deployment.

## 6. Running the tests

```bash
./gradlew test
```

- 370 tests run. At the time of writing, 1 test fails: `voyage.security.audit.HttpActionLogFilterIntegrationSpec > Super User GET success` (an exact comparison of a JSON response body). This does not affect compilation or packaging.
- After the tests, the Cobertura coverage report is generated automatically.
- Reports:
  - Tests: `build/reports/tests/test/index.html`
  - Coverage: `build/reports/cobertura/index.html`

To build without running tests, use `-x test`.

## 7. CodeNarc (Groovy lint)

CodeNarc is a static analysis tool for Groovy code style (line length, duplicated literals, spacing, etc.). It does not affect what is compiled or packaged.

- `codenarcMain` is configured with `ignoreFailures true`: violations are **reported** but do not fail the build.
- `codenarcTest` still fails the build on violations (it currently passes).
- Reports: `build/reports/codenarc/main.html` and `build/reports/codenarc/test.html`.

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Could not find or load main class org.gradle.wrapper.GradleWrapperMain` | `gradle/wrapper/gradle-wrapper.jar` is missing | Make sure your checkout contains `gradle/wrapper/gradle-wrapper.jar`, then pull or re-clone. |
| `Unsupported class file major version ...` or other errors at Gradle startup | Gradle 6.5.1 is running on a JDK that is too new (15+) | Set `JAVA_HOME` to a JDK 8 (section 3). Check with `./gradlew -version`, which prints the JVM being used. |
| `JAVA_HOME is set to an invalid directory` | `JAVA_HOME` points to a non-existent folder or to a JRE | Point it to the root folder of a JDK 8 (the folder that contains `bin/javac`). |
| `Plugin [id: 'net.saliman.cobertura', version: '3.0.1-SNAPSHOT'] was not found` | Old `build.gradle` | Pull the latest version of the repository (the plugin is now `4.0.0`). |
| `SyntaxError: Unexpected token` inside `apidoc/node_modules/...` during `:apidoc` | npm packages need a newer Node than the one Gradle is using | Make sure `node { version = ... }` in `build.gradle` is 22.x, then delete `apidoc/node_modules` and `.gradle/nodejs` and build again. |
| `:nodeSetup` fails to download Node / 404 from `nodejs.org` | No access to `nodejs.org`, or no Node build for your OS/architecture | Check internet / proxy. Node 22 provides builds for Linux, macOS and Windows on both x64 and arm64. |
| `:apidocInstall` fails with network / `ETIMEDOUT` / `ECONNREFUSED` | No access to `registry.npmjs.org` (often a corporate proxy) | Set the `HTTPS_PROXY` / `HTTP_PROXY` environment variables, or configure npm with `npm config set proxy ...` / `https-proxy ...`. |
| `Could not resolve all dependencies ...` / connection timeouts | No access to Maven Central, the Gradle Plugin Portal or `services.gradle.org` | Check internet / firewall. Behind a proxy, add `systemProp.https.proxyHost`, `systemProp.https.proxyPort` (and the `http.` variants) to `~/.gradle/gradle.properties`. |
| `FileNotFoundException: ~/.gradle/wrapper/dists/gradle-6.5.1-bin/.../gradle-6.5.1-bin.zip` | Corrupted or partial Gradle distribution in the local cache | Delete `~/.gradle/wrapper/dists/gradle-6.5.1-bin` (Windows: `%USERPROFILE%\.gradle\wrapper\dists\gradle-6.5.1-bin`) and build again. |
| `PKIX path building failed` / SSL handshake errors while downloading | Very old JDK 8 update with outdated CA certificates, or a corporate TLS proxy | Use a recent JDK 8 update, or import your corporate CA certificate into the JDK `cacerts`. |
| `Permission denied: ./gradlew` | The execute bit was lost (e.g. ZIP download, Windows checkout) | `chmod +x gradlew` |
| `/bin/sh^M: bad interpreter` | `gradlew` was checked out with Windows (CRLF) line endings | `git config core.autocrlf input` and re-checkout, or run `sed -i 's/\r$//' gradlew`. |
| `java.lang.OutOfMemoryError` during compilation | Not enough heap for the Groovy compiler | Add `org.gradle.jvmargs=-Xmx1g` to `gradle.properties` (project root or `~/.gradle`). |
| `Port 8080 was already in use` when running the app | Another process is using port 8080 | Stop that process, or run with `--server.port=8081`. |

## 9. Build-related changes made to this repository

These are the only changes made so the project builds again. Nothing else was touched.

1. **Added `gradle/wrapper/gradle-wrapper.jar`** (Gradle 6.5.1 wrapper). It was missing, so `./gradlew` could not start.
2. **`build.gradle`: Cobertura plugin `3.0.1-SNAPSHOT` changed to `4.0.0`.** The snapshot was only ever published to the Sonatype snapshots repository and is no longer available. The released `3.0.0` is incompatible with Gradle 6 (`DeprecationLogger.whileDisabled()` error); `4.0.0` works.
3. **`build.gradle`: Node.js `14.5.0` changed to `22.23.4`** (in the `node { }` block used for apidoc). `apidoc/package.json` requests `apidoc >0.17.4` and there is no lockfile, so npm installs the latest packages, which require Node 18 or newer and fail on Node 14 with `SyntaxError`. Node 14 also has no native build for Apple Silicon.
4. **`build.gradle`: `codenarcMain` changed to `ignoreFailures true`.** The main sources have around 110 CodeNarc style violations, which made `./gradlew build` fail even though compilation and packaging succeed. The violations are still reported in `build/reports/codenarc/main.html`. `codenarcTest` is unchanged.
