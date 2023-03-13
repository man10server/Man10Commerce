package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Utility
import red.man10.man10commerce.data.ItemDataOld
import java.text.SimpleDateFormat
import kotlin.math.floor

class OneItemList(p:Player,val itemID:Int) : Menu("§l同じアイテムのリスト",54,p){
    override fun open() {

        val list = ItemDataOld.getAllItem(itemID)

        Bukkit.getScheduler().runTask(Man10Commerce.plugin, Runnable {

            for (data in list){
                if (menu.last()!=null)break

                val item = ItemDataOld.itemDictionary[itemID]?.clone()?:return@Runnable

                item.amount = data.amount

                val lore = item.lore?: mutableListOf()

                lore.add("§e§l値段:${Utility.format(floor(data.price))}")
                lore.add("§e§l単価:${Utility.format(floor(data.price / data.amount))}")
                lore.add("§e§l出品者${Bukkit.getOfflinePlayer(data.seller!!).name}")
                lore.add("§e§l個数:${data.amount}")
                lore.add("§e§l${SimpleDateFormat("yyyy-MM-dd").format(data.date)}")
                if (data.isOp) lore.add("§d§l公式出品アイテム")
                lore.add("§cシフトクリックで1-Click購入")

                val meta = item.itemMeta
                meta.persistentDataContainer.set(NamespacedKey(Man10Commerce.plugin,"order_id"), PersistentDataType.INTEGER,data.id)
                meta.persistentDataContainer.set(NamespacedKey(Man10Commerce.plugin,"item_id"), PersistentDataType.INTEGER,data.itemID)
                item.itemMeta = meta

                item.lore = lore

                menu.addItem(item)

            }

            p.openInventory(menu)

            pushStack()

        })


    }

    override fun click(e: InventoryClickEvent, menu: Menu, id: String, item: ItemStack) {

        if (menu !is OneItemList)return

        val meta = item.itemMeta?:return

        val orderID = meta.persistentDataContainer[NamespacedKey(Man10Commerce.plugin,"order_id"), PersistentDataType.INTEGER]?:-1

        if (orderID == -1)return

        if (p.hasPermission(Man10Commerce.OP) && e.action == InventoryAction.CLONE_STACK){
            ItemDataOld.close(orderID,p)
            Utility.sendMsg(p, "§c§l出品を取り下げました")
            menu.open()
            return
        }

        if (e.action != InventoryAction.MOVE_TO_OTHER_INVENTORY) { return }

        ItemDataOld.buy(p, menu.itemID, orderID) {
            menu.open()
        }

    }
}