package red.man10.man10commerce.menu

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import red.man10.man10commerce.Man10Commerce.Companion.OP
import red.man10.man10commerce.Man10Commerce.Companion.es
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Man10Commerce.Companion.prefix
import red.man10.man10commerce.Utility.format
import red.man10.man10commerce.Utility.sendMsg
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.data.ItemData.itemDictionary
import red.man10.man10commerce.data.ItemData.opOrderMap
import red.man10.man10commerce.data.ItemData.orderMap
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

object CommerceMenu : Listener{

    private val playerMenuMap = ConcurrentHashMap<Player,String>()
    private val pageMap = ConcurrentHashMap<Player,Int>()
    private val categoryMap = ConcurrentHashMap<Player,String>()

    private const val ITEM_MENU = "${prefix}§l出品中のアイテム一覧"
    private const val SELL_MENU = "${prefix}§l出品したアイテム"
    private const val MAIN_MENU = "${prefix}§lメニュー"
    private const val CATEGORY_MENU = "${prefix}§lカテゴリーメニュー"
    private const val CATEGORY_ITEM = "${prefix}§lカテゴリーアイテム"
    private const val BASIC_MENU = "${prefix}§d§lAmanzonBasic"
    private const val ITEM_LIST_MENU = "${prefix}§l同じアイテムのリスト"

    fun openMainMenu(p:Player){

        val inv = Bukkit.createInventory(null,9, text(MAIN_MENU))

        val showItem = ItemStack(Material.GRASS_BLOCK)
        val shouItemMeta = showItem.itemMeta
        shouItemMeta.displayName(text("§a§l出品されているアイテムをみる"))
        setID(shouItemMeta,"ItemMenu")
        showItem.itemMeta = shouItemMeta

//        val category = ItemStack(Material.BOOK)
//        val categoryMeta = category.itemMeta
//        categoryMeta.displayName(text("§a§lカテゴリーごとに見る"))
//        categoryMeta.lore = mutableListOf("§fオリジナルアイテムなどが","§fカテゴリーごとに分かれています")
//        setID(categoryMeta,"Category")
//        category.itemMeta = categoryMeta
//
        val basic = ItemStack(Material.DIAMOND)
        val basicMeta = basic.itemMeta
        basicMeta.displayName(text("§a§lAmazonBasic"))
        basicMeta.lore = mutableListOf("§f運営が販売している","アイテムを買うことができます")
        setID(basicMeta,"Basic")
        basic.itemMeta = basicMeta

        val sellItem = ItemStack(Material.CHEST)
        val sellItemMeta = sellItem.itemMeta
        sellItemMeta.displayName(text("§a§l出品したアイテムを確かめる"))
        setID(sellItemMeta,"SellMenu")
        sellItem.itemMeta = sellItemMeta

        val selling = ItemStack(Material.COBBLESTONE)
        val sellingMeta = selling.itemMeta
        sellingMeta.displayName(text("§e§lアイテムを出品する"))
        setID(sellingMeta,"Selling")
        selling.itemMeta = sellingMeta

        inv.setItem(1,showItem)
//        inv.setItem(2,category)
        inv.setItem(3,basic)
        inv.setItem(5,sellItem)
        inv.setItem(7,selling)

        p.openInventory(inv)
        playerMenuMap[p] = MAIN_MENU
    }

    //自分が出品したアイテムを確認する
    private fun openSellItemMenu(p:Player, seller: UUID,page: Int){

        if (p.uniqueId!=seller &&!p.hasPermission("commerce.op")){ return }

        val list = ItemData.sellList(seller)

        val inv = Bukkit.createInventory(null,54, text(SELL_MENU))

        var inc = 0

        while (inv.getItem(44) == null){

            if (list.size <= inc+page*45)break

            val data = list[inc+page*45]

            inc ++

            val item = itemDictionary[data.itemID]?.clone()?:continue

            val lore = item.lore?: mutableListOf()

            lore.add("§e§l値段:${format(data.price)}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§e§l${SimpleDateFormat("yyyy-MM/dd").format(data.date)}")
            lore.add("§c§lシフトクリックで出品を取り下げる")

            item.lore = lore

            val meta = item.itemMeta

            meta.persistentDataContainer.set(NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER,data.id)

            item.itemMeta = meta

            inv.addItem(item)

        }

        if (page!=0){

            val prevItem = ItemStack(Material.PAPER)
            val prevMeta = prevItem.itemMeta
            prevMeta.displayName(text("§6§l前ページへ"))
            setID(prevMeta,"prev")

            prevItem.itemMeta = prevMeta

            inv.setItem(45,prevItem)

        }

        if (inc >=44){
            val nextItem = ItemStack(Material.PAPER)
            val nextMeta = nextItem.itemMeta
            nextMeta.displayName(text("§6§l次ページへ"))

            setID(nextMeta,"next")

            nextItem.itemMeta = nextMeta

            inv.setItem(53,nextItem)

        }

        Bukkit.getScheduler().runTask(plugin, Runnable {
            p.openInventory(inv)
            playerMenuMap[p] = SELL_MENU
            pageMap[p] = page
        })

    }

