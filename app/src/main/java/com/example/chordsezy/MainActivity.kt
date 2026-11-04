package com.example.chordsezy

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.chordsezy.music.Key
import com.google.android.material.card.MaterialCardView

/** Screen 1: choose the chord family (the key / root note). */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        applySystemBarPadding(findViewById(R.id.main))

        val grid = findViewById<GridLayout>(R.id.noteGrid)
        Key.ALL.forEachIndexed { index, key ->
            grid.addView(noteCard(index, key))
        }
    }

    private fun noteCard(index: Int, key: Key): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(16f)
            setCardBackgroundColor(getColor(R.color.surface))
            strokeColor = getColor(R.color.surface_high)
            strokeWidth = dp(1)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, ChordsActivity::class.java)
                        .putExtra(ChordsActivity.EXTRA_KEY, index)
                )
            }
            layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = dp(96)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        column.addView(TextView(this).apply {
            text = key.label
            textSize = 30f
            setTextColor(getColor(R.color.text_primary))
            gravity = Gravity.CENTER
        })
        val alt = Key.ALT_NAMES[key.tonic.pitchClass]
        column.addView(TextView(this).apply {
            text = if (alt != null) "also $alt" else "major key"
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            gravity = Gravity.CENTER
        })
        card.addView(column, android.widget.FrameLayout.LayoutParams(-1, -1))
        return card
    }
}
