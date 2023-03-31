package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Utility
import red.man10.man10commerce.data.Transaction
import java.text.SimpleDateFormat

class MySellingItemMenu(p:Player):MenuFramework(p, LARGE_CHEST_SIZE,"§l出品したアイテム") {

    init {
        push()

        Transaction.async { sql ->

            val list = Transaction.syncGetSellerList(p.uniqueId,sql)

            var inc = 0

            Bukkit.getScheduler().runTask(plugin, Runnable {
                while (menu.getItem(53) == null){

                    val index = inc
                    inc++
                    if (list.size<=index) break

                    val data = list[index]
                    val sampleItem = data.item.clone()

                    val itemButton = Button(sampleItem.type)
                    if (data.item.itemMeta?.hasCustomModelData() == true){
                        itemButton.cmd(data.item.itemMeta?.customModelData?:0)
                    }
                    itemButton.title(Man10Commerce.getDisplayName(sampleItem))

                    val lore = mutableListOf<String>()

                    lore.add("§e§l値段:${Utility.format(data.price)}")
                    lore.add("§e§l個数:${data.amount}")
                    lore.add("§e§l${SimpleDateFormat("yyyy-MM-dd").format(data.date)}")
                    lore.add("§c§lシフトクリックで出品を取り下げる")

                    itemButton.lore(lore)

                    itemButton.setClickAction{
                        //シフト左クリック
                        if (it.action == InventoryAction.MOVE_TO_OTHER_INVENTORY){
                            Transaction.asyncClose(p,data.id)
                            return@setClickAction
                        }
                    }

                    Bukkit.getScheduler().runTask(plugin, Runnable { addButton(itemButton) })
                }
            })
        }
    }

}