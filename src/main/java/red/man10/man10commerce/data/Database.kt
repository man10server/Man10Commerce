package red.man10.man10commerce.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Owns the HikariCP pool used by every database access in this plugin.
 *
 * [setup] fails fast: when no connection can be established within
 * `mysql.pool.initializationFailTimeoutMs` it returns false so the caller can
 * disable the plugin instead of running with a half-initialised state.
 * Once the pool is up, Hikari takes care of reconnecting, so runtime failures
 * are reported to the caller rather than being swallowed.
 */
object Database {

    private const val POOL_NAME = "Man10Commerce"

    /**
     * Attempts made to borrow a connection before giving up. HikariCP already
     * waits `connectionTimeout` internally, so the worst case a caller can block
     * is ATTEMPTS * connectionTimeout. Keep it small: a stalled write thread
     * backs up every purchase behind it.
     */
    private const val ACQUIRE_ATTEMPTS = 2
    private const val ACQUIRE_RETRY_INTERVAL_MS = 200L

    /** Throttles the "database is down" log so a broken DB cannot spam the console. */
    private const val FAILURE_LOG_INTERVAL_MS = 30_000L

    @Volatile
    private var dataSource: HikariDataSource? = null

    @Volatile
    private var lastFailureLoggedAt = 0L

    @Volatile
    private var logger: Logger = Logger.getLogger(POOL_NAME)

    val isReady: Boolean
        get() = dataSource?.isClosed == false

    fun setup(plugin: JavaPlugin): Boolean {
        logger = plugin.logger
        shutdown()

        val config = plugin.config
        val host = config.getString("mysql.host")
        val port = config.getString("mysql.port") ?: "3306"
        val database = config.getString("mysql.db")
        val user = config.getString("mysql.user")
        val password = config.getString("mysql.pass") ?: ""

        if (host.isNullOrBlank() || database.isNullOrBlank() || user.isNullOrBlank()) {
            logger.severe("mysql.host / mysql.db / mysql.user are not set in config.yml")
            return false
        }

        val driverClass = resolveDriverClass()
        if (driverClass == null) {
            logger.severe("No MySQL JDBC driver on the classpath (expected com.mysql.cj.jdbc.Driver)")
            return false
        }

        val hikari = HikariConfig().apply {
            poolName = POOL_NAME
            jdbcUrl = buildJdbcUrl(plugin, host, port, database)
            username = user
            this.password = password
            driverClassName = driverClass

            maximumPoolSize = config.getInt("mysql.pool.maximumPoolSize", 10)
            minimumIdle = config.getInt("mysql.pool.minimumIdle", 2)
            connectionTimeout = config.getLong("mysql.pool.connectionTimeoutMs", 3_000)
            validationTimeout = config.getLong("mysql.pool.validationTimeoutMs", 3_000)
            idleTimeout = config.getLong("mysql.pool.idleTimeoutMs", 600_000)
            maxLifetime = config.getLong("mysql.pool.maxLifetimeMs", 1_740_000)
            keepaliveTime = config.getLong("mysql.pool.keepaliveTimeMs", 300_000)
            leakDetectionThreshold = config.getLong("mysql.pool.leakDetectionThresholdMs", 20_000)
            initializationFailTimeout = config.getLong("mysql.pool.initializationFailTimeoutMs", 10_000)

            // Server side prepared statements plus a client side cache: every query in
            // this plugin is a prepared statement, so the whole workload benefits.
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("cacheServerConfiguration", "true")
            addDataSourceProperty("elideSetAutoCommits", "true")
            addDataSourceProperty("maintainTimeStats", "false")
        }

        return try {
            val source = HikariDataSource(hikari)
            source.connection.use { connection ->
                if (!connection.isValid(3)) throw SQLException("Connection validation failed")
            }
            dataSource = source
            logger.info("[$POOL_NAME] connection pool ready (maximumPoolSize=${hikari.maximumPoolSize})")
            true
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "[$POOL_NAME] failed to initialise the connection pool: ${e.message}", e)
            false
        }
    }

    fun shutdown() {
        val source = dataSource ?: return
        dataSource = null
        try {
            source.close()
            logger.info("[$POOL_NAME] connection pool closed")
        } catch (e: Exception) {
            logger.log(Level.WARNING, "[$POOL_NAME] failed to close the connection pool: ${e.message}", e)
        }
    }

    /**
     * Borrows a connection, retrying transient acquisition failures.
     * Retrying here is safe because no statement has been executed yet.
     *
     * @return a pooled connection, or null when the database is unreachable.
     */
    fun connection(): Connection? {
        val source = dataSource
        if (source == null || source.isClosed) {
            logFailure("connection requested before the pool was initialised")
            return null
        }

        var last: SQLException? = null
        repeat(ACQUIRE_ATTEMPTS) { attempt ->
            try {
                return source.connection
            } catch (e: SQLException) {
                last = e
                if (attempt < ACQUIRE_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(ACQUIRE_RETRY_INTERVAL_MS * (attempt + 1))
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return null
                    }
                }
            }
        }

        logFailure("could not obtain a connection after $ACQUIRE_ATTEMPTS attempts: ${last?.message}")
        return null
    }

    /** Reports a pool level failure at most once per [FAILURE_LOG_INTERVAL_MS]. */
    private fun logFailure(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastFailureLoggedAt < FAILURE_LOG_INTERVAL_MS) return
        lastFailureLoggedAt = now
        logger.severe("[$POOL_NAME] $message")
    }

    private fun buildJdbcUrl(plugin: JavaPlugin, host: String, port: String, database: String): String {
        val parameters = linkedMapOf<String, String>()
        parameters["useSSL"] = plugin.config.getBoolean("mysql.useSSL", false).toString()

        val extra = plugin.config.getConfigurationSection("mysql.properties")
        if (extra == null) {
            parameters["allowPublicKeyRetrieval"] = "true"
            parameters["rewriteBatchedStatements"] = "true"
        } else {
            for (key in extra.getKeys(false)) {
                val value = extra.get(key) ?: continue
                parameters[key] = value.toString()
            }
        }

        val query = parameters.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "jdbc:mysql://$host:$port/$database?$query"
    }

    private fun resolveDriverClass(): String? {
        for (candidate in arrayOf("com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver")) {
            try {
                Class.forName(candidate, true, Database::class.java.classLoader)
                return candidate
            } catch (ignored: ClassNotFoundException) {
                // try the next candidate
            }
        }
        return null
    }
}
