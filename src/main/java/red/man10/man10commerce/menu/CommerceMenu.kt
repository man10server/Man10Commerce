package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import red.man10.man10commerce.Man10Commerce.Companion.es
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Utility
import red.man10.man10commerce.Utility.sendMsg
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.data.ItemData.itemIndex
import red.man10.man10commerce.data.ItemData.itemList
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap

object CommerceMenu : Listener{

    private val playerMenuMap = ConcurrentHashMap<Player,String>()
    private val pageMap = HashMap<Player,Int>()

    private const val ITEM_MENU = "§e§l出品中のアイテム一覧"
    private const val SELL_MENU = "§e§l出品したアイテム"
    private const val MAIN_MENU = "§e§lメニュー"

    fun openMainMenu(p:Player){

        val inv = Bukkit.createInventory(null,9, MAIN_MENU)

        val button1 = ItemStack(Material.GRASS_BLOCK)
        val meta1 = button1.itemMeta
        meta1.setDisplayName("§a§l現在出品中のアイテムを見る")
        setID(meta1,"ItemMenu")
        button1.itemMeta = meta1

        val button2 = ItemStack(Material.CHEST)
        val meta2 = button2.itemMeta
        meta2.setDisplayName("§a§l出品したアイテムを見る")
        setID(meta2,"SellMenu")
        button2.itemMeta = meta2

        inv.setItem(2,button1)
        inv.setItem(6,button2)

        playerMenuMap[p] = MAIN_MENU

        p.openInventory(inv)

    }

    private fun openSellItemMenu(p:Player, seller: UUID){

        if (p.uniqueId!=seller &&!p.hasPermission("commerce.op")){ return }

        val list = ItemData.sellList(seller)?:return

        val inv = Bukkit.createInventory(null,54, SELL_MENU)

        for (i in 0 .. 53){

            if (list.size <=i)break

            val data = list[i]

            val item = itemIndex[data.itemID]!!.clone()

            val lore = item.lore?: mutableListOf()

            lore.add("§e§l値段:${Utility.format(data.price)}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§e§l${SimpleDateFormat("yyyy-MM/dd").format(data.date)}")
            lore.add("§c§lシフトクリックで出品を取り下げる")

            item.lore = lore

            val meta = item.itemMeta

            meta.persistentDataContainer.set(NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER,data.id)

            item.itemMeta = meta

            inv.setItem(i,item)
        }

        Bukkit.getScheduler().runTask(plugin, Runnable {
            p.openInventory(inv)
            playerMenuMap[p] = SELL_MENU
        })
    }

    private fun openItemMenu(p:Player, page:Int){

        val inv = Bukkit.createInventory(null,54, ITEM_MENU)

        val keys = itemIndex.keys().toList()

        var inc = 0

        for (i in page*45 .. (page+1)*44){

            if (keys.size <= i)break

            val itemID = keys[i]

            val data = itemList[itemID]?:continue
            val item = itemIndex[itemID]!!.clone()

            val lore = item.lore?: mutableListOf()

            lore.add("§e§l値段:${Utility.format(data.price)}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§cシフトクリックで1Click購入")

            val meta = item.itemMeta
            meta.persistentDataContainer.set(NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER,data.id)
            meta.persistentDataContainer.set(NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER,data.itemID)
            item.itemMeta = meta

            item.lore = lore

            inv.setItem(inc,item)

            inc ++

        }

        val reloadItem = ItemStack(Material.NETHER_STAR)
        val reloadMeta = reloadItem.itemMeta
        reloadMeta.setDisplayName("§6§lリロード")
        setID(reloadMeta,"reload")
        reloadItem.itemMeta = reloadMeta
        inv.setItem(49,reloadItem)


        if (page!=0){

            val prevItem = ItemStack(Material.PAPER)
            val prevMeta = prevItem.itemMeta
            prevMeta.setDisplayName("§§l前ページへ")
            setID(prevMeta,"prev")

            prevItem.itemMeta = prevMeta

            inv.setItem(45,prevItem)

        }

        if (inc >=44){
            val nextItem = ItemStack(Material.PAPER)
            val nextMeta = nextItem.itemMeta
            nextMeta.setDisplayName("§§l次ページへ")

            setID(nextMeta,"next")

            nextItem.itemMeta = nextMeta

            inv.setItem(53,nextItem)

        }

        p.openInventory(inv)
        playerMenuMap[p] = ITEM_MENU
        pageMap[p] = page

    }

    private fun setID(meta:ItemMeta, value:String){
        meta.persistentDataContainer.set(NamespacedKey(plugin,"id"), PersistentDataType.STRING,value)
    }

    private fun getID(itemStack: ItemStack):String{
        return itemStack.itemMeta?.persistentDataContainer?.get(NamespacedKey(plugin,"id"), PersistentDataType.STRING)
            ?:""
    }

    ////////////////////////////////////////////////////

    @EventHandler
    fun inventoryClick(e:InventoryClickEvent){

        val p = e.whoClicked as Player

        val menuName = playerMenuMap[p]?:return

        val item = e.currentItem?:return
        val action = e.action
        val id = getID(item)

        e.isCancelled = true

        when(menuName){

            ITEM_MENU ->{

                val page = pageMap[p]?:0

                when(id){
                    "prev" ->{ openItemMenu(p,page-1) }

                    "next" ->{ openItemMenu(p,page+1) }

                    "reload" ->{ openItemMenu(p,page) }

                    else ->{
                        if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY)return

                        val meta = item.itemMeta!!

                        val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:0
                        val itemID = meta.persistentDataContainer[NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER]?:0

                        es.execute {
                            if (ItemData.buy(p,itemID,orderID)){
                                sendMsg(p,"§a§l購入成功しました！")
                            }else{
                                sendMsg(p,"§c§l購入失敗、Man10Bankにお金がないか、既に売り切れています！")
                            }

                            Bukkit.getScheduler().runTask(plugin, Runnable { openItemMenu(p,page) })
                        }

                        return
                    }
                }
            }

            SELL_MENU ->{

                if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY)return

                val meta = item.itemMeta!!
                val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:0

                es.execute {
                    if (ItemData.close(orderID,p)){
                        sendMsg(p,"出品を取り下げました")
                        openSellItemMenu(p,p.uniqueId)
                    }
                }

                return
            }

            MAIN_MENU ->{

                when(id){
                    "ItemMenu" -> openItemMenu(p,0)
                    "SellMenu" -> es.execute { openSellItemMenu(p,p.uniqueId) }
                }

            }
        }

    }

    @EventHandler
    fun closeInventory(e:InventoryCloseEvent){

        val p = e.player

        if (playerMenuMap.containsKey(p)) playerMenuMap.remove(p)
        if (pageMap.containsKey(p)) pageMap.remove(p)
    }
}