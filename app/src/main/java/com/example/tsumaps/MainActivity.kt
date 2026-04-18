package com.example.tsumaps

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.tsumaps.ui.theme.TSUMapsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import com.example.tsumaps.MapRenderer.drawPathLine
import com.example.tsumaps.MapRenderer.drawVoronoiZones

val CalibriFont = try { FontFamily(Font(R.font.calibri)) } catch (e: Exception) { FontFamily.SansSerif }

enum class MapMode(val labelRes: Int, val icon: Int) {
    NAVIGATION(R.string.mode_navigation, android.R.drawable.ic_menu_directions),
    CLUSTERING(R.string.mode_clustering, android.R.drawable.ic_menu_mapmode),
    TOUR(R.string.mode_tour, android.R.drawable.ic_menu_mylocation),
    DECISION_TREE(R.string.mode_decision_tree, android.R.drawable.ic_menu_view)
}

data class PointOfInterest(val x: Int, val y: Int, var clusterIndex: Int = -1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TSUMapsTheme {
                CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = CalibriFont)) {
                    TSUMapsApp()
                }
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun TSUMapsApp() {
    val context = LocalContext.current
    val mapRatio = 5.4f
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    val clusterManager = remember { ClusterManager() }
    val treeProcessor = remember { DecisionTreeProcessor() }
    val gridMatrix = remember {
        val options = BitmapFactory.Options().apply { inScaled = false }
        BitmapUtils.loadGridFromBitmap(BitmapFactory.decodeResource(context.resources, R.drawable.grid, options))
    }
    val pathFinder = remember(gridMatrix) { PathFinder(gridMatrix) }

    var currentMode by remember { mutableStateOf(MapMode.NAVIGATION) }
    var startPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var endPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pathPoints by remember { mutableStateOf<List<Pair<Int, Int>>?>(null) }
    val poiList = remember { mutableStateListOf<PointOfInterest>() }
    val clusterColors = remember { mutableStateMapOf<Int, Color>() }
    val tourPoints = remember { mutableStateListOf<Pair<Int, Int>>() }
    var tourPathPoints by remember { mutableStateOf<List<Pair<Int, Int>>?>(null) }
    var showDecisionDialog by remember { mutableStateOf(false) }
    var decisionResult by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    var viewScale by remember { mutableStateOf(2f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        viewScale = (viewScale * zoomChange).coerceIn(1f, 15f)
        viewOffset += offsetChange
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                MapMode.entries.forEach { mode ->
                    NavigationBarItem(
                        selected = currentMode == mode,
                        onClick = { currentMode = mode },
                        label = { Text(stringResource(mode.labelRes), fontSize = 12.sp) },
                        icon = { Icon(painterResource(mode.icon), null) }
                    )
                }
            }
        },
        floatingActionButton = {
            MapActionButtons(
                mode = currentMode,
                tourPointsCount = tourPoints.size,
                onRunACO = {
                    coroutineScope.launch {
                        isProcessing = true
                        tourPathPoints = withContext(Dispatchers.Default) { pathFinder.solveTSPWithAntColony(tourPoints) }
                        isProcessing = false
                    }
                },
                onOpenTree = { showDecisionDialog = true },
                onClearAll = {
                    poiList.clear(); clusterColors.clear(); tourPoints.clear()
                    pathPoints = null; tourPathPoints = null; startPoint = null; endPoint = null; decisionResult = null
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).clipToBounds()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentMode, viewOffset, viewScale) {
                        detectTapGestures { tapOffset ->
                            val cx = size.width / 2f; val cy = size.height / 2f
                            val gridX = (((tapOffset.x - cx - viewOffset.x) / viewScale + cx) / mapRatio).toInt()
                            val gridY = (((tapOffset.y - cy - viewOffset.y) / viewScale + cy) / mapRatio).toInt()
                            if (gridY !in gridMatrix.indices || gridX !in gridMatrix.indices || gridMatrix[gridY][gridX] == 1) return@detectTapGestures
                            when (currentMode) {
                                MapMode.NAVIGATION -> {
                                    if (startPoint == null || (startPoint != null && endPoint != null)) {
                                        startPoint = gridY to gridX; endPoint = null; pathPoints = null
                                    } else {
                                        endPoint = gridY to gridX
                                        coroutineScope.launch {
                                            isProcessing = true
                                            pathPoints = withContext(Dispatchers.Default) { pathFinder.findPathAStar(startPoint!!, endPoint!!) }
                                            isProcessing = false
                                        }
                                    }
                                }
                                MapMode.CLUSTERING -> {
                                    poiList.add(PointOfInterest(gridX, gridY))
                                    if (poiList.size >= 2) clusterManager.performKMeansClustering(poiList, (poiList.size / 2 + 1).coerceAtMost(5))
                                }
                                MapMode.TOUR -> tourPoints.add(gridY to gridX)
                                else -> {}
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = viewScale; scaleY = viewScale; translationX = viewOffset.x; translationY = viewOffset.y
                        transformOrigin = TransformOrigin.Center
                    }
                    .transformable(state = transformState)
            ) {
                Image(painterResource(R.drawable.mega_map), null, Modifier.fillMaxSize(), Alignment.TopStart)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (currentMode == MapMode.CLUSTERING && poiList.isNotEmpty()) {
                        drawVoronoiZones(poiList, mapRatio, { idx ->
                            clusterColors.getOrPut(idx) { Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), 1f) }
                        }, gridMatrix.size, gridMatrix.size)
                    }
                    pathPoints?.let { drawPathLine(it, mapRatio, Color.Red, 4f / viewScale) }
                    tourPathPoints?.let { drawPathLine(it, mapRatio, Color.Blue, 6f / viewScale) }
                    val dotRadius = 10f / viewScale
                    poiList.forEach { drawCircle(clusterColors[it.clusterIndex] ?: Color.Gray, dotRadius, Offset(it.x * mapRatio, it.y * mapRatio)) }
                    tourPoints.forEach { drawCircle(Color.Black, dotRadius, Offset(it.second * mapRatio, it.first * mapRatio)) }
                    startPoint?.let { drawCircle(Color.Green, 8f / viewScale, Offset(it.second * mapRatio, it.first * mapRatio)) }
                    endPoint?.let { drawCircle(Color.Blue, 8f / viewScale, Offset(it.second * mapRatio, it.first * mapRatio)) }
                }
            }
            if (isProcessing) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            decisionResult?.let { (place, path) ->
                Card(modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.recommendation_label, place), fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.path_label, path.joinToString(" → ")), fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
        if (showDecisionDialog) DecisionTreeMenu(treeProcessor, onDismiss = { showDecisionDialog = false }, onConfirm = { decisionResult = it; showDecisionDialog = false })
    }
}

