/** Loads the bitmap runtime HTML shell from packaged resources. */

package rylween.bitmapweb

private const val BITMAP_PAGE_RESOURCE = "bitmap-web/index.html"

internal fun bitmapPageHtml(): String =
    checkNotNull(Thread.currentThread().contextClassLoader.getResource(BITMAP_PAGE_RESOURCE)) {
        "missing bitmap web page resource $BITMAP_PAGE_RESOURCE"
    }.readText()
