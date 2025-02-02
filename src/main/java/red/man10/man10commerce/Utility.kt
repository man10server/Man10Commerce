package red.man10.man10commerce

import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import red.man10.man10commerce.Man10Commerce.Companion.prefix
import red.man10.man10commerce.menu.MenuFramework
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object Utility {

    ///////////////////////////////
    //base 64
    //////////////////////////////
    fun itemFromBase64(data: String): ItemStack? = try {
        val inputStream = ByteArrayInputStream(Base64Coder.decodeLines(data))
        val dataInput = BukkitObjectInputStream(inputStream)
        val items = arrayOfNulls<ItemStack>(dataInput.readInt())

        // Read the serialized inventory
        for (i in items.indices) {
            items[i] = dataInput.readObject() as ItemStack
        }

        dataInput.close()
        items[0]
    } catch (e: Exception) {
        null
    }

    @Throws(IllegalStateException::class)
    fun itemToBase64(item: ItemStack): String {
        try {
            val outputStream = ByteArrayOutputStream()
            val dataOutput = BukkitObjectOutputStream(outputStream)
            val items = arrayOfNulls<ItemStack>(1)
            items[0] = item
            dataOutput.writeInt(items.size)

            for (i in items.indices) {
                dataOutput.writeObject(items[i])
            }

            dataOutput.close()

            return Base64Coder.encodeLines(outputStream.toByteArray())

        } catch (e: Exception) {
            throw IllegalStateException("Unable to save item stacks.", e)
        }
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