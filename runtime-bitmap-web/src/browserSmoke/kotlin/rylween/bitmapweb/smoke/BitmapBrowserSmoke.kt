/**
 * Browser-managed smoke test for the bitmap web runtime.
 */

package rylween.bitmapweb.smoke

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private const val DEFAULT_BROWSER = "chromium"

fun main(args: Array<String>) {
    val options = BrowserSmokeOptions.parse(args.toList())
    BitmapBrowserSmoke(options).run()
}

private data class BrowserSmokeOptions(
    val root: Path,
    val workDir: Path,
    val reportDir: Path,
    val browserName: String,
) {
    companion object {
        fun parse(args: List<String>): BrowserSmokeOptions {
            var root = Path.of(System.getenv("RYLWEEN_BITMAP_SMOKE_ROOT") ?: ".").toAbsolutePath().normalize()
            var workDir: Path? = null
            var reportDir: Path? = null
            var browserName = System.getenv("RYLWEEN_BITMAP_SMOKE_BROWSER") ?: DEFAULT_BROWSER
            var index = 0
            while (index < args.size) {
                when (val arg = args[index]) {
                    "--root" -> {
                        root = Path.of(args.valueAfter(index, arg)).toAbsolutePath().normalize()
                        index += 2
                    }
                    "--work-dir" -> {
                        workDir = Path.of(args.valueAfter(index, arg)).toAbsolutePath().normalize()
                        index += 2
                    }
                    "--report-dir" -> {
                        reportDir = Path.of(args.valueAfter(index, arg)).toAbsolutePath().normalize()
                        index += 2
                    }
                    "--browser" -> {
                        browserName = args.valueAfter(index, arg)
                        index += 2
                    }
                    else -> error("unknown argument $arg")
                }
            }
            return BrowserSmokeOptions(
                root = root,
                workDir = workDir ?: root.resolve("runtime-bitmap-web/build/bitmap-browser-smoke"),
                reportDir = reportDir ?: root.resolve("runtime-bitmap-web/build/reports/bitmap-browser-smoke"),
                browserName = browserName,
            )
        }
    }
}

