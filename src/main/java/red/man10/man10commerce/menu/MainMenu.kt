package red.man10.man10commerce.menu

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10commerce.Man10Commerce

class MainMenu(p:Player) : MenuFramework(p, CHEST_SIZE,"§l出品中のアイテム一覧"){

    init {

        //スタックに追加
        push()

        val buttonShowItem = Button(Material.GRASS_BLOCK)
        buttonShowItem.title("§a§l出品されているアイテムをみる")
        buttonShowItem.setClickAction{
            CategoryMenu(p).open()
        }
        setButton(buttonShowItem,1)

        val buttonBasic = Button(Material.DIAMOND)
        buttonBasic.title("§a§lAmanzonBasic")
        buttonBasic.lore(mutableListOf("§f公式が販売している","§fアイテムを買うことができます"))
        buttonBasic.setClickAction{

        }
        setButton(buttonBasic,3)

        val buttonMySellItem = Button(Material.CHEST)
        buttonMySellItem.title("§a§l自分が出品したアイテムを確かめる")
        buttonMySellItem.setClickAction{

        }
        setButton(buttonMySellItem,5)

        val buttonToSell = Button(Material.COBBLESTONE)
        buttonToSell.title("§a§lアイテムを出品する")
        buttonToSell.setClickAction{

        }
        setButton(buttonToSell,7)

        val buttonSearchByName = Button(Material.OAK_SIGN)
        buttonSearchByName.title("§a§l出品されたアイテムを検索する")
        buttonSearchByName.setClickAction{
            p.closeInventory()
            p.sendMessage(
                Component.text("${Man10Commerce.prefix}§a§n/amsearch <検索するアイテムの名前> を入力してください")
                    .clickEvent(ClickEvent.suggestCommand("/amsearch ")))
        }
        setButton(buttonSearchByName,19)

        val buttonSearchByMaterial = Button(Material.GLASS)
        buttonSearchByMaterial.title("§a§l手に持っているアイテムから検索する")
        buttonSearchByMaterial.setClickAction{
            if (p.inventory.itemInMainHand.type == Material.AIR){
                p.sendMessage("${Man10Commerce.prefix}§c§l手にアイテムを持ってください！")
                return@setClickAction
            }
            MaterialMenu(p,p.inventory.itemInMainHand.type).open()
        }
        setButton(buttonSearchByMaterial,21)

        val buttonSearchByAuthor = Button(Material.PLAYER_HEAD)
        buttonSearchByAuthor.title("§a§l出品者名で検索する")
        buttonSearchByAuthor.setClickAction{
            p.closeInventory()
            p.sendMessage(
                Component.text("${Man10Commerce.prefix}§a§n/amauthor <出品者名> を入力してください").clickEvent(ClickEvent.suggestCommand("/amauthor ")))

        }
        setButton(buttonSearchByAuthor,23)

        val buttonSearchByEnchant = Button(Material.ENCHANTED_BOOK)
        buttonSearchByEnchant.title("§a§lエンチャントで検索する")
        buttonSearchByEnchant.setClickAction{
            EnchantMainMenu(p).open()
        }
        setButton(buttonSearchByEnchant,25)
    }
}