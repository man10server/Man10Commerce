package red.man10.man10commerce.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLNonTransientConnectionException
import java.sql.SQLRecoverableException
import java.util.concurrent.atomic.AtomicInteger
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

    /** Throttles the "database is down" log so a broken DB cannot spam the console. */
    private const val FAILURE_LOG_INTERVAL_MS = 30_000L

    /**
     * Consecutive connection failures after which the pool is treated as down.
     * From then on [connection] returns immediately instead of waiting
     * `connectionTimeout` each: the write queue is serialized, so a backlog of
     * blocked jobs keeps the shop broken long after MySQL itself has recovered.
     */
    private const val FAILURE_THRESHOLD = 2

    /** How often the probe thread checks whether the database came back. */
    private const val PROBE_INTERVAL_MS = 1_000L

    private const val DEFAULT_QUERY_TIMEOUT_SECONDS = 3

    @Volatile
    private var dataSource: HikariDataSource? = null

    @Volatile
    private var lastFailureLoggedAt = 0L

    @Volatile
    private var logger: Logger = Logger.getLogger(POOL_NAME)

    @Volatile
    private var probeThread: Thread? = null

    /** Seconds a single statement may run before it is aborted. 0 disables the limit. */
    @Volatile
    var queryTimeoutSeconds = DEFAULT_QUERY_TIMEOUT_SECONDS
        private set

    private val consecutiveFailures = AtomicInteger(0)

    /** false while the database is known to be unreachable. */
    val isReady: Boolean
        get() = dataSource?.isClosed == false && consecutiveFailures.get() < FAILURE_THRESHOLD

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

        queryTimeoutSeconds = config.getInt("mysql.queryTimeoutSeconds", DEFAULT_QUERY_TIMEOUT_SECONDS)
            .coerceAtLeast(0)

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
            consecutiveFailures.set(0)
            dataSource = source
            startProbeThread()
            logger.info("[$POOL_NAME] connection pool ready (maximumPoolSize=${hikari.maximumPoolSize})")
            true
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "[$POOL_NAME] failed to initialise the connection pool: ${e.message}", e)
            false
        }
    }

    fun shutdown() {
        stopProbeThread()
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
     * Borrows a connection.
     *
     * Once the database is known to be down this returns null without waiting:
     * blocking here would stall the serialized write thread and keep the backlog
     * growing. Recovery is detected by [probeThread], never by a caller.
     *
     * @return a pooled connection, or null when the database is unreachable.
     */
    fun connection(): Connection? {
        val source = dataSource
        if (source == null || source.isClosed) {
            logFailure("connection requested before the pool was initialised")
            return null
        }

        if (consecutiveFailures.get() >= FAILURE_THRESHOLD) return null

        return try {
            // HikariCP itself keeps retrying until connectionTimeout elapses,
            // so there is nothing to gain from retrying around it.
            source.connection.also { onAcquired() }
        } catch (e: SQLException) {
            onFailed(e)
            null
        }
    }

    /**
     * Reports a statement level failure. A connection that dies mid-query is the
     * usual first sign of an outage, so it has to count towards the threshold as
     * well - otherwise the breaker only trips once callers start timing out on
     * acquisition, several seconds later.
     */
    fun reportFailure(e: SQLException) {
        if (!isConnectionError(e)) return
        onFailed(e)
    }

    private fun isConnectionError(e: SQLException): Boolean {
        if (e is SQLNonTransientConnectionException || e is SQLRecoverableException) return true
        return e.sqlState?.startsWith("08") == true
    }

    private fun onAcquired() {
        if (consecutiveFailures.getAndSet(0) >= FAILURE_THRESHOLD) {
            logger.info("[$POOL_NAME] the database is reachable again")
        }
    }

    private fun onFailed(e: SQLException?) {
        if (consecutiveFailures.incrementAndGet() == FAILURE_THRESHOLD) {
            lastFailureLoggedAt = System.currentTimeMillis()
            logger.severe(
                "[$POOL_NAME] the database is unreachable, requests fail immediately until it recovers: ${e?.message}"
            )
            return
        }
        logFailure("could not obtain a connection: ${e?.message}")
    }

    /**
     * Checks in the background whether a downed database came back, so that no
     * request thread ever pays the `connectionTimeout` wait.
     */
    private fun startProbeThread() {
        probeThread = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(PROBE_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@Thread
                }

                if (consecutiveFailures.get() < FAILURE_THRESHOLD) continue

                val source = dataSource ?: continue
                if (source.isClosed) continue

                try {
                    source.connection.use { it.isValid(1) }
                    onAcquired()
                } catch (e: SQLException) {
                    // still down, try again on the next tick
                }
            }
        }, "Man10Commerce-DbProbe").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopProbeThread() {
        probeThread?.interrupt()
        probeThread = null
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
