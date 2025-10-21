package red.man10.man10commerce

import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import red.man10.man10commerce.Man10Commerce.Companion.prefix
import red.man10.man10commerce.menu.MenuFramework
import java.util.*

object Utility {

    ///////////////////////////////
    //base 64
    //////////////////////////////
    fun itemFromBase64(data: String): ItemStack? {
        val bytes = Base64.getDecoder().decode(data)
        return ItemStack.deserializeBytes(bytes)
    }

    fun itemToBase64(item: ItemStack): String {
        val bytes = item.serializeAsBytes()
        return Base64.getEncoder().encodeToString(bytes)
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