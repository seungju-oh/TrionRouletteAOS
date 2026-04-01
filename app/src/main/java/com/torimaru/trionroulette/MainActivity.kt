package com.torimaru.trionroulette

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.random.Random

// --- 1. 데이터 모델 및 설정 ---
enum class RouletteMode { A, B, C }

data class RouletteItem(
    val id: Long = System.currentTimeMillis() + Random.nextLong(),
    var text: String = "",
    var probText: String = ""
)

val RouletteColorPalette = listOf(
    Color(0xFFFFB3BA), Color(0xFFAFF8D8), Color(0xFFBAE1FF), Color(0xFFFFFFBA),
    Color(0xFFD1BAFF), Color(0xFFFFDFBA), Color(0xFFE7FFAC), Color(0xFFFFC9DE),
    Color(0xFFC4FAF8), Color(0xFFB5B9FF), Color(0xFFFFDAB9), Color(0xFFE2F0CB)
)

fun getRouletteColor(index: Int, totalItems: Int): Color {
    if (totalItems == 0) return Color.Gray
    var colorIndex = index % RouletteColorPalette.size
    if (index == totalItems - 1 && colorIndex == 0 && totalItems > 1) {
        colorIndex = 1
    }
    return RouletteColorPalette[colorIndex]
}

// --- 2. 프리셋 저장 로직 (SharedPreferences) ---
fun savePreset(context: Context, name: String, items: List<RouletteItem>) {
    val prefs = context.getSharedPreferences("RoulettePrefs", Context.MODE_PRIVATE)
    val serializedItems = items.joinToString("|||") { "${it.text}===${it.probText}" }
    prefs.edit().putString("preset_$name", serializedItems).apply()
    val namesStr = prefs.getString("preset_names", "") ?: ""
    val namesList = if (namesStr.isEmpty()) mutableListOf() else namesStr.split(",").toMutableList()
    if (!namesList.contains(name)) {
        namesList.add(name)
        prefs.edit().putString("preset_names", namesList.joinToString(",")).apply()
    }
}

