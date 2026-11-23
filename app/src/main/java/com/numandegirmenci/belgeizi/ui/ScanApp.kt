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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
fun ScanApp(activity: ComponentActivity, pendingUris: List<Uri>, onConsumed: () -> Unit) {
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
    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty() || busy) return
        scope.launch {
            busy = true; status = "Belgeler aktarılıyor..."
            try {
                val doc = withContext(Dispatchers.IO) { store.import(uris) }
                documents = store.all(); current = doc; pageIndex = 0
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
                    snackbar.showSnackbar("PDF kaydedildi.")
                } catch (e: Exception) { reportError(e) }
                finally { busy = false }
            }
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
                var latest = doc
                for (i in latest.pages.indices) {
                    if (latest.pages[i].ocrDone) continue
                    status = "OCR ${i + 1}/${latest.pages.size} • metin algılanıyor"
                    val page = latest.pages[i]
                    val lines = withContext(Dispatchers.Default) {
                        val bitmap = store.loadBitmap(page)
                        try { OcrEngine.recognize(bitmap) }
                        finally { bitmap.recycle() }
                    }
                    val pages = latest.pages.toMutableList()
                    pages[i] = page.copy(ocr = lines, ocrDone = true)
                    latest = latest.copy(pages = pages)
                    update(latest)
                }
                snackbar.showSnackbar("OCR tamamlandı. Metin aracından düzeltme yapabilirsin.")
            } catch (e: Exception) { reportError(e) }
            finally { busy = false; status = "" }
        }
    }

    LaunchedEffect(pendingUris) {
        if (pendingUris.isNotEmpty()) {
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
                        onOpen = { doc -> current = doc; pageIndex = 0 },
                        onDelete = { deleteCandidate = it }
                    )
                } else {
                    EditorScreen(
                        doc = current!!, pageIndex = pageIndex, onPageIndex = { pageIndex = it },
                        onBack = { current = null; documents = store.all() },
                        onOcr = ::recognizeAll,
                        onPageChanged = ::changePage,
                        onExport = { doc -> savePdf.launch("${doc.title.take(70)}.pdf") },
                        onSignature = { signatureOpen = true },
                        onStamp = { stampOpen = true },
                        onImageStamp = { stampImagePicker.launch(arrayOf("image/*")) }
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 25.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Accent),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Description, null, tint = Background, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("BELGE İZİ", color = TextMain, fontWeight = FontWeight.Black,
                    fontSize = 20.sp, letterSpacing = 1.4.sp)
                Text("Akıllı tarama • Türkçe OCR", color = TextMuted, fontSize = 12.sp)
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Spacer(Modifier.height(14.dp))
                Text("Belgen hazır mı?", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.height(8.dp))
                Text("Tara, metinleri düzenle, imzala ve PDF olarak kaydet.",
                    color = TextMuted, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(28.dp))
                Surface(onClick = onScan, shape = RoundedCornerShape(25.dp), color = Accent,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(21.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PhotoCamera, null, modifier = Modifier.size(32.dp),
                            tint = Background)
                        Spacer(Modifier.width(17.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Yeni belge tara", color = Background,
                                fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                            Text("Otomatik kırpma • çoklu sayfa", color = Background.copy(alpha = 0.78f),
                                fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.ArrowForward, null, tint = Background)
                    }
                }
                Spacer(Modifier.height(11.dp))
                Surface(onClick = onImport, shape = RoundedCornerShape(22.dp), color = Panel,
                    modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, PanelBright)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(28.dp),
                            tint = TextMain)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Dosyadan aç", color = TextMain,
                                fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text("PDF • Fotoğraf • OneDrive", color = TextMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.ArrowForward, null, tint = TextMuted)
                    }
                }
                Spacer(Modifier.height(25.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Belgelerim", color = TextMain, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${docs.size} belge", color = TextMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (docs.isEmpty()) item {
                Surface(color = Panel, shape = RoundedCornerShape(21.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.InsertDriveFile, null, tint = TextMuted,
                            modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Henüz belge yok", color = TextMain, fontWeight = FontWeight.Bold)
                        Text("İlk taraman burada görünecek.", color = TextMuted)
                    }
                }
            }
            items(docs, key = { it.id }) { doc ->
                Surface(onClick = { onOpen(doc) }, shape = RoundedCornerShape(18.dp),
                    color = Panel, border = BorderStroke(1.dp, PanelBright)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(53.dp).background(PanelBright, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.PictureAsPdf, null, tint = Accent,
                                modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(doc.title, color = TextMain, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text("${doc.pages.size} sayfa  •  ${SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR")).format(Date(doc.createdAt))}",
                                color = TextMuted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onDelete(doc) }) {
                            Icon(Icons.Outlined.DeleteOutline, "Belgeyi sil", tint = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(
    doc: ScanDocument, pageIndex: Int, onPageIndex: (Int) -> Unit,
    onBack: () -> Unit, onOcr: () -> Unit,
    onPageChanged: ((DocumentPage) -> DocumentPage) -> Unit,
    onExport: (ScanDocument) -> Unit,
    onSignature: () -> Unit, onStamp: () -> Unit, onImageStamp: () -> Unit
) {
    val index = pageIndex.coerceIn(0, doc.pages.lastIndex)
    val page = doc.pages[index]
    val context = LocalContext.current
    var textEditing by remember { mutableStateOf(false) }
    var selectedLine by remember { mutableStateOf<OcrLine?>(null) }
    var selectedMark by remember { mutableStateOf<PageMark?>(null) }
    var stampChoices by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Geri", tint = TextMain) }
            Column(Modifier.weight(1f)) {
                Text(doc.title, color = TextMain, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                Text("${index + 1} / ${doc.pages.size} sayfa", color = TextMuted, fontSize = 12.sp)
            }
            Button(onClick = { onExport(doc) },
                contentPadding = PaddingValues(horizontal = 15.dp), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(5.dp))
                Text("PDF")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(100.dp), color = PanelBright) {
                val uncertain = page.ocr.count { it.confidence in 0.001f..0.719f }
                Text(when {
                    !page.ocrDone -> "●  OCR bekliyor"
                    uncertain > 0 -> "●  ${page.ocr.size} satır • $uncertain kontrol"
                    else -> "●  ${page.ocr.size} metin satırı"
                }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (uncertain > 0) Danger else if (page.ocrDone) Accent else TextMuted,
                    fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(enabled = index > 0, onClick = { onPageIndex(index - 1) }) {
                Icon(Icons.Outlined.ChevronLeft, "Önceki sayfa", tint = TextMain)
            }
            IconButton(enabled = index < doc.pages.lastIndex, onClick = { onPageIndex(index + 1) }) {
                Icon(Icons.Outlined.ChevronRight, "Sonraki sayfa", tint = TextMain)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 15.dp)
            .background(Color(0xFF253549), RoundedCornerShape(19.dp)).clip(RoundedCornerShape(19.dp))) {
            DocumentCanvas(page = page, showOcr = textEditing,
                onLineSelected = { selectedLine = it },
                onMarkSelected = { selectedMark = it },
                onMoveMark = { changed -> onPageChanged { p ->
                    p.copy(marks = p.marks.map { if (it.id == changed.id) changed else it })
                } })
        }
        Spacer(Modifier.height(11.dp))
        if (textEditing) {
            Text("Vurgulanan satıra dokunarak metni düzelt.", color = Accent,
                fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionTool(Icons.Outlined.AutoFixHigh, "OCR", onOcr)
            ActionTool(Icons.Outlined.TextFields, "Metin", { textEditing = !textEditing }, textEditing)
            ActionTool(Icons.Outlined.Draw, "İmza", onSignature)
            ActionTool(Icons.Outlined.Approval, "Kaşe", { stampChoices = true })
            ActionTool(Icons.Outlined.ContentCopy, "Kopyala", { showText = true })
        }
    }

    selectedLine?.let { line ->
        var edited by remember(line.id) { mutableStateOf(line.text) }
        AlertDialog(onDismissRequest = { selectedLine = null },
            title = { Text("OCR metnini düzelt") },
            text = {
                Column {
                    Text("Okunan: ${line.original}", color = TextMuted, fontSize = 12.sp)
                    if (line.confidence > 0f) Text("OCR güveni: %${(line.confidence * 100).toInt()}",
                        color = if (line.confidence < 0.72f) Danger else Accent, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = edited, onValueChange = { edited = it },
                        label = { Text("Belgedeki yeni metin") }, minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Text("Değişiklik belge görünümüne yansıtılır. Karmaşık arka planlarda iz kalabilir.",
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
                Text(allText.ifBlank { "Henüz OCR yapılmadı." })
            } },
            confirmButton = { TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Belge İzi OCR", allText))
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
    val bitmap by produceState<Bitmap?>(initialValue = null, page.path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(page.path) }
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
        page.ocr.filter { it.edited || showOcr }.forEach { line ->
            val x = left + line.x * displayW
            val y = top + line.y * displayH
            val width = (line.w * displayW).coerceAtLeast(14f)
            val height = (line.h * displayH).coerceAtLeast(13f)
            Box(Modifier.offset(x.dp, y.dp).size(width.dp, height.dp)
                .then(if (line.edited) Modifier.background(Color.White)
                else Modifier.background(
                    (if (line.confidence in 0.001f..0.719f) Danger else Accent).copy(alpha = 0.24f)
                ))
                .then(if (showOcr) Modifier.border(1.dp,
                    if (line.confidence in 0.001f..0.719f) Danger else Accent)
                    .clickable { onLineSelected(line) }
                else Modifier)) {
                if (line.edited) Text(line.text, color = Color.Black,
                    fontSize = min(height * 0.56f,
                        (width / line.text.length.coerceAtLeast(1)) * 1.7f)
                        .coerceIn(5f, 17f).sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    modifier = Modifier.align(Alignment.CenterStart))
            }
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
