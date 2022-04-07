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
import red.man10.man10commerce.data.ItemData
import java.text.SimpleDateFormat
import java.util.*

class MySellingItemMenu(p:Player,private val seller: UUID):ListMenu("§l出品したアイテム",p) {

    constructor(p:Player) : this(p,p.uniqueId)

    override fun open() {

        if (p.uniqueId!=seller &&!p.hasPermission("commerce.op")){ return }

        val list = ItemData.sellList(seller)

        var inc = 0

        while (menu.getItem(44) == null){

            if (list.size <= inc+page*45)break

            val data = list[inc+page*45]

            inc ++

            val item = ItemData.itemDictionary[data.itemID]?.clone()?:continue

            val lore = item.lore?: mutableListOf()

            lore.add("§e§l値段:${Utility.format(data.price)}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§e§l${SimpleDateFormat("yyyy-MM/dd").format(data.date)}")
            lore.add("§c§lシフトクリックで出品を取り下げる")

            item.lore = lore

            val meta = item.itemMeta

            meta.persistentDataContainer.set(NamespacedKey(Man10Commerce.plugin,"order_id"), PersistentDataType.INTEGER,data.id)

            item.itemMeta = meta

            menu.addItem(item)

        }

        setPageButton()

        Bukkit.getScheduler().runTask(Man10Commerce.plugin, Runnable {
            p.openInventory(menu)

            pushStack()
        })


    }

    override fun click(e: InventoryClickEvent, menu: Menu, id: String, item: ItemStack) {
        if (e.action != InventoryAction.MOVE_TO_OTHER_INVENTORY){return}

        val meta = item.itemMeta!!
        val orderID = meta.persistentDataContainer[NamespacedKey(Man10Commerce.plugin,"order_id"), PersistentDataType.INTEGER]?:0

        Man10Commerce.es.execute {
            if (ItemData.close(orderID,p)){
                Utility.sendMsg(p, "出品を取り下げました")
                menu.open()
            }
        }

    }

}