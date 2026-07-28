package red.man10.man10commerce.data

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Man10Commerce.Companion.getDisplayName
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Utility
import red.man10.man10commerce.Utility.format
import red.man10.man10commerce.Utility.sendMsg
import java.io.File
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference

data class OrderData(
    var id: Int,
    var itemID: Int,
    var price: Double,
    var amount: Int,
    var date: Date?,
    var seller: UUID,
    var isOP: Boolean,
    var item: ItemStack
)

class Category{

    companion object{
        const val NOT_CATEGORIZED = "not"
    }

    lateinit var categoryIcon : ItemStack

    var material = mutableListOf<Material>()
    var displayName = mutableListOf<String>()
    var customModelData = mutableListOf<Int>()
}

object Transaction {

    private const val ORDER_COLUMNS = "id, item_id, price, amount, date, uuid, is_op"
    private const val ORDER_COLUMNS_ALIASED = "o.id, o.item_id, o.price, o.amount, o.date, o.uuid, o.is_op"

    private const val DB_ERROR_MESSAGE = Utility.DB_ERROR_MESSAGE

    /** How long [stop] waits for queued writes to finish before giving up. */
    private const val WRITE_DRAIN_TIMEOUT_MS = 3_000L

    /**
     * Writes stay on a single thread so buy/sell/close remain serialized, while
     * read-only menu queries run on [readExecutor].
     */
    private val writeQueue = LinkedBlockingQueue<(MySQLManager)->Unit>()

    private val sql = MySQLManager("Man10Commerce")

    @Volatile
    private var writeThread: Thread? = null

    @Volatile
    private var readExecutor: ExecutorService? = null

    private val itemDictionary = ConcurrentHashMap<Int,ItemStack>()//アイテムIDとItemStackの辞書

    private val minPriceCache = AtomicReference<CachedOrders?>(null)

    @Volatile
    private var minPriceCacheMillis = 5_000L

    val categories = ConcurrentHashMap<String,Category>()

    private class CachedOrders(val orders: List<OrderData>, val createdAt: Long)

    fun setup(){
        loadCategoryData()
        stop()

        minPriceCacheMillis = plugin.config.getLong("minPriceCacheSeconds", 5) * 1000
        val readThreads = plugin.config.getInt("readThreads", 4).coerceAtLeast(1)

        invalidateMinPriceCache()

        readExecutor = Executors.newFixedThreadPool(readThreads) { runnable ->
            Thread(runnable, "Man10Commerce-Read").apply { isDaemon = true }
        }
        writeThread = Thread({ runWriteQueue() }, "Man10Commerce-Write").apply {
            isDaemon = true
            start()
        }

        asyncLoadItemDictionary()
        asyncCheckExpired()
        asyncLoadMinPriceItems()
    }

    fun stop(){
        val thread = writeThread
        writeThread = null

        if (thread != null){
            val deadline = System.currentTimeMillis() + WRITE_DRAIN_TIMEOUT_MS
            while (writeQueue.isNotEmpty() && System.currentTimeMillis() < deadline){
                try {
                    Thread.sleep(20)
                }catch (e:InterruptedException){
                    Thread.currentThread().interrupt()
                    break
                }
            }
            if (writeQueue.isNotEmpty()){
                Bukkit.getLogger().warning("Man10Commerce: ${writeQueue.size}件の書き込みを処理できませんでした")
            }
            thread.interrupt()
        }

        readExecutor?.shutdownNow()
        readExecutor = null
    }

    ////////////////////////////////
    //      アイテムを買う
    ///////////////////////////////
    fun asyncBuy(p:Player, itemID: Int, orderID:Int, callback: (Boolean) -> Unit){

        //インベントリの参照はメインスレッドでしか安全に行えないので、キューに積む前に確認する
        Utility.sync {

            if (p.inventory.firstEmpty() == -1){
                sendMsg(p,"§cインベントリに空きがありません")
                callback(false)
                return@sync
            }

            asyncWrite { sql-> syncBuy(p, orderID, sql, callback) }
        }
    }

