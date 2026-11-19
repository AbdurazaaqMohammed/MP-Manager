package io.github.abdurazaaqmohammed.ui.editor.symbols

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.zerostudio.idea.widget.editor.symbolinput.v2.manager.SymbolDrawerManagerScreen
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettingsScreen
import io.github.abdurazaaqmohammed.MPManager.R
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity

/** A normal app Activity owns the V2 management surfaces and document pickers. */
class SymbolDrawerActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SymbolDrawerMigration.migrate(this)
        val settings = intent.getBooleanExtra(EXTRA_SETTINGS, false)
        val root = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val toolbar = com.google.android.material.appbar.MaterialToolbar(this)
        root.addView(toolbar, android.widget.LinearLayout.LayoutParams(-1, dpPx(64)))
        val screen = if (settings) SymbolDrawerSettingsScreen(this) else SymbolDrawerManagerScreen(this)
        root.addView(screen, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        setSupportActionBar(toolbar)
        setTitle(if (settings) R.string.editor_settings else R.string.edpref_bottom_bar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!intent.getBooleanExtra(EXTRA_SETTINGS, false))
            menu.add(0, SETTINGS, 0, R.string.settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == SETTINGS) {
            startActivity(android.content.Intent(this, SymbolDrawerActivity::class.java).putExtra(EXTRA_SETTINGS, true))
            return true
        }
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_SETTINGS = "symbol_drawer_settings"
        private const val SETTINGS = 1
    }
}
