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
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.data.ItemData.itemIndex
import red.man10.man10commerce.data.ItemData.itemList
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

object CommerceMenu : Listener{

    private val playerMenuMap = HashMap<Player,String>()
    private val pageMap = HashMap<Player,Int>()

    private const val ITEM_MENU = "§e§l出品中のアイテム一覧"
    private const val SELL_MENU = "§e§l出品したアイテム"
    private const val MAIN_MENU = "§e§lメニュー"

    fun openMainMenu(p:Player){

        val inv = Bukkit.createInventory(null,9, MAIN_MENU)



    }

    fun openSellItemMenu(p:Player,seller: UUID){

        if (p.uniqueId!=seller &&!p.hasPermission("commerce.op")){ return }

        val list = ItemData.sellList(seller)?:return

        val inv = Bukkit.createInventory(null,54, SELL_MENU)

        for (i in 0 .. 53){
            val data = list[i]

            val item = itemIndex[data.itemID]!!.clone()

            val lore = item.lore?: mutableListOf()

            lore.add("§e§l値段:${Utility.format(data.price)}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§e§l${SimpleDateFormat("yyyy-MM/dd").format(data.date)}")
            lore.add("§c§lシフトクリックで出品を取り下げる")

            item.lore = lore

            inv.addItem(item)
        }

        Bukkit.getScheduler().runTask(plugin, Runnable { p.openInventory(inv) })

    }

    fun openItemMenu(p:Player,page:Int){

        val inv = Bukkit.createInventory(null,54, ITEM_MENU)

        val keys = itemIndex.keys().toList()

        var inc = 0

        Bukkit.getLogger().info("page$page")


        for (i in page*45 .. (page+1)*44){

            Bukkit.getLogger().info("$i,$inc")

            if (keys.size <= i)break

            val itemID = keys[i]

            val data = itemList[itemID]?:continue
            val item = itemIndex[itemID]!!.clone()

            val lore = item.lore?: mutableListOf()

            lore.add("§e§l値段:${Utility.format(data.price)}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§cシフト左クリックで1Click購入")

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

                                p.sendMessage("購入成功")

                            }else{
                                p.sendMessage("購入失敗")
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
                    if (ItemData.close(orderID)){
                        p.sendMessage("出品を取り下げました")
                    }
                }

                return
            }

            MAIN_MENU ->{



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