    private fun syncBuy(p:Player, orderID:Int, sql:MySQLManager, callback: (Boolean) -> Unit){

        val rows = sql.query("SELECT $ORDER_COLUMNS FROM order_table WHERE id = ?", orderID){ readOrders(it) }

        if (rows == null){
            sendMsg(p, DB_ERROR_MESSAGE)
            callback(false)
            return
        }

        val order = rows.firstOrNull()

        if (order == null){
            sendMsg(p,"§cすでに売り切れです！")
            callback(false)
            return
        }

        val totalPrice = order.price*order.amount

        val item = order.item.clone()
        item.amount = order.amount

        //お金関連の処理
        if (!Man10Commerce.vault.withdraw(p,totalPrice)){
            sendMsg(p,"§c電子マネーのお金が足りません(必要なお金:${format(totalPrice)}円)")
            callback(false)
            return
        }

        //出品の削除が成功して初めて購入が確定する
        if (!order.isOP){
            val deleted = sql.execute("DELETE FROM order_table WHERE id = ?", order.id)

            if (deleted != 1){
                //購入失敗による返金
                Man10Commerce.vault.deposit(p,totalPrice)

                if (deleted == MySQLManager.FAILED){
                    sendMsg(p,"${Man10Commerce.prefix}§cセンターにアクセスができませんでした。もう一度購入し直してください")
                }else{
                    sendMsg(p,"§cすでに売り切れです！")
                }
                callback(false)
                return
            }
            invalidateMinPriceCache()
        }

        Log.buyLog(p,order,item)

        Man10Commerce.bank.deposit(order.seller,totalPrice,"SellItemOnMan10Commerce","Amanzonの売り上げ (${getDisplayName(item).take(7)} ${order.price}円x${order.amount}個)")

        Utility.giveItem(p,item)

        sendMsg(p,"§a${format(totalPrice)}円で購入しました")
        callback(true)
    }

