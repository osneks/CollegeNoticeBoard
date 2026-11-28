package com.example.collegenoticeboard.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.collegenoticeboard.ui.theme.CollegeNoticeBoardTheme
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.collegenoticeboard.ui.main.MainScreen]. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testAppTitleDisplayed() {
        composeTestRule.setContent {
            CollegeNoticeBoardTheme {
                MainScreen(onItemClick = {})
            }
        }
        composeTestRule.onNodeWithText("College Notice Board").assertIsDisplayed()
    }

    @Test
    fun testSearchFieldDisplayed() {
        composeTestRule.setContent {
            CollegeNoticeBoardTheme {
                MainScreen(onItemClick = {})
            }
        }
        composeTestRule.onNodeWithText("Search all notices...").assertIsDisplayed()
    }

    @Test
    fun testFilterButtonDisplayed() {
        composeTestRule.setContent {
            CollegeNoticeBoardTheme {
                MainScreen(onItemClick = {})
            }
        }
        composeTestRule.onNodeWithContentDescription("Filter Menu").assertIsDisplayed()
    }

    @Test
    fun testRefreshButtonDisplayed() {
        composeTestRule.setContent {
            CollegeNoticeBoardTheme {
                MainScreen(onItemClick = {})
            }
        }
        composeTestRule.onNodeWithContentDescription("Refresh").assertIsDisplayed()
    }

    @Test
    fun testFilterMenuOpensOnClick() {
        composeTestRule.setContent {
            CollegeNoticeBoardTheme {
                MainScreen(onItemClick = {})
            }
        }
        composeTestRule.onNodeWithContentDescription("Filter Menu").performClick()
        composeTestRule.onNodeWithText("All Notices").assertIsDisplayed()
        composeTestRule.onNodeWithText("Urgent & Important").assertIsDisplayed()
        composeTestRule.onNodeWithText("Admission").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exam").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tender").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hidden Notices").assertIsDisplayed()
    }
}
