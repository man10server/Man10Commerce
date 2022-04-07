package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
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
}