package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.DentalSearchBar
import com.example.ui.components.DentalSectionHeader
import com.example.ui.components.DentalStatCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingStateView
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class DentalScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun screenshot_loading_state() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Surface {
                    LoadingStateView(message = "در حال بارگذاری لیست بیماران...")
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/loading_state.png")
    }

    @Test
    fun screenshot_empty_state() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Surface {
                    EmptyStateView(
                        title = "پرونده‌ای یافت نشد",
                        subtitle = "برای ثبت اولین پرونده بیمار، دکمه «افزودن بیمار جدید» را لمس کنید.",
                        actionLabel = "افزودن بیمار جدید",
                        onAction = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/empty_state.png")
    }

    @Test
    fun screenshot_error_state() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Surface {
                    ErrorStateView(
                        message = "خطا در ارتباط با پایگاه داده مطب",
                        onRetry = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/error_state.png")
    }

    @Test
    fun screenshot_stats_and_search() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DentalSectionHeader(
                            title = "خلاصه مالی و آمار امروز",
                            subtitle = "گزارش وضعیت مطب",
                            actionText = "مشاهده جزئیات",
                            onAction = {}
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DentalStatCard(
                                title = "دریافتی امروز",
                                value = "۱۴,۵۰۰,۰۰۰ تومان",
                                modifier = Modifier.weight(1f)
                            )
                            DentalStatCard(
                                title = "نوبت‌های فعال",
                                value = "۸ بیمار",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        DentalSearchBar(
                            query = "علی رضایی",
                            onQueryChange = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/stats_and_search.png")
    }
}
