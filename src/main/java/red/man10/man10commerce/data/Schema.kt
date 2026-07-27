package red.man10.man10commerce.data

import org.bukkit.Bukkit

/**
 * Creates the tables and the indexes the plugin relies on.
 *
 * MySQL has no `CREATE INDEX IF NOT EXISTS`, so every index is looked up in
 * information_schema first. Running this on an already migrated database is a
 * no-op.
 */
object Schema {

    private val TABLES = listOf(
        """
        CREATE TABLE IF NOT EXISTS item_list (
            id INT AUTO_INCREMENT,
            item_name VARCHAR(128) NULL,
            item_type VARCHAR(64) NULL,
            base64 LONGTEXT NULL,
            CONSTRAINT item_list_pk PRIMARY KEY (id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS order_table (
            id INT AUTO_INCREMENT,
            player VARCHAR(16) NULL,
            uuid VARCHAR(36) NULL,
            item_id INT NOT NULL,
            item_name VARCHAR(128) NULL,
            date DATETIME NULL,
            amount INT NULL,
            price DOUBLE NOT NULL,
            is_op TINYINT NOT NULL DEFAULT 0,
            expired TINYINT NOT NULL DEFAULT 0,
            CONSTRAINT order_table_pk PRIMARY KEY (id)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS log (
            id INT AUTO_INCREMENT,
            order_player VARCHAR(16) NULL,
            target_player VARCHAR(16) NULL,
            action VARCHAR(16) NULL,
            item_id INT NULL,
            item_name VARCHAR(128) NULL,
            amount INT NULL,
            price DOUBLE NULL,
            date DATETIME NULL,
            CONSTRAINT log_pk PRIMARY KEY (id)
        )
        """.trimIndent()
    )

    private val INDEXES = listOf(
        // Cheapest listing per item and "all listings of one item", both of which
        // read every active row of the table on the hottest code path.
        Index(
            "order_table", "idx_order_active_item_price",
            "CREATE INDEX idx_order_active_item_price ON order_table (expired, item_id, price, id)"
        ),
        // Amanzon Basic listing.
        Index(
            "order_table", "idx_order_official",
            "CREATE INDEX idx_order_official ON order_table (is_op, expired, price)"
        ),
        // Weekly expiry sweep.
        Index(
            "order_table", "idx_order_expire_sweep",
            "CREATE INDEX idx_order_expire_sweep ON order_table (expired, is_op, date)"
        ),
        // Seller lookups already have order_table_uuid_item_id_index on legacy
        // databases; recreate it for fresh installs.
        Index(
            "order_table", "order_table_uuid_item_id_index",
            "CREATE INDEX order_table_uuid_item_id_index ON order_table (uuid, item_id, is_op, expired)"
        )
    )

    private data class Index(val table: String, val name: String, val statement: String)

    /**
     * @return true when the schema is known to be up to date. A false result is
     * not fatal: the plugin still runs, only without the newer indexes.
     */
    fun migrate(sql: MySQLManager): Boolean {
        var success = true

        for (table in TABLES) {
            if (sql.execute(table) == MySQLManager.FAILED) {
                Bukkit.getLogger().warning("Failed to create a table, schema may be incomplete")
                success = false
            }
        }

        for (index in INDEXES) {
            when (exists(sql, index)) {
                true -> continue
                null -> {
                    success = false
                    continue
                }
                false -> {
                    if (sql.execute(index.statement) == MySQLManager.FAILED) {
                        Bukkit.getLogger().warning("Failed to create index ${index.name}")
                        success = false
                    } else {
                        Bukkit.getLogger().info("Created index ${index.name} on ${index.table}")
                    }
                }
            }
        }

        return success
    }

    /** null means the check itself failed. */
    private fun exists(sql: MySQLManager, index: Index): Boolean? {
        return sql.query(
            "SELECT 1 FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? LIMIT 1",
            index.table, index.name
        ) { it.next() }
    }
}
