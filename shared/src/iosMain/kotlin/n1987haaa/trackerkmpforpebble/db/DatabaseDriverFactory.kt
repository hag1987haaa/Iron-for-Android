package n1987haaa.trackerkmpforpebble.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class DatabaseDriverFactory {
    fun createDriver(): SqlDriver {
        return NativeSqliteDriver(PebbleTrackerDatabase.Schema, "pebble_tracker.db")
    }
}