    /////////////////////////////
    //      販売する
    /////////////////////////////
    fun asyncSell(p:Player, item:ItemStack, price:Double, callback:(Boolean)->Unit, isOP: Boolean = false){

        asyncWrite {sql ->

            val sellCount = UserData.getSellCount(p.uniqueId, sql)

            if (sellCount == null){
                sendMsg(p, DB_ERROR_MESSAGE)
                callback(false)
                return@asyncWrite
            }

            if (sellCount>Man10Commerce.maxItems){
                sendMsg(p,"§c出品数上限に達しています")
                callback(false)
                return@asyncWrite
            }

            if (price<Man10Commerce.minPrice){
                sendMsg(p,"§c単価は${format(Man10Commerce.minPrice)}円以上にしてください。")
                callback(false)
                return@asyncWrite
            }

            if (price>Man10Commerce.maxPrice){
                sendMsg(p,"§c単価は${format(Man10Commerce.maxPrice)}円未満にしてください。")
                callback(false)
                return@asyncWrite
            }

            val meta = item.itemMeta

            if (meta != null && meta is org.bukkit.inventory.meta.Damageable && meta.hasDamage()){
                sendMsg(p,"§c§l耐久値が削れているので出品できません！")
                callback(false)
                return@asyncWrite
            }

            if (price != price.toInt().toDouble()){
                sendMsg(p,"§c§l少数以下の値段設定はできません")
                callback(false)
                return@asyncWrite
            }

            val name = getDisplayName(item)

            if (item.hasItemMeta()){
                if (Man10Commerce.disableItems.contains(ChatColor.stripColor(name))){
                    sendMsg(p,"　§cこのアイテムは販売できません")
                    callback(false)
                    return@asyncWrite
                }
            }

            val id = syncRegisterItemDictionary(item,sql)

            if (id == null){
                sendMsg(p,"§c出品失敗！もう一度出品し直してみてください")
                callback(false)
                return@asyncWrite
            }

            val inserted = sql.execute(
                "INSERT INTO order_table (player, uuid, item_id, item_name, date, amount, price, is_op) " +
                        "VALUES (?, ?, ?, ?, now(), ?, ?, ?)",
                p.name, p.uniqueId, id, name, item.amount, price, if (isOP) 1 else 0
            )

            if (inserted != 1){
                sendMsg(p,"§c出品失敗！センターにアクセスできませんでした")
                callback(false)
                return@asyncWrite
            }

            sendMsg(p,"§a§l出品成功！")
            Log.sellLog(p,item,price,id)

            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.broadcast(
                    Component.text(
                        "${Man10Commerce.prefix}§f${name}§f(${item.amount}個)が§e§l単価${
                            format(price)
                        }円§fで出品されました！"
                    )) })

            //最安値のデータを読み直す
            invalidateMinPriceCache()
            asyncLoadMinPriceItems()

            callback(true)
        }
    }

    /////////////////////////////
    //      注文を取り消す
    ////////////////////////////
    fun asyncClose(p:Player, id:Int){
        asyncWrite {sql->

            val rows = sql.query("SELECT $ORDER_COLUMNS FROM order_table WHERE id = ?", id){ readOrders(it) }

            if (rows == null){
                sendMsg(p, DB_ERROR_MESSAGE)
                return@asyncWrite
            }

            val order = rows.firstOrNull()

            if (order == null){
                sendMsg(p,"§c取り消し失敗！注文が存在しない可能性があります")
                return@asyncWrite
            }

            //アイテムを返す前に必ず出品を消す。逆順だと削除に失敗したときに複製できてしまう
            val deleted = sql.execute("DELETE FROM order_table WHERE id = ?", id)

            if (deleted != 1){
                if (deleted == MySQLManager.FAILED){
                    sendMsg(p,"§c取り消し失敗！センターにアクセスできませんでした")
                }else{
                    sendMsg(p,"§c取り消し失敗！注文が存在しない可能性があります")
                }
                return@asyncWrite
            }

            invalidateMinPriceCache()

            val item = order.item.clone()
            item.amount = order.amount
            Utility.giveItem(p,item)

            Log.closeLog(p,order.itemID,item)
            sendMsg(p, "§c§l出品を取り下げました")
        }
    }

    /**
     * 出品するアイテムのitem_listのIDを返す。未登録なら登録してから返す。
     * @return アイテムID。登録に失敗した場合はnull
     */
    private fun syncRegisterItemDictionary(item:ItemStack, sql:MySQLManager):Int?{
        val one = item.asOne()

        itemDictionary.entries.firstOrNull { it.value.isSimilar(one) }?.let { return it.key }

        val name = getDisplayName(one)

        val base64 = try {
            Utility.itemToBase64(one)
        }catch (e:Exception){
            Bukkit.getLogger().warning("${name}のシリアライズに失敗しました ${e.message}")
            return null
        }

        val id = sql.insert(
            "INSERT INTO item_list (item_name, item_type, base64) VALUES (?, ?, ?)",
            name, one.type.name, base64
        )

        if (id == null){
            Bukkit.getLogger().warning("${name}の登録に失敗しました")
            return null
        }

        itemDictionary[id] = one
        return id
    }

    private fun asyncLoadItemDictionary(){
        asyncWrite { sql ->

            Bukkit.getLogger().info("アイテム辞書を読み込み開始")

            val loaded = sql.query("SELECT id, base64 FROM item_list"){ rs ->
                val map = HashMap<Int,ItemStack>()
                while (rs.next()) {
                    val id = rs.getInt("id")
                    try {
                        map[id] = Utility.itemFromBase64(rs.getString("base64"))
                    } catch (e : Exception) {
                        Bukkit.getLogger().warning("アイテムの読み込みに失敗しました id:${id} ${e.message}")
                    }
                }
                map
            }

            //読み込みに失敗したときに辞書を空にしてしまうと全メニューが壊れるので、古い内容を残す
            if (loaded == null){
                Bukkit.getLogger().severe("アイテム辞書の読み込みに失敗しました。以前の辞書をそのまま使用します")
                return@asyncWrite
            }

            itemDictionary.keys.retainAll(loaded.keys)
            itemDictionary.putAll(loaded)

            Bukkit.getLogger().info("アイテム辞書を読み込みました(${itemDictionary.size}件)")
        }
    }

    //非同期で最安値のリストを読む
    private fun asyncLoadMinPriceItems(){
        asyncWrite { sql ->
            syncGetMinPriceItems(sql)
        }
    }

    //1週間たったアイテムは期限切れとする
    private fun asyncCheckExpired(){
        asyncWrite { sql->

            val calendar = Calendar.getInstance()
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR,-7)
            calendar.set(Calendar.HOUR_OF_DAY,0)
            calendar.set(Calendar.MINUTE,0)
            calendar.set(Calendar.SECOND,0)
            calendar.set(Calendar.MILLISECOND,0)

            val updated = sql.execute(
                "UPDATE order_table SET expired = 1 WHERE expired = 0 AND is_op = 0 AND date < ?",
                Timestamp(calendar.timeInMillis)
            )

            if (updated == MySQLManager.FAILED){
                Bukkit.getLogger().warning("期限切れ出品の取り下げに失敗しました")
                return@asyncWrite
            }

            if (updated > 0) invalidateMinPriceCache()
            Bukkit.getLogger().info("1週間以上経った出品を${updated}件取り下げました")
        }
    }

    /**
     * アイテムごとの最安値のリストを引く(スレッドで呼ぶ)。
     * 全出品を読んでJava側で絞り込むと重いので、最安値の抽出はMySQLに任せる。
     * @return 取得できなかった場合はnull
     */
    fun syncGetMinPriceItems(sql:MySQLManager):List<OrderData>?{

        val cached = minPriceCache.get()
        if (cached != null && System.currentTimeMillis() - cached.createdAt < minPriceCacheMillis){
            return cached.orders
        }

        val rows = sql.query(
            "SELECT $ORDER_COLUMNS_ALIASED FROM order_table o " +
                    "JOIN (SELECT item_id, MIN(price) AS min_price FROM order_table " +
                    "      WHERE expired = 0 GROUP BY item_id) m " +
                    "  ON m.item_id = o.item_id AND m.min_price = o.price " +
                    "WHERE o.expired = 0 ORDER BY o.price"
        ){ readOrders(it) } ?: return null

        //同じ最安値の出品が複数あると結合結果に残るので、アイテムごとに1件へ絞る
        val list = rows.distinctBy { it.itemID }

        minPriceCache.set(CachedOrders(list, System.currentTimeMillis()))

        return list
    }

    //同じアイテムの全注文を取得(スレッドで呼ぶ)
    fun syncGetOneItemList(itemID:Int, sql: MySQLManager):List<OrderData>?{
        return sql.query(
            "SELECT $ORDER_COLUMNS FROM order_table WHERE expired = 0 AND item_id = ? ORDER BY price",
            itemID
        ){ readOrders(it) }
    }

    fun syncGetCategorizedList(categoryName: String,sql:MySQLManager):List<OrderData>?{

        val list = syncGetMinPriceItems(sql)?:return null
        val dic = getCategorizedDictionary(categoryName)

        return list.filter { dic.containsKey(it.itemID) }
    }

    fun syncGetSellerList(seller: UUID,sql: MySQLManager):List<OrderData>?{
        return sql.query("SELECT $ORDER_COLUMNS FROM order_table WHERE uuid = ?", seller){ readOrders(it) }
    }

    fun syncGetOfficialList(sql: MySQLManager):List<OrderData>?{
        return sql.query(
            "SELECT $ORDER_COLUMNS FROM order_table WHERE is_op = 1 AND expired = 0 ORDER BY price"
        ){ readOrders(it) }
    }

    /** 辞書に無いアイテムの出品は表示できないので読み飛ばす */
    private fun readOrders(rs: ResultSet):List<OrderData>{

        val list = mutableListOf<OrderData>()

        while (rs.next()){
            val itemID = rs.getInt("item_id")
            val item = itemDictionary[itemID]?:continue

            val seller = try {
                UUID.fromString(rs.getString("uuid"))
            }catch (e:Exception){
                Bukkit.getLogger().warning("出品者のUUIDが不正です id:${rs.getInt("id")}")
                continue
            }

            list.add(
                OrderData(
                    rs.getInt("id"),
                    itemID,
                    rs.getDouble("price"),
                    rs.getInt("amount"),
                    rs.getTimestamp("date"),
                    seller,
                    rs.getBoolean("is_op"),
                    item
                )
            )
        }

        return list
    }

    private fun invalidateMinPriceCache(){
        minPriceCache.set(null)
    }

    private fun getCategorizedDictionary(categoryName:String):Map<Int,ItemStack>{

        if (categoryName == Category.NOT_CATEGORIZED){
            return getNotCategorizedDictionary()
        }

        val category = categories[categoryName]?:return emptyMap()

        val isEmptyMaterial = category.material.isEmpty()
        val isEmptyDisplay = category.displayName.isEmpty()
        val isEmptyCMD = category.customModelData.isEmpty()

        return itemDictionary.filter { entry ->

            val item = entry.value
            val meta = item.itemMeta
            val cmd = if (meta==null || !meta.hasCustomModelData()) 0 else meta.customModelData
            val display = getDisplayName(item).replace("§[a-z0-9]".toRegex(), "")

            (isEmptyMaterial || item.type in category.material) &&
                    (isEmptyCMD || cmd in category.customModelData) &&
                    (isEmptyDisplay || category.displayName.any { display.contains(it) })
            }
    }

    //カテゴリー分けされてないアイテムを取得
    private fun getNotCategorizedDictionary():Map<Int,ItemStack>{

        val materials = mutableSetOf<Material>()
        val displays = mutableSetOf<String>()

        for (category in categories.values){
            materials.addAll(category.material)
            displays.addAll(category.displayName)
        }

        return itemDictionary.filter { item ->
                val display = getDisplayName(item.value).replace("§[a-z0-9]".toRegex(), "")
                !materials.contains(item.value.type) && (displays.filter { (display).contains(it) }).isEmpty()
        }
    }

    //  カテゴリーデータをよむ
    private fun loadCategoryData(){

        Bukkit.getLogger().info("カテゴリーデータの読み込み")

        categories.clear()

        val categoryFolder = File(plugin.dataFolder,File.separator+"categories")

        if (!categoryFolder.exists())categoryFolder.mkdir()

        val files = categoryFolder.listFiles()?.toMutableList()

        if (files == null){
            Bukkit.getLogger().info("カテゴリーファイルがありませんでした")
            return
        }

        for (file in files){

            if (!file.path.endsWith(".yml") || file.isDirectory)continue

            val yml = YamlConfiguration.loadConfiguration(file)
            val data = Category()

            val name = yml.getString("CategoryName")?:"none"

            data.customModelData = yml.getIntegerList("CustomModelData")
            data.displayName = yml.getStringList("DisplayName")

            val materialList = mutableListOf<Material>()

            for (m in yml.getStringList("Material")){
                try {
                    materialList.add(Material.valueOf(m))
                }catch (e:Exception){
                    Bukkit.getLogger().warning(e.message)
                }
            }

            data.material = materialList

            val icon = ItemStack(Material.valueOf(yml.getString("CategoryIconMaterial")?:"STONE"))
            val meta = icon.itemMeta
            meta.displayName(Component.text(yml.getString("CategoryIconTitle")?:"Title"))
            meta.setCustomModelData(yml.getInt("CategoryIconCMD"))
            icon.itemMeta = meta

            data.categoryIcon = icon

            Bukkit.getLogger().info("category:$name")

            categories[name] = data
        }

        Bukkit.getLogger().info("カテゴリーデータの読み込み完了")
    }

    /** メニューなどの読み取り専用の処理を並列で流す */
    fun async(process:(MySQLManager)->Unit){

        val executor = readExecutor

        if (executor == null || executor.isShutdown){
            Bukkit.getLogger().warning("Man10Commerce: 読み取りスレッドが停止しているためクエリを破棄しました")
            return
        }

        try {
            executor.execute {
                try {
                    process.invoke(sql)
                }catch (e:Exception){
                    Bukkit.getLogger().warning("Man10Commerce: 読み取り処理に失敗しました ${e.message}")
                    Bukkit.getLogger().warning(e.stackTraceToString())
                }
            }
        }catch (e:RejectedExecutionException){
            Bukkit.getLogger().warning("Man10Commerce: 読み取りスレッドが受け付けられませんでした")
        }
    }

    /** 取引を伴う処理。順序を保証するため必ず1スレッドで実行する */
    private fun asyncWrite(process:(MySQLManager)->Unit){
        writeQueue.add(process)
    }

    private fun runWriteQueue(){

        while (!Thread.currentThread().isInterrupted){

            try {
                writeQueue.take().invoke(sql)

            }catch (e:InterruptedException){
                Thread.currentThread().interrupt()
                return
            }catch (e:Exception){
                Bukkit.getLogger().warning("Man10Commerce: 書き込み処理に失敗しました ${e.message}")
                Bukkit.getLogger().warning(e.stackTraceToString())
            }
        }
    }
}