    //出品アイテム一覧を見る
    private fun openItemMenu(p:Player, page:Int){

        val inv = Bukkit.createInventory(null,54, ITEM_MENU)

        val keys = orderMap.keys().toList()

        var inc = 0

        while (inv.getItem(44) ==null){

            if (keys.size <= inc+page*45)break

            val itemID = keys[inc+page*45]

            inc ++

            val data = orderMap[itemID]
            val item = itemDictionary[itemID]?.clone()?:continue

            val lore = item.lore?: mutableListOf()

            if (data==null){

                lore.add("§c§l売り切れ")

                item.lore = lore

                inv.addItem(item)
                continue
            }

            lore.add("§e§l値段:${format(floor(data.price))}")
            lore.add("§e§l単価:${format(floor(data.price/data.amount))}")
            lore.add("§e§l出品者${Bukkit.getOfflinePlayer(data.seller!!).name}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§e§l${SimpleDateFormat("yyyy-MM-dd").format(data.date)}")
            if (data.isOp) lore.add("§d§l公式出品アイテム")
            lore.add("§cシフトクリックで1-Click購入")

            val meta = item.itemMeta
            meta.persistentDataContainer.set(NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER,data.id)
            meta.persistentDataContainer.set(NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER,data.itemID)
            item.itemMeta = meta

            item.lore = lore

            inv.addItem(item)

        }

        val reloadItem = ItemStack(Material.COMPASS)
        val reloadMeta = reloadItem.itemMeta
        reloadMeta.displayName(text("§6§lリロード"))
        setID(reloadMeta,"reload")
        reloadItem.itemMeta = reloadMeta
        inv.setItem(49,reloadItem)


        if (page!=0){

            val prevItem = ItemStack(Material.PAPER)
            val prevMeta = prevItem.itemMeta
            prevMeta.displayName(text("§6§l前ページへ"))
            setID(prevMeta,"prev")

            prevItem.itemMeta = prevMeta

            inv.setItem(45,prevItem)

        }

        if (inc >=44){
            val nextItem = ItemStack(Material.PAPER)
            val nextMeta = nextItem.itemMeta
            nextMeta.displayName(text("§6§l次ページへ"))

            setID(nextMeta,"next")

            nextItem.itemMeta = nextMeta

            inv.setItem(53,nextItem)

        }

        p.openInventory(inv)
        playerMenuMap[p] = ITEM_MENU
        pageMap[p] = page

    }

    private fun openCategoryMenu(p:Player){

        val inv = Bukkit.createInventory(null,18, text(CATEGORY_MENU))

        val allItemIcon = ItemStack(Material.COBBLESTONE)

        val meta = allItemIcon.itemMeta
        meta.displayName(text("§a§lすべてのアイテムをみる"))
        setID(meta,"all")
        allItemIcon.itemMeta = meta

        inv.addItem(allItemIcon)

        for (data in ItemData.categories.values){
            inv.addItem(data.categoryIcon)
        }

        p.openInventory(inv)
        playerMenuMap[p] = CATEGORY_MENU

    }

