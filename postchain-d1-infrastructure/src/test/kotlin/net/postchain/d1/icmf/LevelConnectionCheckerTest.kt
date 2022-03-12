package net.postchain.d1.icmf
//
//import org.junit.jupiter.api.Assertions.assertFalse
//import org.junit.jupiter.api.Test
//import kotlin.test.assertTrue
//
//class LevelConnectionCheckerTest {
//
//    @Test
//    fun happyHappy() {
//
//        val sourceLcc = LevelConnectionChecker(2, LevelConnectionChecker.NOT_LISTENER)
//        val listenerLcc = LevelConnectionChecker(1, LevelConnectionChecker.HIGH_LEVEL)
//        val bothSourceAndListenerLcc = LevelConnectionChecker(1, LevelConnectionChecker.MID_LEVEL)
//        val mockCc1 = MockConnectionChecker(false)
//
//        assertTrue(listenerLcc.shouldConnect(sourceLcc)) // Sort of self evident
//        assertTrue(listenerLcc.shouldConnect(bothSourceAndListenerLcc)) // B/c this "lcc1" listener is lower level
//        assertTrue(listenerLcc.shouldConnect(mockCc1)) // B/c this "lcc1" listener is lower level
//
//        assertFalse(sourceLcc.shouldConnect(listenerLcc)) // Source shouldn't connect to anything the way we've done it
//        assertFalse(sourceLcc.shouldConnect(bothSourceAndListenerLcc))
//        assertFalse(sourceLcc.shouldConnect(mockCc1))
//    }
//}