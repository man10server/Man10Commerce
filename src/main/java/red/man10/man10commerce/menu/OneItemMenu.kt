package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Utility
import red.man10.man10commerce.data.Transaction
import java.text.SimpleDateFormat
import kotlin.math.floor

class OneItemMenu(p:Player, val itemID:Int, page:Int) : MenuFramework(p, LARGE_CHEST_SIZE,"§l同じアイテムのリスト"){

    init {

        if (peek() !is OneItemMenu)push()


        Transaction.async {sql->

            val list = Transaction.syncGetOneItemList(itemID, sql)

            var inc = 0

            while (menu.getItem(44) == null){

                val index = inc+page*45
                inc++
                if (list.size<=index) break

                val data = list[index]
                val sampleItem = data.item.clone()

                val itemButton = Button(sampleItem.type)
                itemButton.cmd(data.item.itemMeta?.customModelData?:0)
                itemButton.title(Man10Commerce.getDisplayName(sampleItem))

                val lore = mutableListOf<String>()

                //TODO:値段の表示を要チェック
                lore.add("§e§l値段:${Utility.format(floor(data.price*data.amount))}")
                lore.add("§e§l単価:${Utility.format(floor(data.price))}")
                lore.add("§e§l出品者${Bukkit.getOfflinePlayer(data.seller).name}")
                lore.add("§e§l個数:${data.amount}")
                lore.add("§e§l出品日:${SimpleDateFormat("yyyy-MM-dd").format(data.date)}")
                if (data.isOP) lore.add("§d§l公式出品アイテム")
                lore.add("§cシフトクリックで1-Click購入")

                itemButton.lore(lore)

                itemButton.setClickAction{
                    //シフト左クリック
                    if (it.action == InventoryAction.MOVE_TO_OTHER_INVENTORY){
                        Utility.sendMsg(p,"§a§l購入処理中・・・・§a§k§lXX")
                        Transaction.asyncBuy(p,data.itemID,data.id){}
                        return@setClickAction
                    }

                    //右クリック(出品取り消し)
                    if (it.action == InventoryAction.PICKUP_HALF && p.hasPermission(Man10Commerce.OP)){
                        Transaction.asyncClose(p,data.id)
                        return@setClickAction
                    }
                }

                Bukkit.getScheduler().runTask(Man10Commerce.plugin, Runnable { addButton(itemButton) })
            }

            //Back
            val back = Button(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
            back.title("")
            arrayOf(45,46,47,48,49,50,51,52,53).forEach { setButton(back,it) }

            //previous
            if (page!=0){
                val previous = Button(Material.RED_STAINED_GLASS_PANE)
                previous.title("前のページへ")
                previous.setClickAction{ OneItemMenu(p,itemID,page-1).open() }
                arrayOf(45,46,47).forEach { setButton(previous,it) }

            }

            //next
            if (inc>=44){
                val next = Button(Material.RED_STAINED_GLASS_PANE)
                next.title("次のページへ")
                next.setClickAction{ OneItemMenu(p,itemID,page+1).open() }
                arrayOf(51,52,53).forEach { setButton(next,it) }
            }
        }
    }
}