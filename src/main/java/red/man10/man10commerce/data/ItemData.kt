package red.man10.man10commerce.data

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.BankAPI
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Utility
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ItemData {

    var id = 0
    var itemID = 0
    var amount = 0
    var price : Double = 0.0
    var date : Date? = null
    var seller : UUID? = null

    //商品を購入する
    fun buy(buyer:Player):Boolean{

        if (!bank.withdraw(buyer.uniqueId,price,"BuyItemOnMan10Commerce"))return false

        val item = itemIndex[id]?.clone()?:return false

        item.amount = amount

        buyer.inventory.addItem(item)

        bank.deposit(seller!!,(price*(1.0- fee)),"SellItemOnMan10Commerce")

        finish()

        return true
    }

//    //出品期間が過ぎたかどうか
//    fun isFinishTime():Boolean{
//
//
//        return false
//    }

    //出品を取り下げる
    fun close():Boolean{

        bank.deposit(seller!!,price,"CloseItemOnMan10Commerce")

        finish()

        return false
    }

    //取引を完了させたら呼び出す
    private fun finish():Boolean{

        mysql.execute("DELETE FROM order_table where id=$id;")

        return false
    }

    companion object{

        private val itemIndex = ConcurrentHashMap<Int,ItemStack>()

        private val mysql = MySQLManager(plugin,"Man10Commerce")

        private val bank = BankAPI(plugin)

        var fee = 0.1

        //新アイテムを登録する
        private fun registerItemIndex(item:ItemStack):Boolean{

            val one = item.asOne()

            if (itemIndex.containsValue(one))return false

            val name = if (one.hasItemMeta()) one.itemMeta!!.displayName else one.i18NDisplayName

            mysql.execute("INSERT INTO item_list " +
                    "(item_name, item_type, base64) VALUES ('${name}', '${one.type}', '${Utility.itemToBase64(one)}');")

            val rs = mysql.query("select id from item_list ORDER BY id DESC LIMIT 1;")!!

            rs.next()

            itemIndex[rs.getInt("id")] = one

            rs.close()
            mysql.close()

            return true
        }

        fun loadItemIndex(){

            itemIndex.clear()

            val rs = mysql.query("select id,base64 from item_list;")?:return

            while (rs.next()){
                itemIndex[rs.getInt("id")] = Utility.itemFromBase64(rs.getString("base64"))!!
            }

            rs.close()
            mysql.close()

        }

        fun getMinPriceItem(itemID:Int): ItemData? {
            val rs = mysql.query("SELECT * FROM order_table where item_id=$itemID ORDER BY price ASC LIMIT 1;")?:return null

            if (!rs.next())return null

            val data = ItemData()

            data.id = rs.getInt("id")
            data.amount = rs.getInt("amount")
            data.date = rs.getDate("date")
            data.itemID = itemID
            data.price = rs.getDouble("price")
            data.seller = UUID.fromString(rs.getString("uuid"))

            rs.close()
            mysql.close()

            return data
        }

        fun sell(p:Player,item: ItemStack,price:Double):Boolean{

            registerItemIndex(item)

            val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

            val id = itemIndex.forEach{ if (it.value.isSimilar(item)){it.key} }

            mysql.execute("INSERT INTO order_table " +
                    "(player, uuid, item_id, item_name, date, amount, price) " +
                    "VALUES ('${p.name}', '${p.uniqueId}', $id, '${name}', now(), ${item.amount}, $price);")

            Bukkit.getLogger().info("$id,${name},${item.amount},$price")

            return true
        }

    }

}