    //カテゴリーわけされた出品アイテム一覧を見る
    private fun openCategoryList(p:Player, category:String, page:Int){

        val inv = Bukkit.createInventory(null,54, text(CATEGORY_ITEM))

        val keys = ItemData.getCategorized(category)?.keys?.toList()?: mutableListOf()

        var inc = 0

        while (inv.getItem(44) ==null){

            if (keys.size <= inc+page*45)break
//            if (setCount>=categorizeID.size)break

            val itemID = keys[inc+page*45]

            inc ++

            val data = orderMap[itemID]
            val item = itemDictionary[itemID]?.clone()?:continue

//            if (!categorizeID.contains(itemID))continue

            val lore = item.lore?: mutableListOf()

            if (data==null){

                lore.add("§c§l売り切れ")

                item.lore = lore

                inv.addItem(item)
                continue
            }

            lore.add("§e§l値段:${format(floor(data.price))}")
            lore.add("§e§l単価:${format(floor(data.price/data.amount))}")
            lore.add("§e§l出品者${Bukkit.getOfflinePlayer(data.seller!!).name}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§e§l${SimpleDateFormat("yyyy-MM-dd").format(data.date)}")
            if (data.isOp) lore.add("§d§l公式出品アイテム")
            lore.add("§cシフトクリックで1-Click購入")

            val meta = item.itemMeta
            meta.persistentDataContainer.set(NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER,data.id)
            meta.persistentDataContainer.set(NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER,data.itemID)
            item.itemMeta = meta

            item.lore = lore

            inv.addItem(item)

//            setCount ++

        }

        val reloadItem = ItemStack(Material.COMPASS)
        val reloadMeta = reloadItem.itemMeta
        reloadMeta.displayName(text("§6§lリロード"))
        setID(reloadMeta,"reload")
        reloadItem.itemMeta = reloadMeta
        inv.setItem(49,reloadItem)


        if (page!=0){

            val prevItem = ItemStack(Material.PAPER)
            val prevMeta = prevItem.itemMeta
            prevMeta.displayName(text("§6§l前ページへ"))
            setID(prevMeta,"prev")

            prevItem.itemMeta = prevMeta

            inv.setItem(45,prevItem)

        }

        if (inc>=44){
            val nextItem = ItemStack(Material.PAPER)
            val nextMeta = nextItem.itemMeta
            nextMeta.displayName(text("§6§l次ページへ"))

            setID(nextMeta,"next")

            nextItem.itemMeta = nextMeta

            inv.setItem(53,nextItem)

        }

        p.openInventory(inv)
        playerMenuMap[p] = CATEGORY_ITEM
        categoryMap[p] = category
        pageMap[p] = page

    }

    //Amanzon Basic
    private fun openOPMenu(p:Player, page:Int){

        val inv = Bukkit.createInventory(null,54, BASIC_MENU)

        val keys = opOrderMap.keys().toList()

        var inc = 0

        while (inv.getItem(44) ==null){

            if (keys.size <= inc+page*45)break

            val itemID = keys[inc+page*45]

            inc ++

            val data = orderMap[itemID]
            val item = itemDictionary[itemID]?.clone()?:continue

            val lore = item.lore?: mutableListOf()

            if (data==null){

                lore.add("§c§l売り切れ")

                item.lore = lore

                inv.addItem(item)
                continue
            }

            lore.add("§e§l値段:${format(floor(data.price))}")
            lore.add("§e§l単価:${format(floor(data.price/data.amount))}")
            lore.add("§e§l出品者${Bukkit.getOfflinePlayer(data.seller!!).name}")
            lore.add("§e§l個数:${data.amount}")
            lore.add("§e§l${SimpleDateFormat("yyyy-MM/dd").format(data.date)}")
            if (data.isOp) lore.add("§d§l公式出品アイテム")
            lore.add("§cシフトクリックで1-Click購入")

            val meta = item.itemMeta
            meta.persistentDataContainer.set(NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER,data.id)
            meta.persistentDataContainer.set(NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER,data.itemID)
            item.itemMeta = meta

            item.lore = lore

            inv.addItem(item)

        }

        val reloadItem = ItemStack(Material.COMPASS)
        val reloadMeta = reloadItem.itemMeta
        reloadMeta.displayName(text("§6§lリロード"))
        setID(reloadMeta,"reload")
        reloadItem.itemMeta = reloadMeta
        inv.setItem(49,reloadItem)


        if (page!=0){

            val prevItem = ItemStack(Material.PAPER)
            val prevMeta = prevItem.itemMeta
            prevMeta.displayName(text("§6§l前ページへ"))
            setID(prevMeta,"prev")

            prevItem.itemMeta = prevMeta

            inv.setItem(45,prevItem)

        }

        if (inc >=44){
            val nextItem = ItemStack(Material.PAPER)
            val nextMeta = nextItem.itemMeta
            nextMeta.displayName(text("§6§l次ページへ"))

            setID(nextMeta,"next")

            nextItem.itemMeta = nextMeta

            inv.setItem(53,nextItem)

        }


        p.openInventory(inv)
        playerMenuMap[p] = BASIC_MENU
        pageMap[p] = page

    }

