package com.racunko.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racunko.app.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** How wide a page is rasterised. Roughly 2× a phone screen, so text survives zoom. */
private const val PAGE_PX = 1600

/** A very long document is not what this is for; a bill is one or two pages. */
private const val MAX_PAGES = 12

private const val ZOOMED = 2.5f

/**
 * v1.7.2: the document itself, full screen, INSIDE the app.
 *
 * A card can say „adresa?" or „iznos?" and until now there was no way to see WHICH
 * bill that is without handing the file to some other app. That is the one moment
 * a person most needs to look at the paper — to correct a reading the app could not
 * make — so the look must not cost them the app they are standing in.
 *
 * Rendering is `PdfRenderer`, which is platform API and therefore identical in both
 * flavors: no new dependency, and nothing here knows about ML Kit or ZXing. Pages
 * are rasterised ONE AT A TIME as they scroll into view, so a multi-page document
 * costs a couple of bitmaps rather than all of them at once.
 *
 * Zoom is a DOUBLE TAP rather than a pinch, deliberately. Pinch handling and the
 * list's own vertical scrolling fight over the same single-finger drag, and the
 * loser is always the scroll. Double tap keeps both gestures unambiguous: at 1× the
 * drag scrolls the pages, zoomed in it pans them.
 */
@Composable
fun DocumentViewer(doc: ViewedDoc, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = remember(doc.uri) { Uri.parse(doc.uri) }
    val isPdf = doc.name.endsWith(".pdf", ignoreCase = true)

    var scale by remember(doc.uri) { mutableFloatStateOf(1f) }
    var offset by remember(doc.uri) { mutableStateOf(Offset.Zero) }
    val zoomed = scale > 1f

    BackHandler {
        if (zoomed) {
            scale = 1f; offset = Offset.Zero
        } else onDismiss()
    }

    val pageCount by produceState(-1, doc.uri) {
        value = if (!isPdf) 1 else withContext(Dispatchers.IO) { pdfPageCount(context, uri) }
    }

    Surface(color = Palette.Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    doc.name,
                    color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (zoomed) {
                    Text(
                        stringResource(R.string.viewer_reset),
                        color = Palette.Blue, fontSize = 12.sp,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { scale = 1f; offset = Offset.Zero }
                            }
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(RIcons.Close, stringResource(R.string.btn_otkazi), tint = Palette.Muted)
                }
            }

            Box(Modifier.fillMaxSize()) {
                when (pageCount) {
                    -1 -> CircularProgressIndicator(
                        Modifier.align(Alignment.Center), color = Palette.Blue
                    )
                    0 -> Text(
                        stringResource(R.string.viewer_failed),
                        color = Palette.Amber, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    else -> {
                        val gestures = Modifier
                            .pointerInput(doc.uri) {
                                detectTapGestures(
                                    onDoubleTap = { tap ->
                                        if (scale > 1f) {
                                            scale = 1f; offset = Offset.Zero
                                        } else {
                                            scale = ZOOMED
                                            // Keep what was tapped roughly under the finger.
                                            offset = Offset(
                                                (size.width / 2f - tap.x) * (ZOOMED - 1f),
                                                (size.height / 2f - tap.y) * (ZOOMED - 1f)
                                            )
                                        }
                                    }
                                )
                            }
                            .let { base ->
                                // Panning only exists while zoomed; at 1× the very same
                                // drag has to reach the list, or the pages stop scrolling.
                                if (!zoomed) base else base.pointerInput(doc.uri) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        offset += drag
                                    }
                                }
                            }

                        LazyColumn(
                            modifier = gestures
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale; scaleY = scale
                                    translationX = offset.x; translationY = offset.y
                                },
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            userScrollEnabled = !zoomed
                        ) {
                            items((0 until minOf(pageCount, MAX_PAGES)).toList()) { index ->
                                PageImage(doc.uri, uri, index, isPdf)
                            }
                            if (pageCount > MAX_PAGES) {
                                item {
                                    Text(
                                        stringResource(R.string.viewer_truncated, MAX_PAGES, pageCount),
                                        color = Palette.Dim, fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One page, rasterised when it scrolls into view and dropped when it leaves. */
@Composable
private fun PageImage(key: String, uri: Uri, index: Int, isPdf: Boolean) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, key, index) {
        value = withContext(Dispatchers.IO) {
            if (isPdf) renderPdfPage(context, uri, index) else loadImage(context, uri)
        }
    }
    val bmp = bitmap
    if (bmp == null) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.width(28.dp).height(28.dp), color = Palette.Dim)
        }
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().background(AndroidWhite)
        )
    }
}

private val AndroidWhite = androidx.compose.ui.graphics.Color.White

private fun openDescriptor(
    context: android.content.Context,
    uri: Uri
): ParcelFileDescriptor? = runCatching {
    if (uri.scheme == "file") {
        ParcelFileDescriptor.open(
            java.io.File(requireNotNull(uri.path)), ParcelFileDescriptor.MODE_READ_ONLY
        )
    } else {
        context.contentResolver.openFileDescriptor(uri, "r")
    }
}.getOrNull()

/** Page count, or 0 when the document cannot be opened at all. */
private fun pdfPageCount(context: android.content.Context, uri: Uri): Int =
    runCatching {
        openDescriptor(context, uri)?.use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        } ?: 0
    }.getOrDefault(0)

private fun renderPdfPage(context: android.content.Context, uri: Uri, index: Int): Bitmap? =
    runCatching {
        openDescriptor(context, uri)?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (index >= renderer.pageCount) return@use null
                renderer.openPage(index).use { page ->
                    val scale = PAGE_PX.toFloat() / page.width
                    val w = ceil(page.width * scale).toInt()
                    val h = ceil(page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    // A PDF page is transparent where it is blank; on the app's dark
                    // background that renders as unreadable text on black.
                    Canvas(bmp).drawColor(AndroidColor.WHITE)
                    page.render(
                        bmp, null, Matrix().apply { setScale(scale, scale) },
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    bmp
                }
            }
        }
    }.getOrNull()

private fun loadImage(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 1 }
        android.graphics.BitmapFactory.decodeStream(input, null, opts)
    }
}.getOrNull()
