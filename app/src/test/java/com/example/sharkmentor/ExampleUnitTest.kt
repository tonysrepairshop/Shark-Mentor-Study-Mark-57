package com.example.sharkmentor

import com.example.sharkmentor.ui.navigation.Screen
import org.junit.Test
import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun bottomBarItems_containsNoNullElements() {
        val items = Screen.bottomBarItems
        assertNotNull(items)
        assertEquals(6, items.size)
        items.forEach { item ->
            assertNotNull("Bottom bar item should not be null", item)
            assertNotNull("Screen icon should not be null", item.icon)
            assertNotNull("Screen route should not be null", item.route)
        }
    }
}