package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Utility
import red.man10.man10commerce.data.Transaction
import java.text.SimpleDateFormat
import kotlin.math.floor

class EnchantMainMenu(p:Player) : MenuFramework(p, LARGE_CHEST_SIZE,"§lエンチャントで検索"){

    init {
        push()

        for (enchant in Enchantment.values()){
            val item = ItemStack(Material.ENCHANTED_BOOK)
            val meta = item.itemMeta as EnchantmentStorageMeta
            meta.addStoredEnchant(enchant,1,true)
            item.itemMeta = meta

            val button = Button(Material.ENCHANTED_BOOK)

            button.fromItemStack(item)
            button.setClickAction{
//                EnchantLevelMenu(p,meta.storedEnchants.entries.first().key).open()
                EnchantLevelMenu(p,item.enchantments.entries.first().key).open()
            }
            addButton(button)
        }
    }

}

class EnchantLevelMenu(p:Player,enchant:Enchantment) : MenuFramework(p,9,"§lレベルを選択") {

    init {
        push()

        for (level in 1..enchant.maxLevel){
            val item = ItemStack(Material.ENCHANTED_BOOK)
            val meta = item.itemMeta as EnchantmentStorageMeta
            meta.addStoredEnchant(enchant,level,true)
            item.itemMeta = meta
            val button = Button(Material.ENCHANTED_BOOK)
            button.fromItemStack(item)

            button.setClickAction{
                val e = item.enchantments.entries.first()
//                val e = (item.itemMeta as EnchantmentStorageMeta).storedEnchants.entries.first()
                EnchantSelectMenu(p,0,e.key,e.value).open()
            }

            addButton(button)
        }

    }
}

class EnchantSelectMenu(p:Player, page:Int,private val enchant: Enchantment, private val level:Int)
    :MenuFramework(p, LARGE_CHEST_SIZE,"§lエンチャントの検索結果") {

    init {
        if (peek() !is EnchantSelectMenu)push()

        Transaction.async { sql->

            val list = Transaction.syncGetMinPriceItems(sql).filter { data->
                val meta = data.item
                meta.enchantments.containsKey(enchant) && meta.enchantments.containsValue(level)
            }

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

                    //通常クリック
                    if (it.action == InventoryAction.PICKUP_ALL){

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
                previous.setClickAction{ EnchantSelectMenu(p,page-1,enchant,level).open() }
                arrayOf(45,46,47).forEach { setButton(previous,it) }

            }

            //next
            if (inc>=44){
                val next = Button(Material.RED_STAINED_GLASS_PANE)
                next.title("次のページへ")
                next.setClickAction{ EnchantSelectMenu(p,page-1,enchant,level).open() }
                arrayOf(51,52,53).forEach { setButton(next,it) }
            }
        }

    }

}