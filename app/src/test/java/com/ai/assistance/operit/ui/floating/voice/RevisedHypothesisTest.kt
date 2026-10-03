package com.ai.assistance.operit.ui.floating.voice
import org.junit.Assert.*
import org.junit.Test
class RevisedHypothesisTest {
 @Test fun rewrittenPartialIsStillTheBusyUtteranceUntilFinal() {
  val gate = VoiceBargeInGate()
  gate.enterBargeInStopListen()
  gate.onRecognition("今天的故事", false, false)
  gate.resumeConversationListen(false)
  assertFalse(gate.onRecognition("从前有座山", false, true).sendToConversation)
  assertFalse(gate.onRecognition("从前有座山", true, true).sendToConversation)
  assertTrue(gate.onRecognition("你好", true, true).sendToConversation)
 }
}
