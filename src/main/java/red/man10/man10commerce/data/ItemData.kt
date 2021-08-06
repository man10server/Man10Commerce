package red.man10.man10commerce.data

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.Man10Bank
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Man10Commerce.Companion.bank
import red.man10.man10commerce.Man10Commerce.Companion.fee
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Utility
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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

    val itemList = ConcurrentHashMap<Int, ItemStack>()
    val orderList = ConcurrentHashMap<Int, Data>()
    val opOrderList = ConcurrentHashMap<Int, Data>()

    private val mysql = MySQLManager(plugin, "Man10Commerce")

    //新アイテムを登録する
    private fun registerItemIndex(item: ItemStack): Boolean {

        val one = item.asOne()

        if (itemList.containsValue(one)) return false

        val name = if (one.hasItemMeta()) one.itemMeta!!.displayName else one.i18NDisplayName

        mysql.execute(
            "INSERT INTO item_list " +
                    "(item_name, item_type, base64) VALUES ('${name}', '${one.type}', '${Utility.itemToBase64(one)}');"
        )

        val rs = mysql.query("select id from item_list ORDER BY id DESC LIMIT 1;")!!

        rs.next()

        itemList[rs.getInt("id")] = one

        rs.close()
        mysql.close()

        return true
    }

    fun loadItemIndex() {

        itemList.clear()

        val rs = mysql.query("select id,base64 from item_list;")

        if (rs != null) {

            while (rs.next()) {
                itemList[rs.getInt("id")] = Utility.itemFromBase64(rs.getString("base64"))!!
            }

            rs.close()
            mysql.close()

        }

    }

    fun loadOrderTable(){

        orderList.clear()
        val rs = mysql.query("select * from order_table where (item_id,(price/amount)) in (select item_id,min(price/amount) from order_table group by `item_id`) order by price;")?:return

        while (rs.next()) {

            val itemID = rs.getInt("item_id")

            if (orderList.containsKey(itemID)) continue

            val data = Data()

            data.id = rs.getInt("id")
            data.amount = rs.getInt("amount")
            data.date = rs.getDate("date")
            data.itemID = itemID
            data.price = rs.getDouble("price")
            data.seller = UUID.fromString(rs.getString("uuid"))
            data.isOp = rs.getInt("is_op") == 1

            orderList[itemID] = data
        }

        rs.close()
        mysql.close()

    }

    fun loadOPOrderTable(){

        opOrderList.clear()
        val rs = mysql.query("select * from order_table where (item_id,(price/amount)) in (select item_id,min(price/amount)" +
                " from order_table where is_op=1 group by `item_id`) order by price;")?:return

        while (rs.next()) {

            val itemID = rs.getInt("item_id")

            if (opOrderList.containsKey(itemID)) continue

            val data = Data()

            data.id = rs.getInt("id")
            data.amount = rs.getInt("amount")
            data.date = rs.getDate("date")
            data.itemID = itemID
            data.price = rs.getDouble("price")
            data.seller = UUID.fromString(rs.getString("uuid"))
            data.isOp = rs.getInt("is_op") == 1

            opOrderList[itemID] = data
        }

        rs.close()
        mysql.close()

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

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

        var id = -1

        itemList.forEach {
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

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

        var id = -1

        itemList.forEach {
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

        val data = orderList[itemID] ?: return 3

        if (data.id != orderID)return 4

        if (!Man10Bank.vault.withdraw(p.uniqueId,data.price))return 0

        val item = itemList[itemID]?.clone()?:return 5

        item.amount = data.amount

        p.inventory.addItem(item)

        //利益の支払い処理(Primeなら手数料を半分に)
        bank.deposit(data.seller!!,(data.price*(1.0- fee)),"SellItemOnMan10Commerce","Amanzonの売り上げ")


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

        val item = itemList[itemID]!!.clone()
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

}