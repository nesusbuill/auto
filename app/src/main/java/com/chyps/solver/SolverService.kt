package com.chyps.solver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SolverService : AccessibilityService() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
        
    // ==========================================
    // ĐIỀN KEY GROQ CỦA MÀY VÀO ĐÂY
    // ==========================================
    private val apiUrl = "https://api.groq.com/openai/v1/chat/completions"
    private val apiKey = "KEY_GROQ_CUA_MAY" 
    private val model = "openai/gpt-oss-120b"

    private var lastProcessedText = ""
    private var isSolving = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Bắt sự kiện màn hình thay đổi
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            if (isSolving) return 
            checkAndSolve()
        }
    }

    private fun checkAndSolve() {
        val rootNode = rootInActiveWindow ?: return
        
        val screenText = StringBuilder()
        collectText(rootNode, screenText)
        val fullText = screenText.toString().trim()

        // Lọc lấy đúng phần đề thi
        val examText = extractExamText(fullText)
        if (examText.length < 30) return

        if (examText == lastProcessedText) return
        lastProcessedText = examText

        isSolving = true

        CoroutineScope(Dispatchers.IO).launch {
            val correctAnswer = askApi(examText)
            
            if (correctAnswer != null) {
                // Đợi DOM render xong
                delay(500) 
                CoroutineScope(Dispatchers.Main).launch {
                    clickAnswerWithGesture(correctAnswer)
                }
            }
            // Đợi 2s tránh quét nhầm khi màn hình đang chuyển cảnh
            delay(2000) 
            isSolving = false
        }
    }

    private fun extractExamText(fullText: String): String {
        var startIndex = fullText.indexOf("Câu", ignoreCase = true)
        if (startIndex == -1) startIndex = 0
        
        var endIndex = fullText.indexOf("Nộp bài", ignoreCase = true)
        if (endIndex == -1) endIndex = fullText.length
        
        if (startIndex >= endIndex) return ""
        
        val rawExam = fullText.substring(startIndex, endIndex)
        return rawExam.lines().distinct().joinToString("\n").trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (node.text != null) {
            sb.append(node.text).append("\n")
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb) }
        }
    }

    private fun askApi(text: String): String? {
        try {
            val prompt = """
                Bạn là chuyên gia giải trắc nghiệm.
                Đề bài:
                $text
                
                Hãy giải và CHỈ TRẢ LỜI BẰNG MỘT CHỮ CÁI DUY NHẤT (A, B, C, hoặc D).
            """.trimIndent()

            val json = JSONObject().apply {
                put("model", model)
                put("temperature", 0.1)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }
            
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            
            val jsonResponse = JSONObject(responseBody)
            val content = jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                
            val regex = Regex("\\b[A-D]\\b")
            return regex.find(content)?.value
        } catch (e: Exception) {
            return null
        }
    }

    // NÂNG CẤP CLICK: Thử code trước, nếu xịt thì chạm vật lý
    private fun clickAnswerWithGesture(letter: String) {
        val rootNode = rootInActiveWindow ?: return
        val nodes = rootNode.findAccessibilityNodeInfosByText(letter)
        
        for (node in nodes) {
            val text = node.text?.toString() ?: ""
            if (text == letter || text.startsWith("$letter.") || text.startsWith("$letter ")) {
                
                // CÁCH 1: Click bằng Code
                var clickableNode: AccessibilityNodeInfo? = node
                var depth = 0
                while (clickableNode != null && !clickableNode.isClickable && depth < 5) {
                    clickableNode = clickableNode.parent
                    depth++
                }
                
                if (clickableNode != null && clickableNode.isClickable) {
                    val clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return 
                }

                // CÁCH 2: Click bằng Toạ độ vật lý (Dispatch Gesture)
                val rect = Rect()
                node.getBoundsInScreen(rect)
                
                if (rect.width() > 0 && rect.height() > 0) {
                    val centerX = rect.centerX().toFloat()
                    val centerY = rect.centerY().toFloat()
                    
                    val path = Path()
                    path.moveTo(centerX, centerY)
                    
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, 50)) 
                        .build()
                        
                    dispatchGesture(gesture, null, null)
                    return
                }
            }
        }
    }

    override fun onInterrupt() {
        isSolving = false
    }
}