    private fun showItemList(p: Player, itemID:Int){

        val inv = Bukkit.createInventory(null,54, text(ITEM_LIST_MENU))

        val list = ItemData.getAllItem(itemID)

        Bukkit.getScheduler().runTask(plugin, Runnable {

            for (data in list){
                if (inv.last()!=null)break

                val item = itemDictionary[itemID]?.clone()?:return@Runnable

                item.amount = data.amount

                val lore = item.lore?: mutableListOf()

                lore.add("§e§l値段:${format(floor(data.price))}")
                lore.add("§e§l単価:${format(floor(data.price/data.amount))}")
                lore.add("§e§l出品者${Bukkit.getOfflinePlayer(data.seller!!).name}")
                lore.add("§e§l個数:${data.amount}")
                lore.add("§e§l${SimpleDateFormat("yyyy-MM-dd").format(data.date)}")
                if (data.isOp) lore.add("§d§l公式出品アイテム")
                lore.add("§cシフトクリックで1-Click購入")

                val meta = item.itemMeta
                meta.persistentDataContainer.set(NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER,data.id)
                meta.persistentDataContainer.set(NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER,data.itemID)
                item.itemMeta = meta

                item.lore = lore

                inv.addItem(item)

            }

            p.openInventory(inv)
            playerMenuMap[p] = ITEM_LIST_MENU
            pageMap[p] = itemID

        })

    }

    fun setID(meta:ItemMeta, value:String){
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

        e.isCancelled = true

        val item = e.currentItem?:return
        val action = e.action
        val id = getID(item)

        p.playSound(p.location,Sound.UI_BUTTON_CLICK,0.1F,1.0F)

        when(menuName){

            ITEM_MENU ->{

                val page = pageMap[p]?:0

                when(id){
                    "prev" ->{ openItemMenu(p,page-1) }

                    "next" ->{ openItemMenu(p,page+1) }

                    "reload" ->{ openItemMenu(p,page) }

                    else ->{

                        if (id != ""){
                            openCategoryList(p,id,0)
                            return
                        }

                        val meta = item.itemMeta?:return

                        val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:-1
                        val itemID = meta.persistentDataContainer[NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER]?:-1

                        if (orderID == -1)return

                        if (p.hasPermission(OP) && action == InventoryAction.CLONE_STACK){
                            ItemData.close(orderID,p)
                            sendMsg(p,"§c§l出品を取り下げました")
                            Bukkit.getScheduler().runTask(plugin, Runnable { openItemMenu(p,page) })
                            return
                        }

                        if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY){
                            es.execute { showItemList(p,itemID) }
                            return
                        }

                        ItemData.buy(p,itemID,orderID){ code:Int ->
                            when(code){
                                0 -> { sendMsg(p,"§c§l購入失敗！電子マネーが足りません！") }
                                1 -> {sendMsg(p,"§a§l購入成功！")}
                                4 -> {sendMsg(p,"§a§lインベントリに空きがありません！")}
                                3,5 -> { sendMsg(p,"購入しようとしたアイテムが売り切れています！")}
                                else ->{ sendMsg(p,"エラー:${code} サーバー運営者、GMに報告してください")}
                            }

                            Bukkit.getScheduler().runTask(plugin, Runnable { openItemMenu(p,page) })
                        }


                        return
                    }
                }
            }

            CATEGORY_MENU ->{

                if (id != ""){

                    if (id=="all") {
                        openItemMenu(p,0)
                        return
                    }

                    openCategoryList(p,id,0)
                    return
                }

            }

            CATEGORY_ITEM ->{

                val page = pageMap[p]?:0
                val category = categoryMap[p]?:"none"

                when(id){
                    "prev" ->{ openCategoryList(p,category,page-1) }

                    "next" ->{ openCategoryList(p,category,page+1) }

                    "reload" ->{ openCategoryList(p,category,page) }

                    else ->{

                        val meta = item.itemMeta?:return

                        val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:-1
                        val itemID = meta.persistentDataContainer[NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER]?:-1

                        if (orderID == -1)return

                        if (p.hasPermission(OP) && action == InventoryAction.CLONE_STACK){
                            ItemData.close(orderID,p)
                            sendMsg(p,"§c§l出品を取り下げました")
                            Bukkit.getScheduler().runTask(plugin, Runnable { openCategoryList(p,category,page) })
                            return
                        }

                        if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY){
                            es.execute { showItemList(p,itemID) }
                            return
                        }

                        ItemData.buy(p,itemID,orderID){ code:Int ->
                            when(code){
                                0 -> { sendMsg(p,"§c§l購入失敗！電子マネーが足りません！") }
                                1 -> {sendMsg(p,"§a§l購入成功！")}
                                4 -> {sendMsg(p,"§a§lインベントリに空きがありません！")}
                                3,5 -> { sendMsg(p,"購入しようとしたアイテムが売り切れています！")}
                                else ->{ sendMsg(p,"エラー:${code} サーバー運営者、GMに報告してください")}
                            }

                            Bukkit.getScheduler().runTask(plugin, Runnable { openCategoryList(p,category,page) })
                        }

                        return
                    }
                }
            }

