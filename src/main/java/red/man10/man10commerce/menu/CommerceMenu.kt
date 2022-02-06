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
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import red.man10.man10commerce.Man10Commerce.Companion.OP
import red.man10.man10commerce.Man10Commerce.Companion.es
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Man10Commerce.Companion.prefix
import red.man10.man10commerce.Utility.format
import red.man10.man10commerce.Utility.sendMsg
import red.man10.man10commerce.data.Data
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.data.ItemData.itemDictionary
import red.man10.man10commerce.data.ItemData.opOrderMap
import red.man10.man10commerce.data.ItemData.orderMap
import red.man10.man10commerce.menu.CommerceMenu.Menu
import red.man10.man10commerce.sort.Sort
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

object CommerceMenu : Listener{

    private val menuStack = ConcurrentHashMap<Player,Stack<MenuData>>()


    class MenuData{
        var name = ""
        var page = 0
        var category : String? = ""
        var search : String? = null
        var material : Material? = null
        lateinit var menu : Menu
    }

    private const val ITEM_MENU = "${prefix}§l出品中のアイテム一覧"
    private const val SELL_MENU = "${prefix}§l出品したアイテム"
    private const val MAIN_MENU = "${prefix}§lメニュー"
    private const val CATEGORY_MENU = "${prefix}§lカテゴリーメニュー"
    private const val CATEGORY_ITEM = "${prefix}§lカテゴリーアイテム"
    private const val BASIC_MENU = "${prefix}§d§lAmanzonBasic"
    private const val ITEM_LIST_MENU = "${prefix}§l同じアイテムのリスト"
    private const val QUERY_MENU = "${prefix}§l検索結果"
    private const val MATERIAL_MENU = "${prefix}§l同じ種類のリスト"

    fun openMainMenu(p:Player){

        val inv = Bukkit.createInventory(null,27, text(MAIN_MENU))

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

        val nameSort = ItemStack(Material.OAK_SIGN)
        val nameSortMeta = nameSort.itemMeta
        nameSortMeta.displayName(text("§a§l出品されたアイテムを検索する"))
        setID(nameSortMeta,"NameSort")
        nameSort.itemMeta = nameSortMeta

        val materialSort = ItemStack(Material.GLASS)
        val materialSortMeta = materialSort.itemMeta
        materialSortMeta.displayName(text("§a§l手に持っているアイテムを検索する"))
        setID(materialSortMeta,"MaterialSort")
        materialSort.itemMeta = materialSortMeta

        inv.setItem(1,showItem)
//        inv.setItem(2,category)
        inv.setItem(3,basic)
        inv.setItem(5,sellItem)
        inv.setItem(7,selling)
        inv.setItem(19,nameSort)
        inv.setItem(21,materialSort)


        p.openInventory(inv)

        val data = MenuData()
        data.name = MAIN_MENU
        data.menu= Menu { openMainMenu(p) }
        pushStack(p,data)
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

            val oldData = peekStack(p)
            if (oldData!=null && oldData.name == SELL_MENU){ popStack(p) }

            val data = MenuData()
            data.name = SELL_MENU
            data.page = page
            data.menu= Menu { openSellItemMenu(p,seller, page) }

            pushStack(p,data)
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
//        pageMap[p] = page

        val oldData = peekStack(p)
        if (oldData!=null && oldData.name == ITEM_MENU){ popStack(p) }

        val data = MenuData()
        data.name = ITEM_MENU
        data.page = page
        data.menu= Menu { openItemMenu(p,page) }

        pushStack(p,data)

    }

    //カテゴリー一覧
    fun openCategoryMenu(p:Player){

        val inv = Bukkit.createInventory(null,27, text(CATEGORY_MENU))

        val allItemIcon = ItemStack(Material.COBBLESTONE)

        val meta = allItemIcon.itemMeta
        meta.displayName(text("§a§lすべてのアイテムをみる"))
        setID(meta,"all")
        allItemIcon.itemMeta = meta

        inv.addItem(allItemIcon)

        for (data in ItemData.categories.values){
            inv.addItem(data.categoryIcon)
        }

        val notCategorized = ItemStack(Material.BARRIER)

        val meta2 = notCategorized.itemMeta
        meta2.displayName(text("§a§lカテゴリーわけされてないアイテムを見る"))
        setID(meta2,"not")
        notCategorized.itemMeta = meta2

        inv.addItem(notCategorized)



        p.openInventory(inv)

        val data = MenuData()
        data.name = CATEGORY_MENU
        data.menu= Menu { openCategoryMenu(p) }

        pushStack(p,data)


    }

