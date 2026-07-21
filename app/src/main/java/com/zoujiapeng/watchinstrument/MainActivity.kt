package com.zoujiapeng.watchinstrument

import android.content.Intent
import android.os.Bundle
import com.zoujiapeng.watchinstrument.model.InstrumentType
import com.zoujiapeng.watchinstrument.ui.HomeScreenView

class MainActivity : BaseWatchActivity(), HomeScreenView.Listener {
    private lateinit var homeView: HomeScreenView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeView = HomeScreenView(this, this)
        setContentView(homeView)
    }

    override fun onResume() {
        super.onResume()
        homeView.setHighlighted(appPreferences.lastInstrument)
    }

    override fun onInstrumentSelected(instrument: InstrumentType) {
        appPreferences.lastInstrument = instrument
        startActivity(
            Intent(this, InstrumentActivity::class.java)
                .putExtra(InstrumentActivity.EXTRA_INSTRUMENT, instrument.id),
        )
    }

    override fun onRecordingsSelected() {
        startActivity(Intent(this, RecordingsActivity::class.java))
    }

    override fun onMetronomeSelected() {
        startActivity(Intent(this, MetronomeActivity::class.java))
    }

    override fun onSettingsSelected() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