            ITEM_LIST_MENU ->{

                val itemID = pageMap[p]?:0

                val meta = item.itemMeta?:return

                val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:-1

                if (orderID == -1)return

                if (p.hasPermission(OP) && action == InventoryAction.CLONE_STACK){
                    ItemData.close(orderID,p)
                    sendMsg(p,"§c§l出品を取り下げました")
                    showItemList(p,itemID)
                    return
                }

                if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY)return

                ItemData.buy(p,itemID,orderID){ code:Int ->
                    when(code){
                        0 -> { sendMsg(p,"§c§l購入失敗！電子マネーが足りません！") }
                        1 -> {sendMsg(p,"§a§l購入成功！")}
                        4 -> {sendMsg(p,"§a§lインベントリに空きがありません！")}
                        3,5 -> { sendMsg(p,"購入しようとしたアイテムが売り切れています！")}
                        else ->{ sendMsg(p,"エラー:${code} サーバー運営者、GMに報告してください")}
                    }

                    Bukkit.getScheduler().runTask(plugin, Runnable { showItemList(p,itemID) })
                }

                return
            }

            SELL_MENU ->{

                if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY)return

                val page = pageMap[p]?:0

                val meta = item.itemMeta!!
                val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:0

                es.execute {

                    if (id=="prev"){
                        openSellItemMenu(p,p.uniqueId,page-1)
                        return@execute
                    }

                    if (id=="next"){
                        openSellItemMenu(p,p.uniqueId,page+1)
                        return@execute
                    }

                    if (ItemData.close(orderID,p)){
                        sendMsg(p,"出品を取り下げました")
                        openSellItemMenu(p,p.uniqueId,page)
                    }
                }

                return
            }

            MAIN_MENU ->{

                when(id){
                    "ItemMenu" -> openCategoryMenu(p)
//                    "Category" ->
                    "Basic" -> openOPMenu(p,0)
                    "SellMenu" -> es.execute { openSellItemMenu(p,p.uniqueId,0) }
                    "Selling"    -> {
                        p.closeInventory()
                        p.sendMessage(
                            text("${prefix}§a§n売るアイテムを手に持って、/amsell <金額> を入力してください")
                            .clickEvent(ClickEvent.suggestCommand("/amsell ")))
                    }
                }
            }

            BASIC_MENU ->{

                val page = pageMap[p]?:0

                when(id){
                    "prev" ->{ openOPMenu(p,page-1) }

                    "next" ->{ openOPMenu(p,page+1) }

                    "reload" ->{ openOPMenu(p,page) }

                    else ->{
                        if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY)return

                        val meta = item.itemMeta!!

                        val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:-1
                        val itemID = meta.persistentDataContainer[NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER]?:-1

                        if (orderID == -1)return

                        if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY){
                            es.execute { showItemList(p,itemID) }
                            return
                        }

                        ItemData.buy(p,itemID,orderID){ code:Int ->
                            when(code){
                                0 -> { sendMsg(p,"§c§l購入失敗！電子マネーが足りません！") }
                                1 -> {sendMsg(p,"§a§l購入成功！")}
                                4 -> {sendMsg(p,"§a§lインベントリに空きがありません！")}
                                3,5 -> { sendMsg(p,"購入しようとしたアイテムが売り切れています！")}
                                else ->{ sendMsg(p,"エラー:${code} サーバー運営者、GMに報告してください")}
                            }

                            Bukkit.getScheduler().runTask(plugin, Runnable { openOPMenu(p,page) })
                        }


                        return
                    }
                }
            }
        }

    }

    @EventHandler
    fun closeInventory(e:InventoryCloseEvent){

        val p = e.player as Player

        if (playerMenuMap.containsKey(p)) playerMenuMap.remove(p)
        if (pageMap.containsKey(p)) pageMap.remove(p)
    }
}