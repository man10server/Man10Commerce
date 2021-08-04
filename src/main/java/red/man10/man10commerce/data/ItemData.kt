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

    val itemIndex = ConcurrentHashMap<Int, ItemStack>()
    val itemList = ConcurrentHashMap<Int, Data>()
    val opItemList = ConcurrentHashMap<Int,Data>()

    private val mysql = MySQLManager(plugin, "Man10Commerce")

    //新アイテムを登録する
    private fun registerItemIndex(item: ItemStack): Boolean {

        val one = item.asOne()

        if (itemIndex.containsValue(one)) return false

        val name = if (one.hasItemMeta()) one.itemMeta!!.displayName else one.i18NDisplayName

        mysql.execute(
            "INSERT INTO item_list " +
                    "(item_name, item_type, base64) VALUES ('${name}', '${one.type}', '${Utility.itemToBase64(one)}');"
        )

        val rs = mysql.query("select id from item_list ORDER BY id DESC LIMIT 1;")!!

        rs.next()

        itemIndex[rs.getInt("id")] = one

        rs.close()
        mysql.close()

        return true
    }

    fun loadItemIndex() {

        itemIndex.clear()

        val rs = mysql.query("select id,base64 from item_list;")

        if (rs != null) {

            while (rs.next()) {
                itemIndex[rs.getInt("id")] = Utility.itemFromBase64(rs.getString("base64"))!!
            }

            rs.close()
            mysql.close()

        }


        itemList.clear()
        opItemList.clear()

        val rs2 = mysql.query("select * from order_table")

        if (rs2 != null) {
            while (rs2.next()) {

                val itemID = rs2.getInt("item_id")

                if (itemList.containsKey(itemID)) continue

                val data = Data()

                data.id = rs2.getInt("id")
                data.amount = rs2.getInt("amount")
                data.date = rs2.getDate("date")
                data.itemID = itemID
                data.price = rs2.getDouble("price")
                data.seller = UUID.fromString(rs2.getString("uuid"))
                data.isOp = rs2.getInt("is_op") == 1

                itemList[itemID] = data
                if (data.isOp) opItemList[itemID] = data
            }

            rs2.close()
            mysql.close()

        }

    }

    private fun setMinPriceItem(itemID: Int) {

        itemList.remove(itemID)

        val rs = mysql.query("SELECT * FROM order_table where item_id=$itemID ORDER BY price/amount ASC LIMIT 1;") ?: return

        if (!rs.next()){
            itemIndex.remove(itemID)
            opItemList.remove(itemID)
            mysql.execute("DELETE FROM item_list where id=$itemID;")
            return
        }

        val data = Data()

        data.id = rs.getInt("id")
        data.amount = rs.getInt("amount")
        data.date = rs.getDate("date")
        data.itemID = itemID
        data.price = rs.getDouble("price")
        data.seller = UUID.fromString(rs.getString("uuid"))
        data.isOp = rs.getInt("is_op") == 1

        rs.close()
        mysql.close()

        val nowItem = itemList[itemID]

        if (nowItem == null || data.price < nowItem.price) {
            itemList[itemID] = data
            if (data.isOp){ opItemList[itemID] = data }
        }

    }

    fun sell(p: Player, item: ItemStack, price: Double): Boolean {

        val isPrime = UserData.isPrimeUser(p)

        if (Man10Commerce.maxItems< UserData.getSellAmount(p) && !isPrime){
            Utility.sendMsg(p,"出品数の上限に達しています！")
            return false
        }

        if (Man10Commerce.maxPrice < price &&!isPrime){
            Utility.sendMsg(p,"金額の上限に達しています！")
            return false
        }

        registerItemIndex(item)

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

        var id = -1

        itemIndex.forEach {
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

        setMinPriceItem(id)

        item.amount = 0

        return true
    }

    //運営用ショップ
    fun sellOP(p: Player, item: ItemStack, price: Double): Boolean {

        registerItemIndex(item)

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

        var id = -1

        itemIndex.forEach {
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

        setMinPriceItem(id)

        item.amount = 0

        return true
    }

    @Synchronized
    fun buy(p:Player,itemID:Int,orderID:Int):Int{

        val data = itemList[itemID] ?: return 3

        if (data.id != orderID)return 4

        if (!Man10Bank.vault.withdraw(p.uniqueId,data.price))return 0

        val item = itemIndex[itemID]?.clone()?:return 5

        item.amount = data.amount

        p.inventory.addItem(item)

        //利益の支払い処理(Primeなら手数料を半分に)
        if (UserData.isPrimeUser(data.seller!!)){
//            bank.deposit(data.seller!!,(data.price*(1.0- (fee/2))),"SellItemOnMan10Commerce")
        }else{
            bank.deposit(data.seller!!,(data.price*(1.0- fee)),"SellItemOnMan10Commerce","Amanzonの売り上げ")
        }


        Log.buyLog(p, data, item)

        if (!data.isOp){
            mysql.execute("DELETE FROM order_table where id=${data.id};")
        }
        setMinPriceItem(itemID)

        return 1
    }

    @Synchronized
    fun close(id:Int,p:Player):Boolean{

        val rs = mysql.query("select item_id,amount from order_table where id=${id};")?:return false

        if (!rs.next())return false

        val itemID = rs.getInt("item_id")
        val amount = rs.getInt("amount")

        val item = itemIndex[itemID]!!.clone()
        item.amount = amount
        p.inventory.addItem(item)


        mysql.execute("DELETE FROM order_table where id=${id};")
        setMinPriceItem(itemID)

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