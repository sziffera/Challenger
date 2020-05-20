package com.sziffer.challenger


import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class ChallengeRecordingTest {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityTestRule(
        ChallengeRecorderActivity::class.java,
        false, false
    )

    @Rule
    @JvmField
    var mGrantPermissionRule =
        GrantPermissionRule.grant(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION"
        )

    private var mIdlingResource: IdlingResource? = null


    @Before
    fun registerIdlingResource() {


    }

    @Before
    fun setUp() {
        mActivityTestRule.launchActivity(
            Intent()
                .putExtra(ChallengeRecorderActivity.CREATED_CHALLENGE_INTENT, true)
                .putExtra(ChallengeRecorderActivity.AVG_SPEED, 7.0)
                .putExtra(ChallengeRecorderActivity.DISTANCE, 2000.0)
        )
    }

    @Test
    fun challengeRecordingTest() {

        val chip = onView(
            allOf(
                withId(R.id.cyclingChip), withText("cycling"),
                childAtPosition(
                    allOf(
                        withId(R.id.activityChooserChipGroup),
                        childAtPosition(
                            withId(R.id.firstStartView),
                            1
                        )
                    ),
                    0
                ),
                isDisplayed()
            )
        )
        chip.perform(click())

        val appCompatButton3 = onView(
            allOf(
                withId(R.id.firstStartButton), withText("Start"),
                childAtPosition(
                    allOf(
                        withId(R.id.firstStartView),
                        childAtPosition(
                            withClassName(`is`("android.widget.LinearLayout")),
                            3
                        )
                    ),
                    4
                ),
                isDisplayed()
            )
        )
        appCompatButton3.perform(click())

        //the recording must start after 6secs
        Thread.sleep(6000)

        onView(withId(R.id.differenceTextView)).check(ViewAssertions.matches(isDisplayed()))
        ViewActions.pressBack()


    }

    private fun childAtPosition(
        parentMatcher: Matcher<View>, position: Int
    ): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                        && view == parent.getChildAt(position)
            }
        }
    }

}
