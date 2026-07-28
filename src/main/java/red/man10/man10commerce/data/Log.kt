package red.man10.man10commerce.data

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.LinkedBlockingQueue

/**
 * 取引ログの書き込み。ログ1件ごとに1クエリを投げると取引スレッドを待たせるので、
 * 専用スレッドでまとめてバッチINSERTする。
 */
object Log {

    private const val INSERT =
        "INSERT INTO log (order_player, target_player, action, item_id, item_name, amount, price, date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, now())"

    private const val MAX_BATCH_SIZE = 200

    /** シャットダウン時に書き残しを吐き出すまでの上限 */
    private const val DRAIN_TIMEOUT_MS = 3_000L

    private val queue = LinkedBlockingQueue<Array<out Any?>>()
    private val sql = MySQLManager("Man10CommerceLog")

    @Volatile
    private var thread: Thread? = null

    fun setup(){
        stop()
        thread = Thread({ run() }, "Man10Commerce-Log").apply {
            isDaemon = true
            start()
        }
    }

    fun stop(){
        val worker = thread ?: return
        thread = null

        val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
        while (queue.isNotEmpty() && System.currentTimeMillis() < deadline){
            try {
                Thread.sleep(20)
            }catch (e:InterruptedException){
                Thread.currentThread().interrupt()
                break
            }
        }

        if (queue.isNotEmpty()){
            Bukkit.getLogger().warning("Man10Commerce: ${queue.size}件のログを書き込めませんでした")
        }

        worker.interrupt()
    }

    //      販売ログを追加
    fun sellLog(p:Player,item: ItemStack,price:Double,itemID:Int){
        queue.add(arrayOf(p.name, "", "SellItem", itemID, displayName(item), item.amount, price))
    }

    //      購入ログを追加
    fun buyLog(p:Player,data: OrderData,item:ItemStack){
        val seller = Bukkit.getOfflinePlayer(data.seller).name ?: ""
        queue.add(arrayOf(seller, p.name, "BuyItem", data.itemID, displayName(item), item.amount, data.price))
    }

    /**
     * 取り消しログを追加。
     * OPが他人の出品を取り下げた場合は、誰の出品だったかをtarget_playerに残す。
     */
    fun closeLog(p:Player,order: OrderData,item: ItemStack,isOwner:Boolean){
        val seller = if (isOwner) "" else Bukkit.getOfflinePlayer(order.seller).name ?: ""
        queue.add(arrayOf(p.name, seller, "CloseItem", order.itemID, displayName(item), item.amount, 0.0))
    }

    private fun displayName(item: ItemStack):String{
        val name = if (item.hasItemMeta()) item.itemMeta?.displayName else item.i18NDisplayName
        return name ?: ""
    }

    private fun run(){

        val batch = mutableListOf<Array<out Any?>>()

        while (!Thread.currentThread().isInterrupted){

            try {
                batch.add(queue.take())
                queue.drainTo(batch, MAX_BATCH_SIZE - 1)

                if (!sql.batch(INSERT, batch)){
                    //ログはリトライしても復旧しないことが多いので、内容を残して捨てる
                    Bukkit.getLogger().warning("Man10Commerce: ログ${batch.size}件の書き込みに失敗しました")
                    batch.forEach { Bukkit.getLogger().warning("lost log: ${it.joinToString(",")}") }
                }

                batch.clear()

            }catch (e:InterruptedException){
                Thread.currentThread().interrupt()
                return
            }catch (e:Exception){
                Bukkit.getLogger().warning("Man10Commerce: ログの書き込みに失敗しました ${e.message}")
                batch.clear()
            }
        }
    }
}
