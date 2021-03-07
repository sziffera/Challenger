package com.sziffer.challenger.user

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.sziffer.challenger.R
import com.sziffer.challenger.databinding.ActivityBodyFatCalculatorBinding
import com.sziffer.challenger.utils.getStringFromNumber
import kotlin.math.log

class BodyFatCalculatorActivity : AppCompatActivity() {

    private var height = 0
    private var weight = 0
    private var neck = 0
    private var hip = 0
    private var waist = 0
    private lateinit var userManager: UserManager

    private lateinit var binding: ActivityBodyFatCalculatorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBodyFatCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userManager = UserManager(this)

        binding.femaleRadioButton.isChecked = userManager.isFemale
        binding.maleRadioButton.isChecked = !userManager.isFemale

        if (userManager.isFemale) {
            setViewForFemale()
        } else {
            setViewForMale()
        }

        binding.femaleRadioButton.setOnCheckedChangeListener { _, isChecked ->
            userManager.isFemale = isChecked
            if (isChecked) {
                setViewForFemale()
            } else {
                setViewForMale()
            }
            calculateBmi()
        }

        if (userManager.bmi != 0f)
            binding.bmiIndexTextView.text = getStringFromNumber(1, userManager.bmi)
        if (userManager.bodyFat != 0f) {
            binding.bodyFatTextView.text = "${getStringFromNumber(1, userManager.bodyFat)}%"
            binding.bodyFatCategoryTextView.text = getBodyFatCategory(userManager.bodyFat.toInt())
        }


        binding.calculateBodyFatButton.setOnClickListener {
            calculateBodyFat()
        }

    }

    private fun setViewForFemale() {
        with(binding) {
            hipInfoTextView.visibility = View.VISIBLE
            hipEditTextNumberDecimal.visibility = View.VISIBLE
            waistInfoTextView.text = getString(R.string.waistfemale)
        }
    }

    private fun setViewForMale() {
        with(binding) {
            hipInfoTextView.visibility = View.GONE
            hipEditTextNumberDecimal.visibility = View.GONE
            waistInfoTextView.text = getString(R.string.waistmale)
        }
    }

    /** calculate body fat estimation based on US Navy formula */
    @SuppressLint("SetTextI18n")
    private fun calculateBodyFat() {

        try {

            with(binding) {

                height = heightEditTextNumberDecimal.text.toString().toInt()
                weight = weightEditTextNumberDecimal.text.toString().toInt()
                neck = neckEditTextNumberDecimal.text.toString().toInt()
                if (userManager.isFemale) {
                    hip = hipEditTextNumberDecimal.text.toString().toInt()
                }
                waist = waistEditTextNumberDecimal.text.toString().toInt()


                if (height == 0) {
                    heightEditTextNumberDecimal.startAnimation(
                        AnimationUtils.loadAnimation(
                            this@BodyFatCalculatorActivity, R.anim.shake
                        )
                    )
                    return
                }
                if (weight == 0) {
                    weightEditTextNumberDecimal.startAnimation(
                        AnimationUtils.loadAnimation(
                            this@BodyFatCalculatorActivity, R.anim.shake
                        )
                    )
                    return
                }
                if (neck == 0) {
                    neckEditTextNumberDecimal.startAnimation(
                        AnimationUtils.loadAnimation(
                            this@BodyFatCalculatorActivity, R.anim.shake
                        )
                    )
                    return
                }
                if (hip == 0 && userManager.isFemale) {
                    hipEditTextNumberDecimal.startAnimation(
                        AnimationUtils.loadAnimation(
                            this@BodyFatCalculatorActivity, R.anim.shake
                        )
                    )
                    return
                }
                if (waist == 0) {
                    waistEditTextNumberDecimal.startAnimation(
                        AnimationUtils.loadAnimation(
                            this@BodyFatCalculatorActivity, R.anim.shake
                        )
                    )
                    return
                }
            }

            val bodyFat: Double =
                if (userManager.isFemale) {

                    495 / (1.29579 - 0.35004 * log(
                        (waist + hip - neck).toDouble(),
                        10.0
                    ) + 0.221 * log(height.toDouble(), 10.0)) - 450


                    /*
                    163.205 * log((waist + hip - neck).toDouble(),
                        10.0) - 97.684 * log(height.toDouble(),10.0) - 78.387

                     */
                } else {

                    495 / (1.0324 - 0.19077 * log(
                        (waist - neck).toDouble(),
                        10.0
                    ) + 0.15456 * log(height.toDouble(), 10.0)
                            ) - 450
                    /*
                    86.010 * log((waist - neck).toDouble(),
                        10.0) - 70.041 * log(height.toDouble(),10.0) + 36.76

                     */
                }

            userManager.bodyFat = bodyFat.toFloat()
            userManager.bmi = calculateBmi()
            binding.bmiIndexTextView.text = getStringFromNumber(1, calculateBmi())
            binding.bodyFatTextView.text = "${getStringFromNumber(1, bodyFat)}%"
            binding.bodyFatCategoryTextView.text = getBodyFatCategory(bodyFat.toInt())

        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }

    }

    private fun calculateBmi(): Float {
        val heightInMetres: Double = height.div(100.0)
        return weight.div(heightInMetres * heightInMetres).toFloat()
    }

    private fun getBodyFatCategory(fat: Int): String {

        if (userManager.isFemale) {
            return when (fat) {
                in 10..13 -> getString(R.string.essential_fat)
                in 14..20 -> getString(R.string.athletic)
                in 21..24 -> getString(R.string.fit)
                in 25..31 -> getString(R.string.acceptable)
                else -> getString(R.string.obese)
            }
        } else {
            return when (fat) {
                in 2..5 -> getString(R.string.essential_fat)
                in 6..13 -> getString(R.string.athletic)
                in 14..17 -> getString(R.string.fit)
                in 18..25 -> getString(R.string.acceptable)
                else -> getString(R.string.obese)
            }
        }
    }

}