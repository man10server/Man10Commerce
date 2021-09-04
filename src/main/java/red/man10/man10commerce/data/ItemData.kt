package red.man10.man10commerce.data

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.Man10Bank
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Man10Commerce.Companion.bank
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Utility
import red.man10.man10commerce.menu.CommerceMenu
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap

class Data {

    var id = 0
    var itemID = 0
    var amount = 0
    var price : Double = 0.0
    var date : Date? = null
    var seller : UUID? = null
    var isOp = false

}

object ItemData {

    val itemDictionary = ConcurrentHashMap<Int, ItemStack>()//item_idとitemStackの辞書
    val orderMap = ConcurrentHashMap<Int, Data>()//order_idと注文情報のマップ
    val opOrderMap = ConcurrentHashMap<Int, Data>()

    val categories = ConcurrentHashMap<String,Category>()

    private val mysql = MySQLManager(plugin, "Man10Commerce")

    //新アイテムを登録する
    private fun registerItemIndex(item: ItemStack): Boolean {

        val one = item.asOne()

        if (itemDictionary.containsValue(one)) return false

        val name = item.itemMeta?.displayName?:item.i18NDisplayName

        mysql.execute(
            "INSERT INTO item_list " +
                    "(item_name, item_type, base64) VALUES ('${name}', '${one.type}', '${Utility.itemToBase64(one)}');"
        )

        val rs = mysql.query("select id from item_list ORDER BY id DESC LIMIT 1;")!!

        rs.next()

        itemDictionary[rs.getInt("id")] = one

        rs.close()
        mysql.close()

        return true
    }

    fun loadItemIndex() {

        itemDictionary.clear()

        val rs = mysql.query("select id,base64 from item_list;")

        if (rs != null) {

            while (rs.next()) {
                itemDictionary[rs.getInt("id")] = Utility.itemFromBase64(rs.getString("base64"))?:continue
            }

            rs.close()
            mysql.close()

        }

    }

    fun loadOrderTable(){

        orderMap.clear()
//        val rs = mysql.query("select * from order_table where (item_id,(price/amount)) in (select item_id,min(price/amount) from order_table group by `item_id`) order by price;")?:return
        val rs = mysql.query("select * from order_table order by price;")?:return

        while (rs.next()) {

            val itemID = rs.getInt("item_id")

            if (orderMap.containsKey(itemID)) continue

            val data = Data()

            data.id = rs.getInt("id")
            data.amount = rs.getInt("amount")
            data.date = rs.getDate("date")
            data.itemID = itemID
            data.price = rs.getDouble("price")*data.amount
            data.seller = UUID.fromString(rs.getString("uuid"))
            data.isOp = rs.getInt("is_op") == 1

            orderMap[itemID] = data
        }

        rs.close()
        mysql.close()

    }

    fun loadOPOrderTable(){

        opOrderMap.clear()
        val rs = mysql.query("select * from order_table where (item_id,(price/amount)) in (select item_id,min(price/amount)" +
                " from order_table where is_op=1 group by `item_id`) order by price;")?:return

        while (rs.next()) {

            val itemID = rs.getInt("item_id")

            if (opOrderMap.containsKey(itemID)) continue

            val data = Data()

            data.id = rs.getInt("id")
            data.amount = rs.getInt("amount")
            data.date = rs.getDate("date")
            data.itemID = itemID
            data.price = rs.getDouble("price")
            data.seller = UUID.fromString(rs.getString("uuid"))
            data.isOp = rs.getInt("is_op") == 1

            opOrderMap[itemID] = data
        }

        rs.close()
        mysql.close()

    }

