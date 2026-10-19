package com.withyou.anniversary

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TodoCelebrationUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun confirmedBothCompletionCelebratesOnceAndRespectsDisabledMotion() {
        val uid="celebration-"+UUID.randomUUID();val date=TodoRules.today()
        val task=TodoTask(owner=uid,title="共同小目标",kind=TodoKind.SHARED,shared=true,pairId="pair")
        val base=TodoBook(uid,pairId="pair",partnerId="peer",tasks=listOf(task))
        var book by mutableStateOf(base);var mounted by mutableStateOf(true);var motion by mutableStateOf(true)
        val label="♡ 今天的小约定，一起完成了"
        compose.setContent {MaterialTheme {CompositionLocalProvider(LocalMotionEnabled provides motion) {if(mounted) TodoTogetherCelebration(book,date,true)}}}
        compose.waitForIdle();compose.onNodeWithText(label).assertDoesNotExist()
        val completed=listOf(TodoCheck(task.id,uid,date,true),TodoCheck(task.id,"peer",date,true))
        compose.runOnIdle {book=base.copy(checks=completed)}
        compose.waitUntil(3000) {compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.waitUntil(6000) {compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()}
        compose.runOnIdle {book=base};compose.waitForIdle()
        compose.runOnIdle {book=base.copy(checks=completed)};compose.waitForIdle()
        compose.onNodeWithText(label).assertDoesNotExist()
        compose.runOnIdle {mounted=false};compose.waitForIdle()
        compose.runOnIdle {mounted=true};compose.waitForIdle()
        compose.onNodeWithText(label).assertDoesNotExist()
        compose.runOnIdle {book=base;motion=false};compose.waitForIdle()
        compose.runOnIdle {book=base.copy(checks=completed)};compose.waitForIdle()
        compose.onNodeWithText(label).assertDoesNotExist()
    }
}
