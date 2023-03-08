package red.man10.man10commerce.menu

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import red.man10.man10commerce.Man10Commerce

class MainMenu(p:Player) : Menu("§l出品中のアイテム一覧",27,p) {

    override fun open() {

        val showItem = ItemStack(Material.GRASS_BLOCK)
        val shouItemMeta = showItem.itemMeta
        shouItemMeta.displayName(Component.text("§a§l出品されているアイテムをみる"))
        setID(shouItemMeta, "ItemMenu")
        showItem.itemMeta = shouItemMeta

        val basic = ItemStack(Material.DIAMOND)
        val basicMeta = basic.itemMeta
        basicMeta.displayName(Component.text("§a§lAmazonBasic"))
        basicMeta.lore = mutableListOf("§f運営が販売している","アイテムを買うことができます")
        setID(basicMeta, "Basic")
        basic.itemMeta = basicMeta

        val sellItem = ItemStack(Material.CHEST)
        val sellItemMeta = sellItem.itemMeta
        sellItemMeta.displayName(Component.text("§a§l出品したアイテムを確かめる(現在利用不可能)"))
        setID(sellItemMeta, "SellMenu")
        sellItem.itemMeta = sellItemMeta

        val selling = ItemStack(Material.COBBLESTONE)
        val sellingMeta = selling.itemMeta
        sellingMeta.displayName(Component.text("§e§lアイテムを出品する"))
        setID(sellingMeta, "Selling")
        selling.itemMeta = sellingMeta

        val nameSort = ItemStack(Material.OAK_SIGN)
        val nameSortMeta = nameSort.itemMeta
        nameSortMeta.displayName(Component.text("§a§l出品されたアイテムを検索する"))
        setID(nameSortMeta, "NameSort")
        nameSort.itemMeta = nameSortMeta

        val materialSort = ItemStack(Material.GLASS)
        val materialSortMeta = materialSort.itemMeta
        materialSortMeta.displayName(Component.text("§a§l手に持っているアイテムを検索する"))
        setID(materialSortMeta, "MaterialSort")
        materialSort.itemMeta = materialSortMeta

        val authorSort = ItemStack(Material.PLAYER_HEAD)
        val authorSortMeta = authorSort.itemMeta
        authorSortMeta.displayName(Component.text("§a§l出品者名で検索する"))
        setID(authorSortMeta, "AuthorSort")
        authorSort.itemMeta = authorSortMeta

        val enchantSort = ItemStack(Material.ENCHANTED_BOOK)
        val enchantSortMeta = enchantSort.itemMeta
        enchantSortMeta.displayName(Component.text("§a§lエンチャントで検索する"))
        setID(enchantSortMeta, "EnchantSort")
        enchantSort.itemMeta = enchantSortMeta


        menu.setItem(1,showItem)
        menu.setItem(3,basic)
        menu.setItem(5,sellItem)
        menu.setItem(7,selling)
        menu.setItem(19,nameSort)
        menu.setItem(21,materialSort)
        menu.setItem(23,authorSort)
        menu.setItem(25,enchantSort)

        p.openInventory(menu)
        pushStack()

    }

    override fun click(e: InventoryClickEvent, menu: Menu, id: String, item: ItemStack) {

        when(id){
            "ItemMenu" -> CategoryMenu(p).open()

            "Basic" -> OfficialItemMenu(p).open()
            "SellMenu" -> Man10Commerce.es.execute {
//                MySellingItemMenu(p).open()
            }
            "Selling"    -> {
                p.closeInventory()
                p.sendMessage(
                    Component.text("${Man10Commerce.prefix}§a§n売るアイテムを手に持って、/amsell <金額> を入力してください")
                        .clickEvent(ClickEvent.suggestCommand("/amsell ")))
            }

            "NameSort"->{
                p.closeInventory()
                p.sendMessage(
                    Component.text("${Man10Commerce.prefix}§a§n/amsearch <検索するアイテムの名前> を入力してください")
                        .clickEvent(ClickEvent.suggestCommand("/amsearch ")))
            }

            "MaterialSort"->{
                if (p.inventory.itemInMainHand.type == Material.AIR){
                    p.sendMessage("${Man10Commerce.prefix}§c§l手にアイテムを持ってください！")
                    return
                }
                MaterialMenu(p,p.inventory.itemInMainHand.type).open()
            }
            "AuthorSort"->{
                p.closeInventory()
                p.sendMessage(
                    Component.text("${Man10Commerce.prefix}§a§n/amauthor <出品者名> を入力してください").clickEvent(ClickEvent.suggestCommand("/amauthor ")))
            }
            "EnchantSort"->{
                EnchantMainMenu(p).open()
            }
        }


    }
}