    fun loadCategoryData(){

        categories.clear()

        val categoryFolder = File(plugin.dataFolder,File.separator+"categories")

        if (!categoryFolder.exists())categoryFolder.mkdir()

        val files = categoryFolder.listFiles()?.toMutableList()?:return

        Bukkit.getLogger().info("Start Loading Categories")

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
                }catch (e:Exception){ }
            }

            data.material = materialList

            val icon = ItemStack(Material.valueOf(yml.getString("CategoryIconMaterial")?:"STONE"))
            val meta = icon.itemMeta
            meta.displayName(Component.text(yml.getString("CategoryIconTitle")?:"Title"))
            meta.setCustomModelData(yml.getInt("CategoryIconCMD"))
            CommerceMenu.setID(meta,name)
            icon.itemMeta = meta

            data.categoryIcon = icon

            Bukkit.getLogger().info("category:$name")

            categories[name] = data
        }

        Bukkit.getLogger().info("Finish Loading Categories")
    }



    fun sell(p: Player, item: ItemStack, price: Double): Boolean {

        if (Man10Commerce.maxItems< UserData.getSellAmount(p)){
            Utility.sendMsg(p,"出品数の上限に達しています！")
            return false
        }

        if (Man10Commerce.maxPrice < price){
            Utility.sendMsg(p,"金額の上限に達しています！")
            return false
        }

        registerItemIndex(item)

        val name = item.itemMeta?.displayName?:item.i18NDisplayName

        var id = -1

        itemDictionary.forEach {
            if (it.value.isSimilar(item)) {
                id = it.key
            }
        }

        if (id == -1){
            Utility.sendMsg(p,"§c出品失敗！サーバー管理者にレポートしてください！sell error 1")
            return false
        }

        mysql.execute(
            "INSERT INTO order_table " +
                    "(player, uuid, item_id, item_name, date, amount, price) " +
                    "VALUES ('${p.name}', '${p.uniqueId}', $id, '${name}', now(), ${item.amount}, $price);"
        )

        Log.sellLog(p,item,price,id)

        loadOrderTable()

        item.amount = 0

        return true
    }

    //運営用ショップ
    fun sellOP(p: Player, item: ItemStack, price: Double): Boolean {

        registerItemIndex(item)

        val name = item.itemMeta?.displayName?:item.i18NDisplayName

        var id = -1

        itemDictionary.forEach {
            if (it.value.isSimilar(item)) {
                id = it.key
            }
        }

        if (id == -1){
            Utility.sendMsg(p,"§c出品失敗！サーバー管理者にレポートしてください！sell error 2")
            return false
        }

        mysql.execute(
            "INSERT INTO order_table " +
                    "(player, uuid, item_id, item_name, date, amount, price, is_op) " +
                    "VALUES ('${p.name}', '${p.uniqueId}', $id, '${name}', now(), ${item.amount}, $price, 1);"
        )

        Log.sellLog(p,item,price,id)

        loadOPOrderTable()

        item.amount = 0

        return true
    }

    @Synchronized
    fun buy(p:Player,itemID:Int,orderID:Int):Int{

        val data = orderMap[itemID] ?: return 3

        if (data.id != orderID)return 4

        if (!Man10Bank.vault.withdraw(p.uniqueId,data.price))return 0

        val item = itemDictionary[itemID]?.clone()?:return 5

        item.amount = data.amount

        p.inventory.addItem(item)

        bank.deposit(data.seller!!,(data.price*data.amount),"SellItemOnMan10Commerce","Amanzonの売り上げ")

        Log.buyLog(p, data, item)

        if (!data.isOp){
            mysql.execute("DELETE FROM order_table where id=${data.id};")
            loadOrderTable()
        }else{
            loadOPOrderTable()
        }

        return 1
    }

    @Synchronized
    fun close(id:Int,p:Player):Boolean{

        val rs = mysql.query("select item_id,amount,is_op from order_table where id=${id};")?:return false

        if (!rs.next())return false

        val itemID = rs.getInt("item_id")
        val amount = rs.getInt("amount")
        val isOp = rs.getInt("is_op") == 1

        val item = itemDictionary[itemID]!!.clone()
        item.amount = amount
        p.inventory.addItem(item)


        mysql.execute("DELETE FROM order_table where id=${id};")

        if (isOp) loadOPOrderTable() else loadOrderTable()

        Log.closeLog(p,itemID,item)

        return true
    }

    fun sellList(uuid:UUID): MutableList<Data> {

        val list = mutableListOf<Data>()

        val rs = mysql.query("select * from order_table where uuid='${uuid}';")?:return list

        while (rs.next()){
            val data = Data()

            data.id = rs.getInt("id")
            data.amount = rs.getInt("amount")
            data.date = rs.getDate("date")
            data.itemID = rs.getInt("item_id")
            data.price = rs.getDouble("price")
            data.seller = uuid
            data.isOp = rs.getInt("is_op") == 1

            list.add(data)
        }

        rs.close()
        mysql.close()

        return list
    }

    fun getCategorized(categoryName: String): Map<Int, Data>? {

        val category = categories[categoryName] ?: return null

        val list = mutableListOf<Int>()

        val isEmptyMaterial = category.material.isEmpty()
        val isEmptyDisplay = category.displayName.isEmpty()
        val isEmptyCMD = category.customModelData.isEmpty()

        for (data in itemDictionary) {

            val item = data.value
            val meta = item.itemMeta

            var matchMaterial = false
            var matchDisplay = false
            var matchCMD = false

            if (isEmptyMaterial) {
                matchMaterial = true
            } else if (category.material.contains(item.type)) matchMaterial = true

            val display = (meta?.displayName ?: item.i18NDisplayName ?: "none").replace("§[a-z0-9]".toRegex(), "")

            if (isEmptyDisplay) {
                matchDisplay = true
            } else if ((category.displayName.filter { display.contains(it) }).isNotEmpty()) matchDisplay = true

            val cmd = if (meta.hasCustomModelData()) meta.customModelData else 0

            if (isEmptyCMD) {
                matchCMD = true
            } else if (category.customModelData.contains(cmd)) matchCMD = true

            if (matchCMD && matchDisplay && matchMaterial) list.add(data.key)
        }

        return orderMap.filterKeys { list.contains(it) }
    }

    @Synchronized
    fun getAllItem(itemID:Int):List<Data>{

        val list = mutableListOf<Data>()

        val rs = mysql.query("select * from order_table where item_id=$itemID order by price;")?:return Collections.emptyList()

        while (rs.next()){

            val data = Data()

            data.id = rs.getInt("id")
            data.amount = rs.getInt("amount")
            data.date = rs.getDate("date")
            data.itemID = itemID
            data.price = rs.getDouble("price")*data.amount
            data.seller = UUID.fromString(rs.getString("uuid"))
            data.isOp = rs.getInt("is_op") == 1

            list.add(data)
        }

        rs.close()
        mysql.close()

        return list
    }
}

class Category{

    lateinit var categoryIcon : ItemStack

    var material = mutableListOf<Material>()
    var displayName = mutableListOf<String>()
    var customModelData = mutableListOf<Int>()


}