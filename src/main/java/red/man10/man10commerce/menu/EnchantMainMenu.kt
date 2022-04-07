package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.persistence.PersistentDataType
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.sort.Sort

class EnchantMainMenu(p:Player) : Menu("§lエンチャントで検索",54,p){
    override fun open() {
        for (enchant in Enchantment.values()){
            val item = ItemStack(Material.ENCHANTED_BOOK)
            val meta = item.itemMeta as EnchantmentStorageMeta
            meta.addStoredEnchant(enchant,1,true)
            item.itemMeta = meta
            menu.addItem(item)
        }

        p.openInventory(menu)

        pushStack()

    }

    override fun click(e: InventoryClickEvent, menu: Menu, id: String, item: ItemStack) {
        val meta = item.itemMeta as EnchantmentStorageMeta
        EnchantLevelMenu(p,meta.storedEnchants.entries.first().key).open()
    }
}

class EnchantLevelMenu(p:Player,private val enchant:Enchantment) : Menu("§lレベルを選択",9,p) {

    override fun open() {
        for (level in 1..enchant.maxLevel){
            val item = ItemStack(Material.ENCHANTED_BOOK)
            val meta = item.itemMeta as EnchantmentStorageMeta
            meta.addStoredEnchant(enchant,level,true)
            item.itemMeta = meta
            menu.addItem(item)
        }

        p.openInventory(menu)

        pushStack()

    }

    override fun click(e: InventoryClickEvent, menu: Menu, id: String, item: ItemStack) {
        val enchant = (item.itemMeta as EnchantmentStorageMeta).storedEnchants.entries.first()
        EnchantSelectMenu(p,enchant.key,enchant.value)

    }
}

class EnchantSelectMenu(p:Player,
                        private val enchant: Enchantment,
                        private val level:Int):ListMenu("§lエンチャントの検索結果",p) {
    override fun open() {
        val keys = Sort.enchantSort(enchant,level, ItemData.orderMap.keys().toList())

        listInventory(keys)

        p.openInventory(menu)

        pushStack()
    }

    override fun click(e: InventoryClickEvent, menu: Menu, id: String, item: ItemStack) {
        val meta = item.itemMeta!!

        val orderID = meta.persistentDataContainer[NamespacedKey(Man10Commerce.plugin,"order_id"), PersistentDataType.INTEGER]?:-1
        val itemID = meta.persistentDataContainer[NamespacedKey(Man10Commerce.plugin,"item_id"), PersistentDataType.INTEGER]?:-1

        if (orderID == -1)return

        if (e.action != InventoryAction.MOVE_TO_OTHER_INVENTORY){
            Man10Commerce.es.execute { OneItemList(p,itemID).open() }
            return
        }

        ItemData.buy(p,itemID,orderID){
            Bukkit.getScheduler().runTask(Man10Commerce.plugin, Runnable { menu.open() })
        }

    }
}