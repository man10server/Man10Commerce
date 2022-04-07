package red.man10.man10commerce.menu

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Utility
import red.man10.man10commerce.data.Data
import red.man10.man10commerce.data.ItemData
import java.text.SimpleDateFormat
import kotlin.math.floor


/**
 * ページ切り替えをするメニュー用の抽象クラス
 */
abstract class ListMenu(title:String,p:Player) : Menu(title,54,p) {

    var page = 0

    fun next(){
        page++
        open()
    }

    fun previous(){
        page--
        open()
    }

    /**
     * 一覧形式で表示させる時の雛型
     */
    fun listInventory(keys : List<Int>): Inventory {

        var inc = 0

        while (menu.getItem(44) ==null){

            if (keys.size <= inc+page*45)break

            val itemID = keys[inc+page*45]

            inc ++

            val data = ItemData.orderMap[itemID]
            val item = ItemData.itemDictionary[itemID]?.clone()?:continue

            menu.addItem(addItemInformation(item, data))

        }

        setPageButton()

        return menu
    }

    /**
     * リロードとページ切り替えボタンを追加
     */
    protected fun setPageButton(){
        if (page!=0){

            val prevItem = ItemStack(Material.PAPER)
            val prevMeta = prevItem.itemMeta
            prevMeta.displayName(Component.text("§6§l前ページへ"))
            setID(prevMeta, "prev")

            prevItem.itemMeta = prevMeta

            menu.setItem(45,prevItem)

        }

        if (menu.getItem(44)!=null ){
            val nextItem = ItemStack(Material.PAPER)
            val nextMeta = nextItem.itemMeta
            nextMeta.displayName(Component.text("§6§l次ページへ"))

            setID(nextMeta, "next")

            nextItem.itemMeta = nextMeta

            menu.setItem(53,nextItem)

        }

        val reloadItem = ItemStack(Material.COMPASS)
        val reloadMeta = reloadItem.itemMeta
        reloadMeta.displayName(Component.text("§6§lリロード"))
        setID(reloadMeta, "reload")
        reloadItem.itemMeta = reloadMeta
        menu.setItem(49,reloadItem)
    }

    /**
     * アイテムに情報をつける
     */
    fun addItemInformation(item: ItemStack, data : Data?): ItemStack {
        val lore = item.lore?: mutableListOf()

        if (data==null){

            lore.add("§c§l売り切れ")

            item.lore = lore

            return item
        }

        lore.add("§e§l値段:${Utility.format(floor(data.price))}")
        lore.add("§e§l単価:${Utility.format(floor(data.price / data.amount))}")
        lore.add("§e§l出品者${Bukkit.getOfflinePlayer(data.seller!!).name}")
        lore.add("§e§l個数:${data.amount}")
        lore.add("§e§l${SimpleDateFormat("yyyy-MM/dd").format(data.date)}")
        if (data.isOp) lore.add("§d§l公式出品アイテム")
        lore.add("§cシフトクリックで1-Click購入")

        val meta = item.itemMeta
        meta.persistentDataContainer.set(NamespacedKey(Man10Commerce.plugin,"order_id"), PersistentDataType.INTEGER,data.id)
        meta.persistentDataContainer.set(NamespacedKey(Man10Commerce.plugin,"item_id"), PersistentDataType.INTEGER,data.itemID)
        item.itemMeta = meta

        item.lore = lore
        return item
    }
}