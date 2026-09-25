package com.keenin.calbudget

import android.app.Application
import com.keenin.calbudget.data.BudgetRepository
import com.keenin.calbudget.data.ThemeSettings
import com.keenin.calbudget.data.db.AppDatabase

class CalBudgetApp : Application() {
    lateinit var repository: BudgetRepository
        private set

    lateinit var themeSettings: ThemeSettings
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BudgetRepository(AppDatabase.build(this))
        themeSettings = ThemeSettings(this)
    }
}