private class BitmapBrowserSmoke(
    private val options: BrowserSmokeOptions,
) {
    private val dataDir = options.workDir.resolve("data")
    private val saveFile = dataDir.resolve("saves/world.rsave")
    private val runtimeLauncher = options.root.resolve("runtime/build/install/rylween/bin/rylween").platformExecutable()
    private val bitmapLauncher =
        options.root
            .resolve(
                "runtime-bitmap-web/build/install/rylween-bitmap-web/bin/rylween-bitmap-web",
            ).platformExecutable()
    private val bundleFile = options.root.resolve("build/compiled/rylween-bundle.json")
    private val screenshotFile = options.reportDir.resolve("bitmap-first-frame.png")
    private val perspectiveScreenshotFile = options.reportDir.resolve("bitmap-wilderness-3d.png")
    private val perspectiveToolScreenshotFile = options.reportDir.resolve("bitmap-wilderness-perspective-tool.png")
    private var serverProcess: Process? = null

    fun run() {
        resetDirectories()
        try {
            seedStrongSave()
            val port = freePort()
            val baseUrl = "http://127.0.0.1:$port"
            serverProcess = startBitmapServer(port)
            waitForHealth(baseUrl)
            Playwright.create().use { playwright ->
                launchBrowser(playwright).use { browser ->
                    smokeBrowser(browser, baseUrl)
                }
            }
        } finally {
            stop(serverProcess)
        }
    }

    private fun resetDirectories() {
        options.workDir.toFile().deleteRecursively()
        options.reportDir.toFile().deleteRecursively()
        dataDir.resolve("saves").createDirectories()
        dataDir.resolve("logs/runtime").createDirectories()
        dataDir.resolve("logs/runtime-bitmap-web").createDirectories()
        options.reportDir.createDirectories()
    }

    private fun seedStrongSave() {
        runProcess(
            command =
                listOf(
                    runtimeLauncher.absolutePathString(),
                    "--bundle",
                    bundleFile.absolutePathString(),
                    "--user",
                    "Strong",
                    "--state",
                    "strong-replay",
                    "--save",
                    saveFile.absolutePathString(),
                    "--script",
                    "SX",
                    "--no-title",
                    "--log",
                    dataDir.resolve("logs/runtime/seed-strong.log").absolutePathString(),
                ),
            stdout = options.reportDir.resolve("seed.stdout.log"),
            stderr = options.reportDir.resolve("seed.stderr.log"),
        )
    }

    private fun startBitmapServer(port: Int): Process =
        ProcessBuilder(
            bitmapLauncher.absolutePathString(),
            "--host",
            "127.0.0.1",
            "--port",
            port.toString(),
            "--data",
            dataDir.absolutePathString(),
            "--state",
            "fresh-replay",
            "--map-tiles",
            "6",
            "--tile-size",
            "68",
            "--user",
            "Strong",
            "--load",
            saveFile.absolutePathString(),
            "--save",
            saveFile.absolutePathString(),
            "--no-title",
        ).apply {
            directory(options.root.toFile())
            redirectOutput(options.reportDir.resolve("server.stdout.log").toFile())
            redirectError(options.reportDir.resolve("server.stderr.log").toFile())
        }.start()

    private fun waitForHealth(baseUrl: String) {
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                val connection = URI("$baseUrl/healthz").toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 1_000
                connection.readTimeout = 1_000
                if (connection.responseCode == 200 &&
                    connection.inputStream
                        .bufferedReader()
                        .readText()
                        .trim() == "ok"
                ) {
                    return
                }
            } catch (error: Throwable) {
                lastError = error
            }
            Thread.sleep(200)
        }
        error("timed out waiting for bitmap server health: ${lastError?.message ?: "no response"}")
    }

    private fun launchBrowser(playwright: Playwright): Browser {
        val browserType =
            when (options.browserName.lowercase()) {
                "chromium" -> playwright.chromium()
                "firefox" -> playwright.firefox()
                "webkit" -> playwright.webkit()
                else -> error("unsupported browser ${options.browserName}; use chromium, firefox, or webkit")
            }
        return browserType.launch(
            BrowserType
                .LaunchOptions()
                .setHeadless(true),
        )
    }

    private fun smokeBrowser(
        browser: Browser,
        baseUrl: String,
    ) {
        val page =
            browser.newPage(
                Browser
                    .NewPageOptions()
                    .setViewportSize(1280, 720),
            )
        page.navigate("$baseUrl/play")
        page.waitForFunction(renderedFramePredicate(), null, Page.WaitForFunctionOptions().setTimeout(30_000.0))
        val frame = page.evaluate("() => window.__rylweenBitmapSmoke && window.__rylweenBitmapSmoke()").asMap()
        check(frame["wildernessFacing"] == "South") {
            "bitmap wilderness should initialize from saved party facing south: $frame"
        }
        val images = page.evaluate(compiledImageProbe()).asList()
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(screenshotFile),
        )
        smokeTownLeaveFade(page)
        val perspectiveFrame =
            page
                .evaluate("() => window.__rylweenBitmapSetWildernessView && window.__rylweenBitmapSetWildernessView('3d', 'East')")
                .asMap()
        check(perspectiveFrame["wildernessViewMode"] == "3d") {
            "bitmap wilderness display did not switch to 3D"
        }
        check((perspectiveFrame["perspectiveCells"] as? Number)?.toInt() ?: 0 > 0) {
            "bitmap wilderness 3D view did not receive perspective cells"
        }
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(perspectiveScreenshotFile),
        )
        smokePerspectiveTool(page, baseUrl)
        println(
            "bitmap browser smoke ok: " +
                "browser=${options.browserName} " +
                "mode=${frame["mode"]} " +
                "backdrop=${frame["backdrop"]} " +
                "commands=${frame["commandCount"]} " +
                "mapCells=${frame["mapCells"]} " +
                "perspectiveCells=${perspectiveFrame["perspectiveCells"]} " +
                "assets=${images.joinToString(",") { image -> image.asMap()["category"].toString() + ":" + image.asMap()["id"] }} " +
                "screenshot=${screenshotFile.absolutePathString()} " +
                "perspectiveScreenshot=${perspectiveScreenshotFile.absolutePathString()} " +
                "toolScreenshot=${perspectiveToolScreenshotFile.absolutePathString()}",
        )
    }

    private fun smokeTownLeaveFade(page: Page) {
        page.evaluate("() => window.__rylweenBitmapSetWildernessView && window.__rylweenBitmapSetWildernessView('3d', 'South')")
        page.keyboard().press("ArrowUp")
        page.waitForFunction(
            "() => window.__rylweenBitmapSmoke && window.__rylweenBitmapSmoke().y === 10",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.keyboard().press("ArrowUp")
        page.waitForFunction(
            "() => window.__rylweenBitmapSmoke && window.__rylweenBitmapSmoke().y === 11",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.keyboard().press("E")
        page.waitForFunction(
            "() => { const smoke = window.__rylweenBitmapSmoke && window.__rylweenBitmapSmoke(); " +
                "return smoke && smoke.mode === 'scene' && /towne/i.test(smoke.backdrop || ''); }",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.keyboard().press("L")
        page.waitForTimeout(250.0)
        page.keyboard().press("L")
        page.waitForFunction(
            "() => { const smoke = window.__rylweenBitmapSmoke && window.__rylweenBitmapSmoke(); " +
                "return smoke && smoke.mode === 'wilderness' && smoke.centerSettlementSceneSuppressed === true; }",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
    }

    private fun smokePerspectiveTool(
        page: Page,
        baseUrl: String,
    ) {
        page.navigate("$baseUrl/tools/wilderness-perspective")
        page.waitForFunction(toolRenderedPredicate(), null, Page.WaitForFunctionOptions().setTimeout(30_000.0))
        val initial =
            page
                .evaluate(
                    "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool()",
                ).asMap()
        check(initial["x"].asInt() == 12 && initial["y"].asInt() == 10) {
            "wilderness perspective tool must start outside Thord at 12x10: $initial"
        }
        check(initial["facing"] == "South" && initial["mode"] == "3d") {
            "wilderness perspective tool must start facing south in 3D mode: $initial"
        }
        check(initial["frontTile"] == "town" && initial["visibleSettlements"].asList().contains("town")) {
            "wilderness perspective tool must show Thord when facing south from 12x10: $initial"
        }
        page.keyboard().press("ArrowRight")
        page.waitForFunction(
            "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool().facing === 'West'",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.keyboard().press("ArrowLeft")
        page.waitForFunction(
            "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool().facing === 'South'",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.keyboard().press("ArrowUp")
        page.waitForFunction(
            "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool().y === 11",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.keyboard().press("ArrowDown")
        page.waitForFunction(
            "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool().y === 10",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.keyboard().press("8")
        page.waitForFunction(
            "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool().y === 11",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.keyboard().press("ArrowRight")
        page.waitForFunction(
            "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool().facing === 'West'",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.keyboard().press("2")
        val afterInput =
            page
                .evaluate(
                    "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool()",
                ).asMap()
        check(
            afterInput["x"].asInt() == 12 && afterInput["y"].asInt() == 11 && afterInput["facing"] == "West" && afterInput["mode"] == "2d",
        ) {
            "wilderness perspective tool controls failed: $afterInput"
        }
        page.keyboard().press("3")
        page.waitForFunction(
            "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool().mode === '3d'",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.evaluate("() => window.__rylweenWildernessPerspectiveSet && window.__rylweenWildernessPerspectiveSet(12, 10, 'South', '3d')")
        page.waitForFunction(
            "() => window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool().frontTile === 'town'",
            null,
            Page.WaitForFunctionOptions().setTimeout(5_000.0),
        )
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(perspectiveToolScreenshotFile),
        )
    }

    private fun renderedFramePredicate(): String =
        """
        () => {
          const smoke = window.__rylweenBitmapSmoke && window.__rylweenBitmapSmoke();
          const canvas = document.getElementById("game");
          if (!smoke || !(canvas instanceof HTMLCanvasElement)) return false;
          const context = canvas.getContext("2d");
          if (!context || canvas.width < 1200 || canvas.height < 700) return false;
          const data = context.getImageData(0, 0, canvas.width, canvas.height).data;
          const step = Math.max(4, Math.floor(data.length / 8000 / 4) * 4);
          let nonBlank = 0;
          const colors = new Set();
          for (let index = 0; index < data.length; index += step) {
            const r = data[index];
            const g = data[index + 1];
            const b = data[index + 2];
            const a = data[index + 3];
            if (a > 8 && r + g + b > 18) {
              nonBlank += 1;
              colors.add(r + "," + g + "," + b + "," + a);
            }
          }
          return smoke.mode && smoke.mode !== "boot" &&
            (smoke.mapCells > 0 || smoke.textLineCount > 0) &&
            smoke.commandCount > 0 &&
            smoke.terrainTransitionAssetsReady === true &&
            nonBlank > 300 &&
            colors.size > 20;
        }
        """.trimIndent()

    private fun compiledImageProbe(): String =
        """
        async () => {
          const smoke = window.__rylweenBitmapSmoke && window.__rylweenBitmapSmoke();
          if (!smoke || smoke.compiledImageAssets.length === 0) {
            throw new Error("no compiled bitmap image metadata reached the browser");
          }
          const selectors = [
            {category: "service", pattern: /scene-(smith|merchant|inn|tavern|church)\.png${'$'}/},
            {category: "monster", pattern: /monster-goblinoid\.png${'$'}/},
            {category: "dungeon", pattern: /scene-dungeon-(chest|trap)\.png${'$'}/},
            {category: "castle", pattern: /scene-(ydarg-castle|jaara-gate|castle-stairs-guardians)\.png${'$'}/}
          ];
          const chosen = selectors.map((selector) => {
            const asset = smoke.compiledImageAssets.find((candidate) => selector.pattern.test(candidate.resource));
            if (!asset) throw new Error("missing compiled bitmap asset for " + selector.category);
            return {...asset, category: selector.category};
          });
          async function sample(asset) {
            const image = new Image();
            image.src = "/assets/game-image/" + encodeURIComponent(asset.id) + "?smoke=" + Date.now();
            await new Promise((resolve, reject) => {
              image.addEventListener("load", resolve, {once: true});
              image.addEventListener("error", () => reject(new Error("failed to load " + asset.id)), {once: true});
            });
            const canvas = document.createElement("canvas");
            canvas.width = image.naturalWidth;
            canvas.height = image.naturalHeight;
            const context = canvas.getContext("2d");
            if (!context) throw new Error("missing 2d context for " + asset.id);
            context.drawImage(image, 0, 0);
            const data = context.getImageData(0, 0, canvas.width, canvas.height).data;
            const step = Math.max(4, Math.floor(data.length / 12000 / 4) * 4);
            let nonBlank = 0;
            for (let index = 0; index < data.length; index += step) {
              const r = data[index];
              const g = data[index + 1];
              const b = data[index + 2];
              const a = data[index + 3];
              if (a > 8 && r + g + b > 18) nonBlank += 1;
            }
            if (canvas.width < 32 || canvas.height < 32 || nonBlank < 100) {
              throw new Error("blank or tiny compiled bitmap asset " + asset.id);
            }
            return {category: asset.category, id: asset.id, resource: asset.resource, width: canvas.width, height: canvas.height, nonBlank};
          }
          return Promise.all(chosen.map(sample));
        }
        """.trimIndent()

    private fun toolRenderedPredicate(): String =
        """
        () => {
          const smoke = window.__rylweenWildernessPerspectiveTool && window.__rylweenWildernessPerspectiveTool();
          const canvas = document.getElementById("wildernessTool");
          if (!smoke || !(canvas instanceof HTMLCanvasElement)) return false;
          const context = canvas.getContext("2d");
          if (!context || smoke.cells <= 0 || smoke.mapCells <= 0 || !smoke.terrainAssetsReady) return false;
          const data = context.getImageData(0, 0, canvas.width, canvas.height).data;
          const step = Math.max(4, Math.floor(data.length / 5000 / 4) * 4);
          let nonBlank = 0;
          const colors = new Set();
          for (let index = 0; index < data.length; index += step) {
            const r = data[index];
            const g = data[index + 1];
            const b = data[index + 2];
            const a = data[index + 3];
            if (a > 8 && r + g + b > 18) {
              nonBlank += 1;
              colors.add(r + "," + g + "," + b + "," + a);
            }
          }
          return nonBlank > 180 && colors.size > 18;
        }
        """.trimIndent()
}

private fun runProcess(
    command: List<String>,
    stdout: Path,
    stderr: Path,
) {
    val process =
        ProcessBuilder(command)
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start()
    val finished = process.waitFor(Duration.ofSeconds(30).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
    if (!finished) {
        process.destroyForcibly()
        error("process timed out: ${command.joinToString(" ")}")
    }
    check(process.exitValue() == 0) {
        "process failed (${process.exitValue()}): ${command.joinToString(" ")}\n" +
            "stdout=${stdout.absolutePathString()}\nstderr=${stderr.absolutePathString()}"
    }
}

private fun freePort(): Int = ServerSocket(0).use { socket -> socket.localPort }

private fun stop(process: Process?) {
    if (process == null || !process.isAlive) return
    process.destroy()
    if (!process.waitFor(Duration.ofSeconds(3).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
    }
}

private fun List<String>.valueAfter(
    index: Int,
    option: String,
): String = getOrNull(index + 1) ?: error("$option requires a value")

private fun Path.platformExecutable(): Path =
    if (isWindows() && !exists()) {
        resolveSibling(fileName.toString() + ".bat")
    } else {
        this
    }

private fun isWindows(): Boolean =
    System
        .getProperty("os.name")
        .lowercase()
        .contains("windows")

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: error("expected map, got ${this?.javaClass?.name ?: "null"}")

private fun Any?.asList(): List<Any?> = this as? List<Any?> ?: error("expected list, got ${this?.javaClass?.name ?: "null"}")

private fun Any?.asInt(): Int = (this as? Number)?.toInt() ?: error("expected number, got ${this?.javaClass?.name ?: "null"}")