    //カテゴリーわけされた出品アイテム一覧を見る
    private fun openCategoryList(p:Player, category:String, page:Int){

        val inv = Bukkit.createInventory(null,54, text(CATEGORY_ITEM))

        val keys = ItemData.getCategorized(category).keys.toList()

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

        val oldData = peekStack(p)
        if (oldData!=null && oldData.name == CATEGORY_ITEM){ popStack(p) }

        val data = MenuData()
        data.name = CATEGORY_ITEM
        data.page = page
        data.category = category
        data.menu= Menu { openCategoryList(p, category, page) }

        pushStack(p,data)

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
//        pageMap[p] = page

        val oldData = peekStack(p)
        if (oldData!=null && oldData.name == BASIC_MENU){ popStack(p) }

        val data = MenuData()
        data.name = BASIC_MENU
        data.page = page
        data.menu= Menu { openOPMenu(p,page) }

        pushStack(p,data)

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

            val oldData = peekStack(p)
            if (oldData!=null && oldData.name == ITEM_LIST_MENU && oldData.page==itemID) popStack(p)

            val data = MenuData()
            data.name = ITEM_LIST_MENU
            data.page = itemID
            data.menu= Menu { showItemList(p, itemID) }

            pushStack(p,data)

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

        val menuData = peekStack(p)?:return

        e.isCancelled = true

        val item = e.currentItem?:return
        val action = e.action
        val id = getID(item)
        val page = menuData.page

        p.playSound(p.location,Sound.UI_BUTTON_CLICK,0.1F,1.0F)

        when(menuData.name){

            ITEM_MENU ->{

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

                        ItemData.buy(p,itemID,orderID){
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

                val category = menuData.category?:"all"

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

                        ItemData.buy(p,itemID,orderID){
                            Bukkit.getScheduler().runTask(plugin, Runnable { openCategoryList(p,category,page) })
                        }

                        return
                    }
                }
            }

            ITEM_LIST_MENU ->{

                val meta = item.itemMeta?:return

                val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:-1

                if (orderID == -1)return

                if (p.hasPermission(OP) && action == InventoryAction.CLONE_STACK){
                    ItemData.close(orderID,p)
                    sendMsg(p,"§c§l出品を取り下げました")
                    showItemList(p, page)
                    return
                }

                if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY) { return }

                ItemData.buy(p, page, orderID) {
                    Bukkit.getScheduler().runTask(plugin, Runnable { showItemList(p, page) })
                }

                return
            }

            SELL_MENU ->{

                if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY){return}

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

                    "NameSort"->{
                        p.closeInventory()
                        p.sendMessage(
                            text("${prefix}§a§n/amsearch <アイテム名> を入力してください").clickEvent(ClickEvent.suggestCommand("/amsearch ")))
                    }

