package red.man10.man10commerce.data

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.BankAPI
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

}

object ItemData {

    val itemIndex = ConcurrentHashMap<Int, ItemStack>()
    val itemList = ConcurrentHashMap<Int, Data>()

    val mysql = MySQLManager(plugin, "Man10Commerce")

    val bank = BankAPI(plugin)

    var fee = 0.1

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

                itemList[itemID] = data
            }

            rs2.close()
            mysql.close()

        }

    }

    fun setMinPriceItem(itemID: Int) {

        itemList.remove(itemID)

        val rs = mysql.query("SELECT * FROM order_table where item_id=$itemID ORDER BY price ASC LIMIT 1;") ?: return

        if (!rs.next()){ return}

        val data = Data()

        data.id = rs.getInt("id")
        data.amount = rs.getInt("amount")
        data.date = rs.getDate("date")
        data.itemID = itemID
        data.price = rs.getDouble("price")
        data.seller = UUID.fromString(rs.getString("uuid"))

        rs.close()
        mysql.close()

        val nowItem = itemList[itemID]

        if (nowItem == null || data.price < nowItem.price) {
            itemList[itemID] = data
        }

    }

    fun sell(p: Player, item: ItemStack, price: Double): Boolean {

        registerItemIndex(item)

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

        var id = -1

        itemIndex.forEach {
            if (it.value.isSimilar(item)) {
                id = it.key
            }
        }

        if (id == -1) return false

        mysql.execute(
            "INSERT INTO order_table " +
                    "(player, uuid, item_id, item_name, date, amount, price) " +
                    "VALUES ('${p.name}', '${p.uniqueId}', $id, '${name}', now(), ${item.amount}, $price);"
        )

        setMinPriceItem(id)

        item.amount = 0

        return true
    }

    @Synchronized
    fun buy(p:Player,itemID:Int,orderID:Int):Boolean{

        val data = itemList[itemID] ?: return false

        if (data.id != orderID)return false

        if (!bank.withdraw(p.uniqueId,data.price,"BuyItemOnMan10Commerce"))return false

        val item = itemIndex[itemID]?.clone()?:return false

        item.amount = data.amount

        p.inventory.addItem(item)

        bank.deposit(data.seller!!,(data.price*(1.0- fee)),"SellItemOnMan10Commerce")

        mysql.execute("DELETE FROM order_table where id=${data.id};")
        setMinPriceItem(itemID)

        return true
    }

    @Synchronized
    fun close(id:Int,p:Player):Boolean{

        val rs = mysql.query("select * from order_table where id=${id};")?:return false

        if (!rs.next())return false

        val itemID = rs.getInt("item_id")
        val amount = rs.getInt("amount")

        mysql.execute("DELETE FROM order_table where id=${id};")
        setMinPriceItem(itemID)

        val item = itemIndex[itemID]!!.clone()
        item.amount = amount
        p.inventory.addItem(item)

        return true
    }

    fun sellList(p:UUID): MutableList<Data>? {

        val list = mutableListOf<Data>()

        val rs = mysql.query("select * from order_table where uuid='${p}';")?:return null

        while (rs.next()){
            val data = Data()

            data.id = rs.getInt("id")
            data.amount = rs.getInt("amount")
            data.date = rs.getDate("date")
            data.itemID = rs.getInt("item_id")
            data.price = rs.getDouble("price")
            data.seller = p

            list.add(data)
        }

        rs.close()
        mysql.close()

        return list
    }

}