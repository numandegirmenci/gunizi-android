package com.numandegirmenci.belgeizi.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.numandegirmenci.belgeizi.data.*
import com.numandegirmenci.belgeizi.engine.OcrEngine
import com.numandegirmenci.belgeizi.engine.PdfWriter
import com.numandegirmenci.belgeizi.engine.ScanPipelinePolicy
import com.numandegirmenci.belgeizi.engine.VisualTextRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
fun ScanApp(activity: ComponentActivity, pendingUris: List<Uri>, externalEdit: Boolean = false,
    onConsumed: () -> Unit) {
    val context = LocalContext.current
    val store = remember { DocumentStore(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var documents by remember { mutableStateOf(store.all()) }
    var current by remember { mutableStateOf<ScanDocument?>(null) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var signatureOpen by remember { mutableStateOf(false) }
    var stampOpen by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<ScanDocument?>(null) }

    BackHandler(enabled = current != null) {
        current = null
        documents = store.all()
    }

    fun reportError(t: Throwable) {
        scope.launch { snackbar.showSnackbar(t.message ?: "İşlem gerçekleştirilemedi.") }
    }
    fun update(doc: ScanDocument) {
        current = doc
        documents = documents.map { if (it.id == doc.id) doc else it }
        // Small JSON metadata is written immediately so edits survive activity recreation.
        runCatching { store.save(doc) }.onFailure(::reportError)
    }
    fun changePage(change: (DocumentPage) -> DocumentPage) {
        val doc = current ?: return
        val index = pageIndex.coerceIn(0, doc.pages.lastIndex)
        val updatedPages = doc.pages.toMutableList()
        updatedPages[index] = change(updatedPages[index])
        update(doc.copy(pages = updatedPages))
    }
    suspend fun processOcr(doc: ScanDocument, force: Boolean = false) {
        var latest = doc
        for (i in latest.pages.indices) {
            val page = latest.pages[i]
            if (!ScanPipelinePolicy.shouldRecognize(page.ocrDone, page.ocrVersion,
                    page.ocr.any { it.edited }, force)) continue
            status = "Belge hazırlanıyor • " + (i + 1) + "/" + latest.pages.size
            val lines = withContext(Dispatchers.Default) {
                val bitmap = store.loadBitmap(page)
                try { OcrEngine.recognize(context, bitmap) }
                finally { bitmap.recycle() }
            }
            val pages = latest.pages.toMutableList()
            pages[i] = page.copy(ocr = lines, ocrDone = true, ocrVersion = ScanPipelinePolicy.OCR_SCHEMA_VERSION)
            latest = latest.copy(pages = pages)
            update(latest)
        }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty() || busy) return
        scope.launch {
            busy = true; status = "Belgeler aktarılıyor..."
            try {
                val doc = withContext(Dispatchers.IO) { store.import(uris) }
                documents = store.all(); current = doc; pageIndex = 0
                processOcr(doc)
                status = "Belge hazır"
            } catch (e: Exception) { reportError(e) }
            finally { busy = false }
        }
    }

    val cameraResult = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanned = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scanned?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) importFiles(uris)
            else reportError(IllegalStateException("Tarama sonucunda sayfa bulunamadı."))
        }
    }
    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        importFiles(uris)
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val doc = current
        if (uri != null && doc != null) {
            scope.launch {
                busy = true; status = "PDF oluşturuluyor..."
                try {
                    withContext(Dispatchers.IO) { PdfWriter.export(doc, context.contentResolver, uri) }
                    if (externalEdit) {
                        activity.setResult(Activity.RESULT_OK, Intent().apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }
                    snackbar.showSnackbar("PDF kaydedildi. Orijinal dosya korundu.")
                } catch (e: Exception) { reportError(e) }
                finally { busy = false }
            }
        }
    }
    fun sharePdf(doc: ScanDocument) {
        if (busy) return
        scope.launch {
            busy = true; status = "Paylaşılacak PDF hazırlanıyor..."
            try {
                val uri = withContext(Dispatchers.IO) {
                    val folder = File(context.cacheDir, "shared_documents").apply { mkdirs() }
                    // Some target apps may import the shared file asynchronously.
                    folder.listFiles()?.filter {
                        it.isFile && System.currentTimeMillis() - it.lastModified() > 7L * 24 * 60 * 60 * 1000
                    }?.forEach { it.delete() }
                    val file = File(folder, "BelgeIzi_${System.currentTimeMillis()}.pdf")
                    file.createNewFile()
                    try {
                        val result = FileProvider.getUriForFile(
                            context, "${context.packageName}.files", file
                        )
                        PdfWriter.export(doc, context.contentResolver, result)
                        result
                    } catch (error: Exception) {
                        file.delete()
                        throw error
                    }
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Belge İzi PDF", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Düzenlenmiş PDF'yi paylaş"))
            } catch (error: Exception) { reportError(error) }
            finally { busy = false; status = "" }
        }
    }

    val stampImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) { store.copyMarkImage(uri) }
                    changePage { p -> p.copy(marks = p.marks + PageMark(
                        type = MarkType.IMAGE_STAMP, assetPath = file
                    )) }
                    snackbar.showSnackbar("Kaşe eklendi. Üzerine basıp taşıyabilirsin.")
                } catch (e: Exception) { reportError(e) }
            }
        }
    }

    fun scan() {
        if (busy) return
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(40)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(opts).getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                cameraResult.launch(IntentSenderRequest.Builder(sender).build())
            }.addOnFailureListener(::reportError)
    }
    fun recognizeAll() {
        val doc = current ?: return
        if (busy) return
        scope.launch {
            busy = true
            try {
                processOcr(doc, force = true)
                snackbar.showSnackbar("Metin tanıma yenilendi; düzenlemelerin korundu.")
            } catch (e: Exception) { reportError(e) }
            finally { busy = false; status = "" }
        }
    }

    LaunchedEffect(pendingUris, busy) {
        if (pendingUris.isNotEmpty() && !busy) {
            importFiles(pendingUris)
            onConsumed()
        }
    }

    BelgeTheme {
        Scaffold(
            containerColor = Background,
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (current == null) {
                    HomeScreen(
                        docs = documents, onScan = ::scan,
                        onImport = { openFiles.launch(arrayOf("application/pdf", "image/*")) },
                        onOpen = { doc ->
                            current = doc; pageIndex = 0
                            if (doc.pages.any { ScanPipelinePolicy.shouldRecognize(it.ocrDone, it.ocrVersion,
                                    it.ocr.any { word -> word.edited }) } && !busy) scope.launch {
                                busy = true
                                try { processOcr(doc) } catch (e: Exception) { reportError(e) }
                                finally { busy = false; status = "" }
                            }
                        },
                        onDelete = { deleteCandidate = it }
                    )
                } else {
                    EditorScreen(
                        doc = current!!, pageIndex = pageIndex, externalEdit = externalEdit,
                        onPageIndex = { pageIndex = it },
                        onBack = { current = null; documents = store.all() },
                                                onPageChanged = ::changePage,
                        onExport = { doc -> savePdf.launch("${doc.title.take(70)}.pdf") },
                        onShare = ::sharePdf,
                        onSignature = { signatureOpen = true },
                        onStamp = { stampOpen = true },
                        onImageStamp = { stampImagePicker.launch(arrayOf("image/*")) },
                        onEnhance = { mode ->
                            current?.let { doc -> if (!busy) scope.launch {
                                busy = true; status = "Belge geliştiriliyor..."
                                try {
                                    val index = pageIndex.coerceIn(0, doc.pages.lastIndex)
                                    val refreshed = withContext(Dispatchers.Default) {
                                        store.reprocessPage(doc.pages[index], mode)
                                    }
                                    val pages = doc.pages.toMutableList()
                                    pages[index] = refreshed
                                    val updated = doc.copy(pages = pages)
                                    update(updated)
                                    if (!refreshed.ocrDone) processOcr(updated)
                                } catch (e: Exception) { reportError(e) }
                                finally { busy = false; status = "" }
                            } }
                        }
                    )
                }
                if (busy) {
                    Box(Modifier.fillMaxSize().background(Background.copy(alpha = 0.83f)),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Accent)
                            Spacer(Modifier.height(20.dp))
                            Text(status, color = TextMain, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        if (signatureOpen) SignatureDialog(onDismiss = { signatureOpen = false }) { bitmap ->
            try {
                val file = store.storeSignature(bitmap)
                changePage { page -> page.copy(marks = page.marks + PageMark(
                    type = MarkType.SIGNATURE, assetPath = file, h = 0.13f
                )) }
                signatureOpen = false
            } catch (e: Exception) { reportError(e) }
            finally { bitmap.recycle() }
        }
        if (stampOpen) {
            var text by remember { mutableStateOf("ONAYLANDI") }
            AlertDialog(
                onDismissRequest = { stampOpen = false },
                title = { Text("Metin kaşesi") },
                text = {
                    OutlinedTextField(value = text, onValueChange = { text = it.take(44) },
                        label = { Text("Kaşe yazısı") }, singleLine = true)
                },
                confirmButton = { Button(enabled = text.isNotBlank(), onClick = {
                    changePage { page -> page.copy(marks = page.marks + PageMark(
                        type = MarkType.TEXT_STAMP, text = text.trim(), h = 0.09f
                    )) }
                    stampOpen = false
                }) { Text("Kaşeyi ekle") } },
                dismissButton = { TextButton(onClick = { stampOpen = false }) { Text("Vazgeç") } }
            )
        }
        deleteCandidate?.let { candidate ->
            AlertDialog(onDismissRequest = { deleteCandidate = null },
                title = { Text("Belge silinsin mi?") },
                text = { Text("${candidate.title} ve içindeki sayfalar kalıcı olarak silinecek.") },
                confirmButton = { Button(onClick = {
                    store.delete(candidate)
                    documents = store.all()
                    deleteCandidate = null
                }) { Text("Sil") } },
                dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("İptal") } }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    docs: List<ScanDocument>, onScan: () -> Unit, onImport: () -> Unit,
    onOpen: (ScanDocument) -> Unit, onDelete: (ScanDocument) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(docs, query) {
        docs.filter { it.title.contains(query, ignoreCase = true) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 21.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AutoStories, "Belge İzi", tint = Background,
                        modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Belge İzi", color = TextMain, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                    Text("Dijital belge atölyen", color = TextMuted, fontSize = 12.sp)
                }
                Surface(color = Panel, shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(1.dp, PanelBright)) {
                    Text("YEREL", color = Accent, fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Column {
                Text("Belgelerin. Daha net.", color = TextMain,
                    fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.7).sp)
                Spacer(Modifier.height(7.dp))
                Text("Tara, otomatik iyileştir, düzenle ve paylaş.",
                    color = TextMuted, fontSize = 14.sp)
            }
        }
        item {
            Surface(onClick = onScan, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(27.dp), color = Accent) {
                Box(Modifier.fillMaxWidth().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF6AE8C6), Color(0xFF31B7B4))
                    )
                )) {
                    Column(Modifier.padding(23.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                                .background(Background.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.PhotoCamera, null, tint = Background,
                                    modifier = Modifier.size(31.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Outlined.ArrowForward, null, tint = Background,
                                modifier = Modifier.size(25.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Belge tara", color = Background,
                            fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(5.dp))
                        Text("Otomatik kesme  •  Netleştirme  •  Metin algılama",
                            color = Background.copy(alpha = 0.82f), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Surface(onClick = onImport, shape = RoundedCornerShape(22.dp),
                color = Panel, border = BorderStroke(1.dp, PanelBright),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(45.dp).clip(RoundedCornerShape(15.dp))
                        .background(PanelBright), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.FolderOpen, null, tint = Accent,
                            modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dosyadan belge aç", color = TextMain,
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("PDF, fotoğraf veya bulut dosyası",
                            color = TextMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted)
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Belgelerim", color = TextMain,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Cihazında güvenle saklanır", color = TextMuted, fontSize = 12.sp)
                }
                Surface(color = PanelBright, shape = RoundedCornerShape(50.dp)) {
                    Text("${docs.size} belge", color = TextMain, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        if (docs.isNotEmpty()) item {
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Belge adına göre ara", color = TextMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextMuted) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, "Aramayı temizle")
                    }
                },
                shape = RoundedCornerShape(18.dp), singleLine = true
            )
        }
        if (results.isEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = Panel,
                border = BorderStroke(1.dp, PanelBright),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (docs.isEmpty()) Icons.Outlined.InsertDriveFile else Icons.Outlined.SearchOff,
                        null, tint = Accent, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(if (docs.isEmpty()) "İlk belgeni tara" else "Eşleşen belge bulunamadı",
                        color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(if (docs.isEmpty()) "Belgelerin burada listelenecek."
                        else "Başka bir belge adı deneyebilirsin.",
                        color = TextMuted, fontSize = 12.sp)
                }
            }
        }
        items(results, key = { it.id }) { doc ->
            Surface(onClick = { onOpen(doc) }, shape = RoundedCornerShape(21.dp),
                color = Panel, border = BorderStroke(1.dp, PanelBright)) {
                Row(Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(width = 59.dp, height = 65.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PanelBright),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Description, null, tint = Accent,
                            modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(doc.title, color = TextMain,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text("${doc.pages.size} sayfa  ·  ${SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR")).format(Date(doc.createdAt))}",
                            color = TextMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onDelete(doc) }) {
                        Icon(Icons.Outlined.DeleteOutline, "Belgeyi sil", tint = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(
    doc: ScanDocument, pageIndex: Int, externalEdit: Boolean,
    onPageIndex: (Int) -> Unit,
    onBack: () -> Unit,
    onPageChanged: ((DocumentPage) -> DocumentPage) -> Unit,
    onExport: (ScanDocument) -> Unit,
    onShare: (ScanDocument) -> Unit,
    onSignature: () -> Unit, onStamp: () -> Unit, onImageStamp: () -> Unit,
    onEnhance: (String) -> Unit
) {
    val index = pageIndex.coerceIn(0, doc.pages.lastIndex)
    val page = doc.pages[index]
    val context = LocalContext.current
    var textEditing by remember { mutableStateOf(false) }
    var selectedLine by remember { mutableStateOf<OcrLine?>(null) }
    var selectedMark by remember { mutableStateOf<PageMark?>(null) }
    var stampChoices by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var filterChoices by remember { mutableStateOf(false) }
    val displayMode = when (page.enhanceMode) {
        "AUTO" -> "Otomatik"
        "VIVID" -> "Canlı renk"
        "BW" -> "Siyah-beyaz"
        else -> "Orijinal"
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = Panel, shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 7.dp, end = 13.dp, top = 9.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Belgelerime dön", tint = TextMain)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(doc.title, color = TextMain, fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (externalEdit) "Dışarıdan açıldı · Orijinal korunur"
                            else "Düzenleme alanı", color = TextMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onShare(doc) }) {
                        Icon(Icons.Outlined.IosShare, "PDF'yi paylaş", tint = Accent,
                            modifier = Modifier.size(23.dp))
                    }
                    Button(
                        onClick = { onExport(doc) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Kaydet", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 19.dp, end = 12.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = PanelBright, shape = RoundedCornerShape(50.dp)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoFixHigh, null, tint = Accent,
                                modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(displayMode, color = TextMain, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(enabled = index > 0, onClick = { onPageIndex(index - 1) },
                        modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.ChevronLeft, "Önceki sayfa",
                            tint = if (index > 0) TextMain else TextMuted.copy(alpha = .4f))
                    }
                    Text("${index + 1} / ${doc.pages.size}", color = TextMain,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 5.dp))
                    IconButton(enabled = index < doc.pages.lastIndex,
                        onClick = { onPageIndex(index + 1) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.ChevronRight, "Sonraki sayfa",
                            tint = if (index < doc.pages.lastIndex) TextMain else TextMuted.copy(alpha = .4f))
                    }
                }
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF26313C))
                .border(1.dp, PanelBright, RoundedCornerShape(22.dp))
        ) {
            DocumentCanvas(page = page, showOcr = textEditing,
                onLineSelected = { selectedLine = it },
                onMarkSelected = { selectedMark = it },
                onMoveMark = { changed -> onPageChanged { p ->
                    p.copy(marks = p.marks.map { if (it.id == changed.id) changed else it })
                } })
            if (textEditing) Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                shape = RoundedCornerShape(50.dp), color = Panel
            ) {
                Text("Değiştirmek istediğin kelimeye dokun",
                    color = Accent, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
        Surface(
            color = Panel, shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Düzenleme araçları", color = TextMain,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Tüm değişiklikler kaydedilir", color = TextMuted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTool(Icons.Outlined.AutoFixHigh, "Geliştir",
                        { filterChoices = true })
                    ActionTool(Icons.Outlined.TextFields, "Metin",
                        { textEditing = !textEditing }, textEditing)
                    ActionTool(Icons.Outlined.Draw, "İmza", onSignature)
                    ActionTool(Icons.Outlined.Approval, "Kaşe", { stampChoices = true })
                    ActionTool(Icons.Outlined.ContentCopy, "Kopyala", { showText = true })
                }
            }
        }
    }

    if (filterChoices) AlertDialog(onDismissRequest = { filterChoices = false },
        title = { Text("Belge görünümü") },
        text = { Column {
            listOf("Otomatik geliştir" to "AUTO", "Canlı renk" to "VIVID",
                "Keskin siyah-beyaz" to "BW", "Orijinal" to "ORIGINAL")
                .forEach { (name, mode) ->
                    TextButton(onClick = { filterChoices = false; onEnhance(mode) }) {
                        Text(name)
                    }
                }
        } },
        confirmButton = { TextButton(onClick = { filterChoices = false }) { Text("Kapat") } }
    )

    selectedLine?.let { line ->
        var edited by remember(line.id) { mutableStateOf(line.text) }
        AlertDialog(onDismissRequest = { selectedLine = null },
            title = { Text("Metni düzenle") },
            text = {
                Column {
                    Text("Okunan: ${line.original}", color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = edited, onValueChange = { edited = it },
                        label = { Text("Belgedeki yeni metin") }, minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Text("Yazı rengi ve arka plan tahmini eşleştirilir; karmaşık zeminlerde iz kalabilir.",
                        color = TextMuted, fontSize = 11.sp)
                }
            },
            confirmButton = { Button(onClick = {
                onPageChanged { p -> p.copy(ocr = p.ocr.map {
                    if (it.id == line.id) it.copy(text = edited) else it
                }) }
                selectedLine = null
            }) { Text("Uygula") } },
            dismissButton = { TextButton(onClick = { selectedLine = null }) { Text("İptal") } }
        )
    }
    selectedMark?.let { mark ->
        var size by remember(mark.id) { mutableFloatStateOf(mark.w) }
        AlertDialog(onDismissRequest = { selectedMark = null },
            title = { Text(if (mark.type == MarkType.SIGNATURE) "İmza" else "Kaşe") },
            text = { Column {
                Text("İşareti sürükleyerek taşıyabilirsin.", color = TextMuted)
                Spacer(Modifier.height(16.dp))
                Text("Boyut: %${(size * 100).toInt()}")
                Slider(value = size, onValueChange = { size = it }, valueRange = 0.12f..0.85f)
            } },
            confirmButton = { Button(onClick = {
                onPageChanged { p -> p.copy(marks = p.marks.map { if (it.id == mark.id)
                    it.copy(w = size, h = (it.h * (size / it.w)).coerceIn(0.03f, 0.50f)) else it }) }
                selectedMark = null
            }) { Text("Boyutu kaydet") } },
            dismissButton = {
                TextButton(onClick = {
                    onPageChanged { p -> p.copy(marks = p.marks.filterNot { it.id == mark.id }) }
                    selectedMark = null
                }) { Text("Kaldır", color = Danger) }
            }
        )
    }
    if (stampChoices) AlertDialog(onDismissRequest = { stampChoices = false },
        title = { Text("Kaşe ekle") },
        text = { Text("Metin kaşesi oluşturabilir veya kendi kaşe görselini dosyadan seçebilirsin.") },
        confirmButton = { Button(onClick = { stampChoices = false; onStamp() }) { Text("Metin kaşesi") } },
        dismissButton = { TextButton(onClick = { stampChoices = false; onImageStamp() }) {
            Text("Görsel seç")
        } }
    )
    if (showText) {
        val allText = doc.pages.joinToString("\n\n") { p -> p.ocr.joinToString("\n") { it.text } }
        AlertDialog(onDismissRequest = { showText = false },
            title = { Text("Belgedeki metin") },
            text = { Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(allText.ifBlank { "Belge metni hazırlanıyor." })
            } },
            confirmButton = { TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Belge metni", allText))
                showText = false
            }) { Text("Tümünü kopyala") } },
            dismissButton = { TextButton(onClick = { showText = false }) { Text("Kapat") } }
        )
    }
}

@Composable
private fun ActionTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String, action: () -> Unit, selected: Boolean = false
) {
    Surface(onClick = action, color = if (selected) Accent else Panel,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(min = 58.dp)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(22.dp),
                tint = if (selected) Background else TextMain)
            Spacer(Modifier.height(5.dp))
            Text(text, color = if (selected) Background else TextMuted,
                fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DocumentCanvas(
    page: DocumentPage,
    showOcr: Boolean,
    onLineSelected: (OcrLine) -> Unit,
    onMarkSelected: (PageMark) -> Unit,
    onMoveMark: (PageMark) -> Unit
) {
    val revision = page.ocr.filter { it.edited }.joinToString("|") { it.id + ":" + it.text }
    val bitmap by produceState<Bitmap?>(initialValue = null, page.path, revision, page.enhanceMode) {
        value = withContext(Dispatchers.Default) {
            val raw = BitmapFactory.decodeFile(page.path)
            if (raw != null && page.ocr.any { it.edited }) {
                val rendered = VisualTextRenderer.render(raw, page.ocr)
                if (rendered !== raw) raw.recycle()
                rendered
            } else raw
        }
    }
    val image = bitmap
    if (image == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }
    val density = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = min(maxWidth.value / page.width, maxHeight.value / page.height)
        val displayW = page.width * scale
        val displayH = page.height * scale
        val left = (maxWidth.value - displayW) / 2f
        val top = (maxHeight.value - displayH) / 2f
        Image(image.asImageBitmap(), contentDescription = "Taranmış belge", contentScale = ContentScale.FillBounds,
            modifier = Modifier.offset(left.dp, top.dp).size(displayW.dp, displayH.dp))
        if (showOcr) page.ocr.forEach { line ->
            val x = left + line.x * displayW
            val y = top + line.y * displayH
            val width = (line.w * displayW).coerceAtLeast(14f)
            val height = (line.h * displayH).coerceAtLeast(13f)
            Box(Modifier.offset(x.dp, y.dp).size(width.dp, height.dp)
                .background((if (line.confidence in 0.001f..0.719f) Danger else Accent).copy(alpha = 0.12f))
                .border(1.dp, if (line.confidence in 0.001f..0.719f) Danger else Accent)
                .clickable { onLineSelected(line) })
        }
        page.marks.forEach { mark ->
            var dragX by remember(mark.id, mark.x) { mutableFloatStateOf(mark.x) }
            var dragY by remember(mark.id, mark.y) { mutableFloatStateOf(mark.y) }
            val width = (mark.w * displayW).coerceAtLeast(35f)
            val height = (mark.h * displayH).coerceAtLeast(20f)
            Box(Modifier.offset((left + dragX * displayW).dp, (top + dragY * displayH).dp)
                .size(width.dp, height.dp)
                .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                .pointerInput(mark.id, displayW, displayH) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            dragX = (dragX + amount.x / (displayW * density)).coerceIn(0f, 1f - mark.w)
                            dragY = (dragY + amount.y / (displayH * density)).coerceIn(0f, 1f - mark.h)
                        },
                        onDragEnd = { onMoveMark(mark.copy(x = dragX, y = dragY)) }
                    )
                }.clickable { onMarkSelected(mark) }, contentAlignment = Alignment.Center) {
                if (mark.type == MarkType.TEXT_STAMP) {
                    Box(Modifier.fillMaxSize().border(2.dp, Color(0xFFB83F4E)),
                        contentAlignment = Alignment.Center) {
                        Text(mark.text, color = Color(0xFFB83F4E),
                            fontSize = (height * 0.36f).coerceIn(7f, 17f).sp,
                            fontWeight = FontWeight.Black, maxLines = 1)
                    }
                } else {
                    val markImage = remember(mark.assetPath) {
                        mark.assetPath?.let { path -> decodePreviewMark(path) }
                    }
                    if (markImage != null) Image(markImage.asImageBitmap(), null,
                        contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun decodePreviewMark(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val size = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (size / sample > 1100) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
