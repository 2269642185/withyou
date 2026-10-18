package com.withyou.anniversary

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = Repository(application)
    private val _data = MutableStateFlow(AppData())
    val data = _data.asStateFlow()
    var ready = MutableStateFlow(false); private set
    var busy = MutableStateFlow(false); private set
    var error = MutableStateFlow<String?>(null); private set
    var loadFailed = MutableStateFlow(false); private set
    init { viewModelScope.launch {
        try { _data.value = withContext(Dispatchers.IO) { repository.read() } }
        catch (e: Exception) { loadFailed.value = true; error.value = "本地数据读取失败，原文件已保留。可从设置恢复备份。" }
        finally { ready.value = true }
    } }
    fun dismissError() { error.value = null }
    fun update(transform: (AppData) -> AppData, done: () -> Unit = {}) {
        if (busy.value || loadFailed.value) return
        execute {
            val next = transform(_data.value)
            repository.save(next)
            _data.value = next
            done()
        }
    }
    fun save(record: MemoryDay, done: () -> Unit) = update({ current ->
        require(current.records.size < 500 || current.records.any { it.id == record.id }) { "最多支持 500 条纪念日" }
        val rest = current.records.filterNot { it.id == record.id }.map { if (record.pinned) it.copy(pinned = false) else it }
        current.copy(records = rest + record)
    }, done)
    fun pin(id: String) = update(transform = { current -> current.copy(records = current.records.map { it.copy(pinned = it.id == id && !it.pinned) }) })
    fun saveCategory(category: Category, done: () -> Unit = {}) = update(transform = { current ->
        val trimmed = category.copy(name = category.name.trim())
        require(trimmed.name.isNotBlank() && trimmed.name.length <= 20) { "分类名称应为 1 至 20 字" }
        require(trimmed.name !in listOf("全部","未分类")) { "请使用其他分类名称" }
        require(current.categories.none { it.id != trimmed.id && it.name.equals(trimmed.name,ignoreCase=true) }) { "这个分类名称已存在" }
        require(current.categories.size < 50 || current.categories.any { it.id == trimmed.id }) { "最多支持 50 个分类" }
        current.copy(categories = current.categories.filterNot { it.id == trimmed.id } + trimmed)
    },done=done)
    fun deleteCategory(id: String) = update(transform={it.deleteCategory(id)})
    fun delete(id: String, done: () -> Unit) = update({ it.copy(records = it.records.filterNot { r -> r.id == id }) }, done)
    fun importPhoto(uri: Uri, done: (String) -> Unit) = execute { done(repository.importPhoto(uri)) }
    fun backup(uri: Uri, done: () -> Unit) = execute { repository.backup(uri, _data.value); done() }
    fun restore(uri: Uri, done: () -> Unit) = execute {
        _data.value = repository.restore(uri); loadFailed.value = false; done()
    }
    private fun execute(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { action() }
            catch (e: Exception) { error.value = e.message ?: "操作失败，请重试" }
            finally { busy.value = false }
        }
    }
}
