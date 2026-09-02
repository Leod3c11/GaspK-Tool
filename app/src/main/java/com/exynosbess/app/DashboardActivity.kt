package com.exynosbess.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout

class DashboardActivity : Activity() {

    private lateinit var partitionCard:
        LinearLayout

    private lateinit var advancedCard:
        LinearLayout


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_dashboard
        )

        partitionCard =
            findViewById(
                R.id.dashboardPartitionCard
            )

        advancedCard =
            findViewById(
                R.id.dashboardAdvancedCard
            )

        partitionCard.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    PartitionManagerActivity::class.java
                )
            )
        }

        advancedCard.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                )
            )
        }
    }
}