fun loadPresetNames(context: Context): List<String> = context.getSharedPreferences("RoulettePrefs", Context.MODE_PRIVATE).getString("preset_names", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
fun loadPresetItems(context: Context, name: String): List<RouletteItem> = context.getSharedPreferences("RoulettePrefs", Context.MODE_PRIVATE).getString("preset_$name", "")?.split("|||")?.map { val parts = it.split("==="); RouletteItem(text = parts.getOrNull(0) ?: "", probText = parts.getOrNull(1) ?: "") } ?: emptyList()
fun deletePreset(context: Context, name: String) {
    val prefs = context.getSharedPreferences("RoulettePrefs", Context.MODE_PRIVATE)
    prefs.edit().remove("preset_$name").apply()
    val namesList = loadPresetNames(context).toMutableList().apply { remove(name) }
    prefs.edit().putString("preset_names", namesList.joinToString(",")).apply()
}

// --- 3. 메인 화면 ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RouletteScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouletteScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(RouletteMode.A) }
    var items by remember { mutableStateOf(listOf(RouletteItem(), RouletteItem())) }
    var eliminatedIds by remember { mutableStateOf(setOf<Long>()) }
    var skipAnimation by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    // !!! 덮어쓰기 확인용 팝업 상태 추가 !!!
    var showOverwriteDialog by remember { mutableStateOf(false) }

    var showLoadDialog by remember { mutableStateOf(false) }
    var presetNameToSave by remember { mutableStateOf("") }
    var savedPresetNames by remember { mutableStateOf(listOf<String>()) }
    var showResetDialog by remember { mutableStateOf(false) }

    val rotation = remember { Animatable(0f) }

    LaunchedEffect(mode) {
        if (mode == RouletteMode.B) eliminatedIds = emptySet()
        rotation.snapTo(0f)
    }

    val currentDisplayItems = if (mode == RouletteMode.B) items.filterNot { it.id in eliminatedIds } else items
    val totalProb = items.mapNotNull { it.probText.toDoubleOrNull() }.sum()
    val isModeCValid = kotlin.math.abs(totalProb - 100.0) < 0.01
    val canSpin = items.size >= 2 && items.all { it.text.isNotBlank() } && (mode != RouletteMode.C || isModeCValid)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("TrionRoulette", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(
                        onClick = { showSaveDialog = true },
                        // [Appium ID] 상단바: 프리셋 저장 팝업을 띄우는 버튼
                        modifier = Modifier.semantics { contentDescription = "btn_topbar_save" }
                    ) { Text("저장") }

                    TextButton(
                        onClick = { showLoadDialog = true },
                        // [Appium ID] 상단바: 프리셋 불러오기 팝업을 띄우는 버튼
                        modifier = Modifier.semantics { contentDescription = "btn_topbar_load" }
                    ) { Text("불러오기") }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // --- 모드 선택 영역 ---
                item {
                    Text("모드 선택", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = mode == RouletteMode.A,
                            onClick = { mode = RouletteMode.A },
                            // [Appium ID] 라디오 버튼: 모드 A (기본) 선택
                            modifier = Modifier.semantics { contentDescription = "radio_mode_a" }
                        )
                        Text("기본 (A)", modifier = Modifier.padding(end = 8.dp))

                        RadioButton(
                            selected = mode == RouletteMode.B,
                            onClick = { mode = RouletteMode.B },
                            // [Appium ID] 라디오 버튼: 모드 B (서바이벌) 선택
                            modifier = Modifier.semantics { contentDescription = "radio_mode_b" }
                        )
                        Text("서바이벌 (B)", modifier = Modifier.padding(end = 8.dp))

                        RadioButton(
                            selected = mode == RouletteMode.C,
                            onClick = { mode = RouletteMode.C },
                            // [Appium ID] 라디오 버튼: 모드 C (커스텀) 선택
                            modifier = Modifier.semantics { contentDescription = "radio_mode_c" }
                        )
                        Text("커스텀 (C)")
                    }
                }

                // --- 룰렛 휠 영역 ---
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                        if (items.size >= 2 && currentDisplayItems.isNotEmpty()) {
                            RouletteWheel(displayItems = currentDisplayItems, allItems = items, mode = mode, rotationDegree = rotation.value)
                            Box(modifier = Modifier.align(Alignment.TopCenter).size(20.dp).background(Color.Red))
                        } else {
                            Text("항목을 2개 이상 입력해주세요.")
                        }
                    }
                }

                // --- 옵션 및 스핀 버튼 ---
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { skipAnimation = !skipAnimation }
                            .padding(vertical = 8.dp)
                            // [Appium ID] 레이아웃 행: 애니메이션 스킵 토글 영역 전체
                            .semantics { contentDescription = "row_skip_animation" },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = skipAnimation,
                            onCheckedChange = { skipAnimation = it },
                            // [Appium ID] 체크박스: 애니메이션 스킵 체크
                            modifier = Modifier.semantics { contentDescription = "chk_skip_animation" }
                        )
                        Text("애니메이션 스킵 (빠른 결과)")
                    }
                    Button(
                        onClick = {
                            if (!canSpin) return@Button
                            coroutineScope.launch {
                                val winnerIndex = calculateWinner(currentDisplayItems, mode)
                                if (winnerIndex != -1) {
                                    val winner = currentDisplayItems[winnerIndex]
                                    if (skipAnimation) { rotation.snapTo((rotation.value + Random.nextInt(0, 360)) % 360f) }
                                    else { rotation.animateTo(rotation.value + 1800f + Random.nextInt(0, 360), tween(3000, easing = FastOutSlowInEasing)) }
                                    resultText = "'${winner.text}' 당첨!"; showDialog = true
                                    if (mode == RouletteMode.B) {
                                        val newEliminatedIds = eliminatedIds + winner.id
                                        if (items.all { it.id in newEliminatedIds }) { eliminatedIds = emptySet(); Toast.makeText(context, "초기화되었습니다.", Toast.LENGTH_SHORT).show() }
                                        else { eliminatedIds = newEliminatedIds }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            // [Appium ID] 메인 버튼: 룰렛 회전 시작
                            .semantics {
                                contentDescription = if (canSpin) "btn_spin_enabled" else "btn_spin_disabled"
                            },
                        enabled = canSpin
                    ) { Text("START SPIN") }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // --- 항목 관리 헤더 ---
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("항목 관리", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Row {
                            IconButton(onClick = { showResetDialog = true }) {
                                // [Appium ID] 아이콘 버튼: 입력 항목 전체 초기화
                                Icon(Icons.Default.Refresh, contentDescription = "btn_reset_all", tint = Color.Gray)
                            }
                            IconButton(onClick = { items = items + RouletteItem() }) {
                                // [Appium ID] 아이콘 버튼: 룰렛 항목 한 개 추가
                                Icon(Icons.Default.Add, contentDescription = "btn_add_item")
                            }
                        }
                    }
                    if (mode == RouletteMode.C) {
                        Text("현재 확률 총합: ${String.format("%.2f", totalProb)}%", color = if (isModeCValid) Color.Blue else Color.Red)
                    }
                }

                // --- 항목 리스트 영역 ---
                itemsIndexed(items) { index, item ->
                    val isEliminated = mode == RouletteMode.B && item.id in eliminatedIds
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(getRouletteColor(index, items.size).copy(alpha = if (isEliminated) 0.3f else 1f)))
                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedTextField(
                            value = item.text,
                            onValueChange = { items = items.toMutableList().apply { set(index, item.copy(text = it)) } },
                            label = { Text("항목 ${index + 1}") },
                            modifier = Modifier
                                .weight(1f)
                                // [Appium ID] 텍스트 입력창: N번째 항목 텍스트 (비활성화 여부 포함 동적 처리)
                                .semantics {
                                    contentDescription = if (isEliminated) "input_item_text_${index}_disabled" else "input_item_text_$index"
                                },
                            singleLine = true, enabled = !isEliminated,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrect = false, imeAction = ImeAction.Next)
                        )

                        if (mode == RouletteMode.C) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = item.probText,
                                onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) items = items.toMutableList().apply { set(index, item.copy(probText = it)) } },
                                label = { Text("%") },
                                modifier = Modifier
                                    .width(80.dp)
                                    // [Appium ID] 텍스트 입력창: N번째 항목 확률 (비활성화 여부 포함 동적 처리)
                                    .semantics {
                                        contentDescription = if (isEliminated) "input_item_prob_${index}_disabled" else "input_item_prob_$index"
                                    },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrect = false),
                                singleLine = true,
                                enabled = !isEliminated
                            )
                        }

                        IconButton(
                            onClick = { if (items.size > 2) items = items.filterIndexed { i, _ -> i != index } },
                            enabled = !isEliminated,
                            // [Appium ID] 버튼 영역: N번째 항목 삭제 버튼 (비활성화 여부 포함 동적 처리)
                            modifier = Modifier.semantics {
                                contentDescription = if (isEliminated) "btn_delete_item_${index}_disabled" else "btn_delete_item_$index"
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = if (isEliminated) 0.3f else 1f))
                        }
                    }
                }
            }
        }
    }

    // --- 4. 다이얼로그 관리 ---

    // 전체 초기화 팝업
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("초기화") },
            text = { Text("모든 항목을 지울까요?") },
            confirmButton = {
                Button(
                    onClick = { items = listOf(RouletteItem(), RouletteItem()); eliminatedIds = emptySet(); coroutineScope.launch { rotation.snapTo(0f) }; showResetDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    // [Appium ID] 팝업 버튼: 전체 초기화 승인
                    modifier = Modifier.semantics { contentDescription = "btn_dialog_reset_confirm" }
                ) { Text("초기화") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    // [Appium ID] 팝업 버튼: 전체 초기화 취소
                    modifier = Modifier.semantics { contentDescription = "btn_dialog_reset_cancel" }
                ) { Text("취소") }
            }
        )
    }

    // 결과 팝업
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("결과") },
            text = { Text(resultText ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    // [Appium ID] 팝업 버튼: 룰렛 당첨 결과 확인창 닫기
                    modifier = Modifier.semantics { contentDescription = "btn_dialog_result_confirm" }
                ) { Text("확인") }
            }
        )
    }

    // 프리셋 저장 팝업
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("저장") },
            text = {
                OutlinedTextField(
                    value = presetNameToSave,
                    onValueChange = { presetNameToSave = it },
                    label = { Text("이름 입력") },
                    // [Appium ID] 텍스트 입력창: 저장할 프리셋 이름 입력
                    modifier = Modifier.semantics { contentDescription = "input_preset_name" }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetNameToSave.isNotBlank()) {
                            val existingNames = loadPresetNames(context)
                            // !!! 중복 검사 로직 추가 !!!
                            if (existingNames.contains(presetNameToSave)) {
                                showSaveDialog = false
                                showOverwriteDialog = true // 중복 시 덮어쓰기 확인 팝업 호출
                            } else {
                                savePreset(context, presetNameToSave, items)
                                Toast.makeText(context, "'$presetNameToSave' 저장 완료!", Toast.LENGTH_SHORT).show()
                                showSaveDialog = false
                                presetNameToSave = ""
                            }
                        }
                    },
                    // [Appium ID] 팝업 버튼: 프리셋 저장 승인
                    modifier = Modifier.semantics { contentDescription = "btn_dialog_save_confirm" }
                ) { Text("저장") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false },
                    // [Appium ID] 팝업 버튼: 프리셋 저장 취소
                    modifier = Modifier.semantics { contentDescription = "btn_dialog_save_cancel" }
                ) { Text("취소") }
            }
        )
    }

    // !!! 새로 추가된 프리셋 덮어쓰기 확인 팝업 !!!
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = {
                showOverwriteDialog = false
                showSaveDialog = true // 취소하면 다시 이름 입력 창으로 돌려보냄
            },
            title = { Text("덮어쓰기 확인") },
            text = { Text("'$presetNameToSave'은(는) 이미 존재하는 목록입니다.\n기존 데이터를 지우고 덮어쓰시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        savePreset(context, presetNameToSave, items)
                        Toast.makeText(context, "'$presetNameToSave' 덮어쓰기 완료!", Toast.LENGTH_SHORT).show()
                        showOverwriteDialog = false
                        presetNameToSave = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    // [Appium ID] 팝업 버튼: 프리셋 덮어쓰기 승인
                    modifier = Modifier.semantics { contentDescription = "btn_dialog_overwrite_confirm" }
                ) { Text("덮어쓰기", color = Color.White) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverwriteDialog = false
                        showSaveDialog = true // 취소하면 다시 이름 입력 창으로 돌려보냄
                    },
                    // [Appium ID] 팝업 버튼: 프리셋 덮어쓰기 취소
                    modifier = Modifier.semantics { contentDescription = "btn_dialog_overwrite_cancel" }
                ) { Text("취소") }
            }
        )
    }

    // 프리셋 불러오기 팝업
    if (showLoadDialog) {
        LaunchedEffect(Unit) { savedPresetNames = loadPresetNames(context) };
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text("불러오기") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                    items(savedPresetNames) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { items = loadPresetItems(context, name); eliminatedIds = emptySet(); showLoadDialog = false }
                                .padding(12.dp)
                                // [Appium ID] 레이아웃 행: 특정 프리셋을 불러오기 위한 행(이름 기반 동적 ID)
                                .semantics { contentDescription = "row_load_preset_$name" }
                        ) {
                            Text(name, Modifier.weight(1f));
                            IconButton(onClick = { deletePreset(context, name); savedPresetNames = loadPresetNames(context) }) {
                                // [Appium ID] 아이콘 버튼: 특정 프리셋 삭제(이름 기반 동적 ID)
                                Icon(Icons.Default.Delete, contentDescription = "btn_delete_preset_$name")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showLoadDialog = false },
                    // [Appium ID] 팝업 버튼: 불러오기 팝업 닫기
                    modifier = Modifier.semantics { contentDescription = "btn_dialog_load_close" }
                ) { Text("닫기") }
            }
        )
    }
}