@Composable
fun MapActionButtons(mode: MapMode, tourPointsCount: Int, onRunACO: () -> Unit, onOpenTree: () -> Unit, onClearAll: () -> Unit) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mode == MapMode.TOUR && tourPointsCount > 2) FloatingActionButton(onClick = onRunACO) { Icon(painterResource(android.R.drawable.ic_media_play), null) }
        if (mode == MapMode.DECISION_TREE) FloatingActionButton(onClick = onOpenTree) { Icon(painterResource(android.R.drawable.ic_input_add), null) }
        SmallFloatingActionButton(onClick = onClearAll, containerColor = MaterialTheme.colorScheme.errorContainer) {
            Icon(painterResource(android.R.drawable.ic_menu_delete), null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun DecisionTreeMenu(processor: DecisionTreeProcessor, onDismiss: () -> Unit, onConfirm: (Pair<String, List<String>>) -> Unit) {
    val context = LocalContext.current
    val initialCsv = remember { BitmapUtils.readAssetFile(context, "default_data.csv") }
    var csvText by remember { mutableStateOf(initialCsv) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            csvText = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: csvText
        }
    }

    val options = mapOf("location" to listOf("main_building", "second_building", "bus_stop", "campus_center"), "budget" to listOf("low", "medium", "high"), "time_available" to listOf("very_short", "short", "medium"), "food_type" to listOf("coffee", "pancakes", "full_meal", "snack"), "queue_tolerance" to listOf("low", "medium", "high"), "weather" to listOf("good", "bad"))
    val selection = remember { mutableStateMapOf<String, String>().apply { options.forEach { put(it.key, it.value.first()) } } }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(1.dp)) {
                Text(stringResource(R.string.decision_settings_title), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f)) {
                    options.forEach { (attr, opts) ->
                        item {
                            Text(attr, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                opts.forEach { opt -> FilterChip(selection[attr] == opt, { selection[attr] = opt }, { Text(opt, fontSize = 7.sp) }) }
                            }
                        }
                    }
                    item {
                        Button(onClick = { filePickerLauncher.launch("text/*") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text("Импорт CSV")
                        }
                        OutlinedTextField(value = csvText, onValueChange = { csvText = it }, label = { Text(stringResource(R.string.csv_data_label)) }, modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp))
                    }
                }
                Button(onClick = { onConfirm(processor.train(csvText).predict(selection)) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.find_lunch)) }
            }
        }
    }
}
