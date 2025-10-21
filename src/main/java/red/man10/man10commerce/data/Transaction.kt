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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

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

    private val blockingQueue = LinkedBlockingQueue<(MySQLManager)->Unit>()
    private var queueThread = Thread{ runBlockingQueue() }

    private val itemDictionary = ConcurrentHashMap<Int,ItemStack>()//アイテムIDとItemStackの辞書
    private var minPriceItems = mutableListOf<OrderData>()

    val categories = ConcurrentHashMap<String,Category>()

    fun setup(){
        loadCategoryData()

        if (queueThread.isAlive){
            queueThread.interrupt()
            queueThread = Thread{ runBlockingQueue() }
        }
        asyncLoadItemDictionary()
        asyncLoadMinPriceItems()
        asyncCheckExpired()
        queueThread.start()
    }

    fun stop(){
        queueThread.interrupt()
    }

    ////////////////////////////////
    //      アイテムを買う
    ///////////////////////////////
    fun asyncBuy(p:Player, itemID: Int, orderID:Int, callback: (Boolean) -> Unit){

        blockingQueue.add { sql->


            if (p.inventory.firstEmpty() == -1){
                sendMsg(p,"§cインベントリに空きがありません")
                callback(false)
                return@add
            }

            val rs = sql.query("select * from order_table where id=$orderID;")

            if (rs == null ||  !rs.next()){
                sendMsg(p,"§cすでに売り切れです！")
                callback(false)
                return@add
            }

            val amount = rs.getInt("amount")
            val date = rs.getDate("date")
            val price = rs.getDouble("price")
            val seller = UUID.fromString(rs.getString("uuid"))
            val isOp = rs.getBoolean("is_op")

            val data = OrderData(
                orderID,
                itemID,
                price,
                amount,
                date,
                seller,
                isOp,
                itemDictionary[itemID]!!
            )

            val totalPrice = price*amount

            rs.close()
            sql.close()

            val item = itemDictionary[itemID]?.clone()

            if (item == null){
                sendMsg(p,"§cすでに売り切れです")
                callback(false)
                return@add
            }

            item.amount = amount

            //お金関連の処理
            if (!Man10Commerce.vault.withdraw(p,totalPrice)){
                sendMsg(p,"§c電子マネーのお金が足りません(必要なお金:${format(totalPrice)}円)")
                callback(false)
                return@add
            }

            if (!isOp){
                val ret = sql.execute("DELETE FROM order_table where id=${orderID};")
                if (!ret){
                    sendMsg(p,"${Man10Commerce.prefix}§cセンターにアクセスができませんでした。もう一度購入し直してください")
                    //購入失敗による返金
                    Man10Commerce.vault.deposit(p,totalPrice)
                    callback(false)
                    return@add
                }
            }
            Log.buyLog(p,data,item)

            Man10Commerce.bank.deposit(seller!!,totalPrice,"SellItemOnMan10Commerce","Amanzonの売り上げ (${getDisplayName(item).take(7)} ${price}円x${amount}個)")

            p.inventory.addItem(item)

            sendMsg(p,"§a${format(totalPrice)}円で購入しました")
            callback(true)
        }
    }

    /////////////////////////////
    //      販売する
    /////////////////////////////
    fun asyncSell(p:Player, item:ItemStack, price:Double, callback:(Boolean)->Unit, isOP: Boolean = false){

        blockingQueue.add {sql ->

            if (UserData.getSellCount(p.uniqueId)>Man10Commerce.maxItems){
                sendMsg(p,"§c出品数上限に達しています")
                callback(false)
                return@add
            }

            if (price<Man10Commerce.minPrice){
                sendMsg(p,"§c単価は${format(Man10Commerce.minPrice)}円以上にしてください。")
                callback(false)
                return@add
            }

            if (price>Man10Commerce.maxPrice){
                sendMsg(p,"§c単価は${format(Man10Commerce.maxPrice)}円未満にしてください。")
                callback(false)
                return@add
            }

            val meta = item.itemMeta

            if (meta != null && meta is org.bukkit.inventory.meta.Damageable && meta.hasDamage()){
                sendMsg(p,"§c§l耐久値が削れているので出品できません！")
                callback(false)
                return@add
            }

            if (price != price.toInt().toDouble()){
                sendMsg(p,"§c§l少数以下の値段設定はできません")
                callback(false)
                return@add
            }

            val name = getDisplayName(item)

            if (item.hasItemMeta()){
                if (Man10Commerce.disableItems.contains(ChatColor.stripColor(name))){
                    sendMsg(p,"　§cこのアイテムは販売できません")
                    callback(false)
                    return@add
                }
            }

            syncRegisterItemDictionary(item,sql)

            var id : Int? = null
             itemDictionary.forEach{
                if (it.value.isSimilar(item)){
                    id = it.key
                }
            }

            if (id == null){
                sendMsg(p,"§c出品失敗！もう一度出品し直してみてください")
                callback(false)
                return@add
            }

            val ret = sql.execute(
                "INSERT INTO order_table " +
                        "(player, uuid, item_id, item_name, date, amount, price,is_op) " +
                        "VALUES ('${p.name}', '${p.uniqueId}', $id, '${MySQLManager.escapeStringForMySQL(name)}'," +
                        " now(), ${item.amount}, $price,${if (isOP) 1 else 0});"
            )

            if (ret){
                sendMsg(p,"§a§l出品成功！")
                Log.sellLog(p,item,price, id!!)

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    Bukkit.broadcast(
                        Component.text(
                            "${Man10Commerce.prefix}§f${name}§f(${item.amount}個)が§e§l単価${
                                format(price)
                            }円§fで出品されました！"
                        )) })

                //最安値のデータを読み直す
                asyncLoadMinPriceItems()

                callback(true)

            }else{
                sendMsg(p,"§c出品失敗！センターにアクセスできませんでした")
                callback(false)
            }
        }
    }

    /////////////////////////////
    //      注文を取り消す
    ////////////////////////////
    fun asyncClose(p:Player, id:Int){
        blockingQueue.add {sql->

            val rs = sql.query("select item_id,amount,is_op from order_table where id=${id};")?:return@add

            if (!rs.next()){
                sendMsg(p,"§c取り消し失敗！注文が存在しない可能性があります")
                return@add
            }

            val itemID = rs.getInt("item_id")
            val amount = rs.getInt("amount")

            val item = itemDictionary[itemID]?.clone()

            if (item == null){
                sendMsg(p,"§c取り消し失敗！注文が存在しない可能性があります")
                return@add
            }

            item.amount = amount
            p.inventory.addItem(item)

            sql.execute("DELETE FROM order_table where id=${id};")

            Log.closeLog(p,itemID,item)
            sendMsg(p, "§c§l出品を取り下げました")
        }
    }

    private fun syncRegisterItemDictionary(item:ItemStack, sql:MySQLManager){
        val one = item.asOne()

        if (itemDictionary.values.any{it.isSimilar(one)})return

        val name = getDisplayName(one)

        val insertResult = sql.execute(
            "INSERT INTO item_list " +
                    "(item_name, item_type, base64) VALUES ('${MySQLManager.escapeStringForMySQL(name)}', '${one.type}', '${Utility.itemToBase64(one)}');"
        )

        if (!insertResult){
            Bukkit.getLogger().warning("${name}の登録に失敗しました")
            return
        }

        val rs = sql.query("select id from item_list ORDER BY id DESC LIMIT 1;")

        if (rs == null || !rs.next()){
            Bukkit.getLogger().warning("${name}の登録に失敗しました")
            return
        }

        if (itemDictionary.putIfAbsent(rs.getInt("id"), one) != null) {
            Bukkit.getLogger().warning("${name}の登録に失敗しました")
            return
        }

        rs.close()
        sql.close()
    }

    private fun asyncLoadItemDictionary(){
        blockingQueue.add { sql ->
            itemDictionary.clear()

            Bukkit.getLogger().info("アイテム辞書を読み込み開始")

            val rs = sql.query("select id,base64 from item_list;")

            if (rs != null) {

                while (rs.next()) {
                    try {
                        itemDictionary[rs.getInt("id")] = Utility.itemFromBase64(rs.getString("base64"))
                    } catch (e : Exception) {
                        Bukkit.getLogger().warning("アイテムの読み込みに失敗しました id:${rs.getInt("id")} ${e.message}")
                        continue
                    }
                }

                rs.close()
                sql.close()
            }
            Bukkit.getLogger().info("アイテム辞書を読み込みました")
        }
    }

    //非同期で最安値のリストを読む
    private fun asyncLoadMinPriceItems(){
        blockingQueue.add { sql ->
            syncGetMinPriceItems(sql)
        }
    }

    //1週間たったアイテムは期限切れとする
    private fun asyncCheckExpired(){
        blockingQueue.add { sql->

            val calender = Calendar.getInstance()
            calender.time = Date()
            calender.add(Calendar.DAY_OF_YEAR,-7)

            sql.execute("update order_table set expired=1 where date<'${SimpleDateFormat("yyyy-MM-dd").format(calender.time)}' and is_op=0;")
            Bukkit.getLogger().info("1週間以上経った出品を取り下げました")
        }
    }

    //最安値のアイテムのリストを引く(スレッドで呼ぶ)
    fun syncGetMinPriceItems(sql:MySQLManager):List<OrderData>{

        val rs = sql.query("select * from order_table where expired=0 order by price;")?:return emptyList()

        val list = mutableListOf<OrderData>()

        while (rs.next()){
            val itemID = rs.getInt("item_id")

            if (list.any { it.itemID == itemID })continue

            val data = OrderData(
                rs.getInt("id"),
                itemID,
                rs.getDouble("price"),
                rs.getInt("amount"),
                rs.getDate("date"),
                UUID.fromString(rs.getString("uuid")),
                rs.getBoolean("is_op"),
                itemDictionary[itemID]?:continue
            )

            list.add(data)
        }

        rs.close()
        sql.close()

        minPriceItems = list

        return list
    }

    //同じアイテムの全注文を取得(スレッドで呼ぶ)
    fun syncGetOneItemList(itemID:Int, sql: MySQLManager):List<OrderData>{

        val rs = sql.query("select * from order_table  where expired=0 and item_id=${itemID}")?:return emptyList()

        val list = mutableListOf<OrderData>()

        while (rs.next()){
            val data = OrderData(
                rs.getInt("id"),
                itemID,
                rs.getDouble("price"),
                rs.getInt("amount"),
                rs.getDate("date"),
                UUID.fromString(rs.getString("uuid")),
                rs.getBoolean("is_op"),
                itemDictionary[itemID]!!
            )

            list.add(data)
        }

        rs.close()
        sql.close()

        return list.sortedBy { it.price }
    }

    fun syncGetCategorizedList(categoryName: String,sql:MySQLManager):List<OrderData>{

        val list = syncGetMinPriceItems(sql)
        val dic = getCategorizedDictionary(categoryName)

        return list.filter { dic.containsKey(it.itemID) }
    }

    fun syncGetSellerList(seller: UUID,sql: MySQLManager):List<OrderData>{

        val rs = sql.query("select * from order_table where uuid='${seller}'")?:return emptyList()

        val list = mutableListOf<OrderData>()

        while (rs.next()){
            val data = OrderData(
                rs.getInt("id"),
                rs.getInt("item_id"),
                rs.getDouble("price"),
                rs.getInt("amount"),
                rs.getDate("date"),
                UUID.fromString(rs.getString("uuid")),
                rs.getBoolean("is_op"),
                itemDictionary[rs.getInt("item_id")]!!
            )

            list.add(data)
        }

        rs.close()
        sql.close()

        return list
    }

    fun syncGetOfficialList(sql: MySQLManager):List<OrderData>{
        val rs = sql.query("select * from order_table where is_op=1")?:return emptyList()

        val list = mutableListOf<OrderData>()

        while (rs.next()){
            val data = OrderData(
                rs.getInt("id"),
                rs.getInt("item_id"),
                rs.getDouble("price"),
                rs.getInt("amount"),
                rs.getDate("date"),
                UUID.fromString(rs.getString("uuid")),
                rs.getBoolean("is_op"),
                itemDictionary[rs.getInt("item_id")]!!
            )

            list.add(data)
        }

        rs.close()
        sql.close()

        return list
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
//            MenuOld.setID(meta, name)
            icon.itemMeta = meta

            data.categoryIcon = icon

            Bukkit.getLogger().info("category:$name")

            categories[name] = data
        }

        Bukkit.getLogger().info("カテゴリーデータの読み込み完了")
    }

    //軽量化のためのキューをクラス外で使うための関数
    fun async(process:(MySQLManager)->Unit){
        blockingQueue.add(process)
    }

    private fun runBlockingQueue(){
        val mysql = MySQLManager(plugin,"BlockingQueue")

        while (true){

            try {
                val job = blockingQueue.take()
                job.invoke(mysql)

            }catch (e:InterruptedException){
                return
            }catch (e:Exception){
                Bukkit.getLogger().info(e.message)
                Bukkit.getLogger().warning(e.stackTraceToString())
                continue
            }
        }
    }
}