// 룰렛 UI 그리기
@Composable
fun RouletteWheel(displayItems: List<RouletteItem>, allItems: List<RouletteItem>, mode: RouletteMode, rotationDegree: Float) {
    Canvas(
        modifier = Modifier
            .size(200.dp)
            // [Appium ID] 캔버스 영역: 룰렛 휠 시각화 객체
            .semantics { contentDescription = "canvas_roulette_wheel" }
    ) {
        val totalProb = if (mode == RouletteMode.C) displayItems.sumOf { it.probText.toDoubleOrNull() ?: 0.0 }.toFloat() else 0f
        var startAngle = rotationDegree
        displayItems.forEachIndexed { index, item ->
            val sweepAngle = if (mode == RouletteMode.C && totalProb > 0) ((item.probText.toFloatOrNull() ?: 0f) / totalProb) * 360f else 360f / displayItems.size
            drawArc(color = getRouletteColor(allItems.indexOf(item), allItems.size), startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true, size = Size(size.width, size.height))
            startAngle += sweepAngle
        }
    }
}

fun calculateWinner(items: List<RouletteItem>, mode: RouletteMode): Int {
    if (items.isEmpty()) return -1
    if (mode == RouletteMode.C) {
        val random = Random.nextDouble(100.0)
        var cumulative = 0.0
        items.forEachIndexed { i, item -> cumulative += item.probText.toDoubleOrNull() ?: 0.0; if (random <= cumulative) return i }
    }
    return Random.nextInt(items.size)
}