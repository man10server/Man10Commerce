package red.man10.man10commerce.data

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.Man10Bank
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Utility
import red.man10.man10commerce.Utility.format
import red.man10.man10commerce.Utility.sendMsg
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

data class ItemData(
    var id : Int,
    var itemID: Int,
    var price : Double,
    var date : Date?,
    var seller : UUID,
    var isOP : Boolean
)

object Transaction {

    private val blockingQueue = LinkedBlockingQueue<(MySQLManager)->Unit>()
    private var queueThread = Thread{ runBlockingQueue() }

    private val itemDictionary = ConcurrentHashMap<Int,ItemStack>()//アイテムIDとItemStackの辞書


    fun runQueueThread(){
        if (queueThread.isAlive){
            queueThread.interrupt()
            queueThread = Thread{ runBlockingQueue() }
        }
        queueThread.start()
    }

    ////////////////////////////////
    //      アイテムを買う
    ///////////////////////////////
    fun buy(p:Player,itemID: Int,orderID:Int){

        blockingQueue.add { sql->

            if (p.inventory.firstEmpty() == -1){
                sendMsg(p,"§cインベントリに空きがありません")
                return@add
            }

            val rs = sql.query("select * from order_table where id=$orderID;")

            if (rs == null ||  !rs.next()){
                sendMsg(p,"§cすでに売り切れです！")
                return@add
            }

            val amount = rs.getInt("amount")
            val date = rs.getDate("date")
            val price = rs.getDouble("price")
            val seller = UUID.fromString(rs.getString("uuid"))
            val isOp = rs.getBoolean("is_op")

            val data = ItemData(
                orderID,
                itemID,
                price,
                date,
                seller,
                isOp
            )

            val totalPrice = price*amount

            rs.close()
            sql.close()

            val item = itemDictionary[itemID]?.clone()

            if (item == null){
                sendMsg(p,"§cすでに売り切れです")
                return@add
            }

            item.amount = amount

            if (!isOp){
                val ret = sql.execute("DELETE FROM order_table where id=${orderID};")
                if (!ret){
                    sendMsg(p,"${Man10Commerce.prefix}§c倉庫にアクセスができませんでした。もう一度購入し直してください")
                    return@add
                }
            }

            //お金関連の処理
            if (!Man10Bank.vault.withdraw(p.uniqueId,totalPrice)){
                sendMsg(p,"§c電子マネーのお金が足りません")
                return@add
            }

            Man10Commerce.bank.deposit(seller!!,totalPrice,"SellItemOnMan10Commerce","Amanzonの売り上げ")

//            Log.buyLog(p,data,item)

            p.inventory.addItem(item)

            sendMsg(p,"§a購入しました")
        }
    }

    /////////////////////////////
    //      販売する
    /////////////////////////////
    fun sell(p:Player,item:ItemStack,price:Double){

        blockingQueue.add {sql ->

            if (UserData.getSellCount(p.uniqueId)>Man10Commerce.maxItems){
                sendMsg(p,"§c出品数上限に達しています")
                return@add
            }

            if (price>Man10Commerce.maxPrice){
                sendMsg(p,"§c単価は${format(Man10Commerce.maxPrice)}円金額にしてください。")
                return@add
            }

            val meta = item.itemMeta

            if (meta != null && meta is org.bukkit.inventory.meta.Damageable && meta.hasDamage()){
                sendMsg(p,"§c§l耐久値が削れているので出品できません！")
                return@add
            }

            val name = Man10Commerce.getDisplayName(item)

            if (item.hasItemMeta()){
                if (Man10Commerce.disableItems.contains(ChatColor.stripColor(name))){
                    sendMsg(p,"　§cこのアイテムは販売できません")
                    return@add
                }
            }

            syncRegisterItemIndex(item,sql)

            var id : Int? = null
             itemDictionary.forEach{
                if (it.value.isSimilar(item)){
                    id = it.key
                }
            }

            if (id == null){
                sendMsg(p,"§c出品失敗！もう一度出品し直してみてください")
                return@add
            }

            val ret = sql.execute(
                "INSERT INTO order_table " +
                        "(player, uuid, item_id, item_name, date, amount, price) " +
                        "VALUES ('${p.name}', '${p.uniqueId}', $id, '${MySQLManager.escapeStringForMySQL(name)}', now(), ${item.amount}, $price);"
            )

            if (ret){
                sendMsg(p,"§a§l出品成功！")
                Log.sellLog(p,item,price, id!!)
            }

        }

    }

    /////////////////////////////
    //      注文を取り消す
    ////////////////////////////
    fun close(p:Player,id:Int){
        blockingQueue.add {sql->

            val rs = sql.query("select item_id,amount,is_op from order_table where id=${id};")?:return false

            if (!rs.next()) return@add

            val itemID = rs.getInt("item_id")
            val amount = rs.getInt("amount")

            val item = ItemDataOld.itemDictionary[itemID]!!.clone()
            item.amount = amount
            p.inventory.addItem(item)

            sql.execute("DELETE FROM order_table where id=${id};")

            Log.closeLog(p,itemID,item)
        }
    }

    private fun syncRegisterItemIndex(item:ItemStack, sql:MySQLManager){
        val one = item.asOne()

        if (itemDictionary.values.any{it.isSimilar(one)})return

        val name = Man10Commerce.getDisplayName(one)

        sql.execute(
            "INSERT INTO item_list " +
                    "(item_name, item_type, base64) VALUES ('${MySQLManager.escapeStringForMySQL(name)}', '${one.type}', '${Utility.itemToBase64(one)}');"
        )

        val rs = sql.query("select id from item_list ORDER BY id DESC LIMIT 1;")!!

        if (!rs.next()){
//            syncRegisterItemIndex(item, sql)
            Bukkit.getLogger().warning("${name}の登録に失敗しました")
            return
        }

        ItemDataOld.itemDictionary[rs.getInt("id")] = one

        rs.close()
        sql.close()
    }

    private fun runBlockingQueue(){

        val mysql = MySQLManager(Man10Commerce.plugin,"BlockingQueue")

        while (true){

            try {

                val job = blockingQueue.take()
                job.invoke(mysql)

            }catch (e:InterruptedException){
                return
            }catch (e:Exception){
                continue
            }

        }
    }
}