package red.man10.man10commerce

import org.bukkit.Bukkit
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Man10Commerce.Companion.prefix
import red.man10.man10commerce.menu.MenuFramework
import java.util.*

object Utility {

    /** DBに問い合わせできなかったときの共通メッセージ */
    const val DB_ERROR_MESSAGE = "§cセンターにアクセスできませんでした。時間をおいてもう一度お試しください"

    /**
     * Bukkit APIを触る処理をメインスレッドで実行する。
     * すでにメインスレッドなら即座に実行するので、1tick遅れない。
     */
    fun sync(job: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            job()
            return
        }
        try {
            Bukkit.getScheduler().runTask(plugin, Runnable(job))
        } catch (e: Exception) {
            //シャットダウン中はスケジューラが受け付けない。アイテムを失うよりはその場で処理する
            Bukkit.getLogger().warning("Man10Commerce: メインスレッドに処理を渡せなかったため直接実行します (${e.message})")
            job()
        }
    }

    /**
     * アイテムをメインスレッドで渡す。
     * 入りきらなかった分は消さずに足元へ落とす。
     */
    fun giveItem(p: Player, item: ItemStack) {
        sync {
            val leftover = p.inventory.addItem(item)
            if (leftover.isEmpty()) return@sync
            leftover.values.forEach { p.world.dropItem(p.location, it) }
            sendMsg(p, "§cインベントリに入りきらなかった分は足元に落としました")
        }
    }

    /** 出品者名の解決はキャッシュミス時にI/Oが走るので、メインスレッドの外でまとめて引く */
    fun resolveNames(uuids: Collection<UUID>): Map<UUID, String> {
        return uuids.distinct().associateWith { Bukkit.getOfflinePlayer(it).name ?: "unknown" }
    }

    ///////////////////////////////
    //base 64
    //////////////////////////////
    fun itemFromBase64(data: String): ItemStack {
        val bytes = Base64Coder.decodeLines(data)
        return ItemStack.deserializeBytes(bytes)
    }

    fun itemToBase64(item: ItemStack): String {
        val bytes = item.serializeAsBytes()
        return Base64Coder.encodeLines(bytes)
    }

    fun format(double: Double):String{
        return String.format("%,.0f",double)
    }

    fun sendMsg(p:Player,msg:String){ p.sendMessage(prefix+msg) }

    fun isShulkerBox(item: ItemStack): Boolean {
        val meta = item.itemMeta
        return meta is BlockStateMeta && meta.blockState is ShulkerBox
    }

    fun shulkerInventory(p: Player, shulker: ItemStack) {
        val shulkerMeta = (shulker.itemMeta as BlockStateMeta).blockState as ShulkerBox
        object : MenuFramework(p, 27, "中身") {
            override fun init() {
                setClickAction {
                    it.isCancelled = true
                }
                for (i in 0..26) {
                    val item = shulkerMeta.inventory.getItem(i)
                    if (item != null) {
                        val button = Button(item.type)
                        button.setIcon(item)
                        button.setClickAction {
                            it.isCancelled = true
                        }
                        setButton(button, i)
                    }
                }
            }
        }.open()

    }

}