                    "MaterialSort"->{
                        if (p.inventory.itemInMainHand.type == Material.AIR){
                            p.sendMessage("${prefix}§c§l手にアイテムを持ってください！")
                            return
                        }
                        openMaterialMenu(p,0,p.inventory.itemInMainHand.type)
                    }
                }
            }

            BASIC_MENU ->{

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

                        ItemData.buy(p,itemID,orderID){
                            Bukkit.getScheduler().runTask(plugin, Runnable { openOPMenu(p,page) })
                        }


                        return
                    }
                }
            }

            QUERY_MENU ->{

                when(id){
                    "prev" ->{ openSearchMenu(p,page-1,menuData.search!!) }

                    "next" ->{ openSearchMenu(p,page+1,menuData.search!!) }

                    "reload" ->{ openSearchMenu(p,page,menuData.search!!) }

                    else ->{
                        val meta = item.itemMeta!!

                        val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:-1
                        val itemID = meta.persistentDataContainer[NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER]?:-1

                        if (orderID == -1)return

                        if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY){
                            es.execute { showItemList(p,itemID) }
                            return
                        }

                        ItemData.buy(p,itemID,orderID){
                            Bukkit.getScheduler().runTask(plugin, Runnable { openSearchMenu(p,page,menuData.search?:return@Runnable) })
                        }


                        return
                    }
                }
            }

            MATERIAL_MENU ->{

                when(id){
                    "prev" ->{ openMaterialMenu(p,page-1,menuData.material!!) }

                    "next" ->{ openMaterialMenu(p,page+1,menuData.material!!) }

                    "reload" ->{ openMaterialMenu(p,page,menuData.material!!) }

                    else ->{
                        val meta = item.itemMeta!!

                        val orderID = meta.persistentDataContainer[NamespacedKey(plugin,"order_id"), PersistentDataType.INTEGER]?:-1
                        val itemID = meta.persistentDataContainer[NamespacedKey(plugin,"item_id"), PersistentDataType.INTEGER]?:-1

                        if (orderID == -1)return

                        if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY){
                            es.execute { showItemList(p,itemID) }
                            return
                        }

                        ItemData.buy(p,itemID,orderID){
                            Bukkit.getScheduler().runTask(plugin, Runnable { openMaterialMenu(p,page,menuData.material!!) })
                        }
                        return
                    }
                }
            }
        }

    }

    fun openSearchMenu(p: Player, page : Int, query : String){
        val keys = Sort.nameSort(query,orderMap.keys().toList())

        val inv = generateSortInventory(Bukkit.createInventory(null,54, QUERY_MENU),page,keys)

        p.openInventory(inv)

        val oldData = peekStack(p)
        if (oldData!=null && oldData.name == QUERY_MENU){ popStack(p) }

        val data = MenuData()
        data.name = QUERY_MENU
        data.page = page
        data.search = query
        data.menu= Menu { openSearchMenu(p,page, query) }

        pushStack(p,data)
    }

    fun openMaterialMenu(p: Player, page : Int, material: Material){

        val keys = Sort.materialSort(material,orderMap.keys().toList())

        val inv = generateSortInventory(Bukkit.createInventory(null,54, MATERIAL_MENU),page,keys)

        p.openInventory(inv)

        val oldData = peekStack(p)
        if (oldData!=null && oldData.name == MATERIAL_MENU){ popStack(p) }

        val data = MenuData()
        data.name = MATERIAL_MENU
        data.page = page
        data.material = material
        data.menu= Menu { openMaterialMenu(p,page,material) }

        pushStack(p,data)
    }


    private fun pushStack(p:Player, data:MenuData){
        val stack = menuStack[p]?: Stack()
        stack.push(data)
        menuStack[p] = stack
    }

    private fun popStack(p:Player):MenuData?{
        val stack = menuStack[p]?:return null
        if (stack.isEmpty())return null
        val id = stack.pop()
        menuStack[p] = stack
        return id
    }

    private fun peekStack(p: Player): MenuData? {
        val stack = menuStack[p] ?: return null
        if (stack.isEmpty()) return null
        //        menuStack[p] = stack
        return stack.peek()
    }

    @EventHandler
    fun closeInventory(e:InventoryCloseEvent){

        val p = e.player as Player

        if (e.reason != InventoryCloseEvent.Reason.PLAYER)return

        popStack(p)
        val menu = (popStack(p)?:return).menu

        Bukkit.getScheduler().runTask(plugin, Runnable { menu.menuFunc() })
    }

    fun interface Menu{ fun menuFunc() }

    fun reloadItem(): ItemStack {
        val reloadItem = ItemStack(Material.COMPASS)
        val reloadMeta = reloadItem.itemMeta
        reloadMeta.displayName(text("§6§lリロード"))
        setID(reloadMeta,"reload")
        reloadItem.itemMeta = reloadMeta
        return reloadItem
    }

    fun prevItem(): ItemStack {
        val prevItem = ItemStack(Material.PAPER)
        val prevMeta = prevItem.itemMeta
        prevMeta.displayName(text("§6§l前ページへ"))
        setID(prevMeta,"prev")
        prevItem.itemMeta = prevMeta
        return prevItem
    }

    fun nextItem(): ItemStack {
        val nextItem = ItemStack(Material.PAPER)
        val nextMeta = nextItem.itemMeta
        nextMeta.displayName(text("§6§l次ページへ"))
        setID(nextMeta,"next")
        nextItem.itemMeta = nextMeta
        return nextItem
    }

    fun addedInfoItem(item: ItemStack, data : Data?): ItemStack {
        val lore = item.lore?: mutableListOf()

        if (data==null){

            lore.add("§c§l売り切れ")

            item.lore = lore

            return item
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
        return item
    }

    fun generateSortInventory(inv : Inventory, page: Int , keys : List<Int>): Inventory {


        var inc = 0

        while (inv.getItem(44) ==null){

            if (keys.size <= inc+page*45)break

            val itemID = keys[inc+page*45]

            inc ++

            val data = orderMap[itemID]
            val item = itemDictionary[itemID]?.clone()?:continue

            inv.addItem(addedInfoItem(item,data))

        }

        inv.setItem(49,reloadItem())


        if (page!=0){
            inv.setItem(45,prevItem())
        }

        if (inc >=44){
            inv.setItem(53,nextItem())
        }

        return inv
    }
}