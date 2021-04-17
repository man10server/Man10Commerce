package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataType
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Utility
import red.man10.man10commerce.data.ItemData.itemIndex
import red.man10.man10commerce.data.ItemData.itemList

object CommerceMenu : Listener{

    private val playerMenuMap = HashMap<Player,String>()

    private const val ITEM_MENU = "§e§l出品中のアイテム一覧"

    fun openMainMenu(p:Player){

    }

    fun openItemMenu(p:Player,page:Int){

        val inv = Bukkit.createInventory(null,54, ITEM_MENU)

        val keys = itemIndex.keys().toList()

        var inc = 0

        Bukkit.getLogger().info("page$page")


        for (i in page*45 .. (page+1)*45){

            Bukkit.getLogger().info("$i,$inc")

            if (keys.size <= i)break

            val itemID = keys[i]

            val data = itemList[itemID]?:continue
            val item = itemIndex[itemID]!!.clone()

            val lore = item.lore?: mutableListOf()

            lore.add("§e§l値段:${Utility.format(data.price)}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§cシフト左クリックで1Click購入")

            val meta = item.itemMeta
            meta.persistentDataContainer.set(NamespacedKey(Man10Commerce.plugin,"id"), PersistentDataType.INTEGER,data.id)
            item.itemMeta = meta

            item.lore = lore

            inv.setItem(inc,item)

            inc ++

        }

        p.openInventory(inv)

    }

    ////////////////////////////////////////////////////



}