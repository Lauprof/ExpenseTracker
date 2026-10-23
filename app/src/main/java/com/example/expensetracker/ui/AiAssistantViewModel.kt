package com.example.expensetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.local.ExpenseDatabase
import com.example.expensetracker.data.parser.ModelDownloadManager
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = ModelDownloadManager(application)
    private val expenseDao = ExpenseDatabase.getInstance(application).expenseDao()
    private var llmInference: LlmInference? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Hello! I am your local offline AI financial assistant. Ask me anything about your spending, M-Pesa balance, or budget insights!",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        initLlm()
    }

    private fun initLlm() {
        viewModelScope.launch(Dispatchers.IO) {
            reloadModelInternal()
        }
    }

    private fun reloadModelInternal() {
        try {
            if (llmInference == null && downloadManager.isModelDownloaded()) {
                val modelFile = downloadManager.getModelFile()
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(256)
                    .build()
                llmInference = LlmInference.createFromOptions(getApplication(), options)
            }
        } catch (e: Exception) {
            // Ignore initialization error
        }
    }

    fun reloadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            reloadModelInternal()
        }
    }

    fun sendMessage(query: String) {
        if (query.isBlank()) return

        val userMsg = ChatMessage(text = query, isUser = true)
        _messages.update { it + userMsg }

        viewModelScope.launch {
            _isGenerating.value = true
            val aiResponse = generateAiReply(query)
            _messages.update { it + ChatMessage(text = aiResponse, isUser = false) }
            _isGenerating.value = false
        }
    }

    private suspend fun generateAiReply(query: String): String = withContext(Dispatchers.IO) {
        reloadModelInternal()
        val inference = llmInference
        if (inference == null) {
            return@withContext "Local AI model is not downloaded yet. Please download the model using the button above to chat offline."
        }

        try {
            val expenses = expenseDao.getAllExpenses().first()
            val totalSpend = expenses.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val totalInc = expenses.filter { it.type == "INCOME" }.sumOf { it.amount }
            val categories = expenses.filter { it.type == "EXPENSE" }
                .groupBy { it.category }
                .map { (cat, list) -> "${cat}: Ksh ${list.sumOf { it.amount }}" }
                .joinToString(", ")

            val prompt = """
                You are a helpful offline personal finance assistant running locally on the user's phone.
                User's financial summary:
                - Total Spending: Ksh $totalSpend
                - Total Income: Ksh $totalInc
                - Categories: $categories
                
                User question: "$query"
                Answer helpfully, concisely, and accurately in Kenya Shillings (Ksh).
            """.trimIndent()

            inference.generateResponse(prompt) ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            "Error generating response: ${e.localizedMessage}"
        }
    }

    override fun onCleared() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
