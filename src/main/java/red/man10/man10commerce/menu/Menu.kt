package red.man10.man10commerce.menu

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Man10Commerce.Companion.prefix
import java.util.*
import java.util.concurrent.ConcurrentHashMap


/**
 * メニューの抽象クラス。新規メニューを作る際はこのクラスを継承すること。
 */
abstract class Menu(title: String,size:Int,val p:Player){

    val name = prefix+title
    val menu = Bukkit.createInventory(null,size, Component.text(name))

    companion object{
        private val menuStack = ConcurrentHashMap<Player, Stack<Menu>>()
    }

    /**
     * StackにMenuを保存
     */
    protected fun pushStack(){
        val stack = menuStack[p]?: Stack()

        //ページ切り替えだけの場合は、スタックを削除して入れ替える
        if (stack.isNotEmpty() && name == stack.peek().name){
            popStack()
        }
        stack.push(this)
        menuStack[p] = stack
    }

    /**
     * Stackの先頭から取り出し、取り出したものはStackから削除
     */
    protected fun popStack(): Menu?{
        val stack = menuStack[p]?:return null
        if (stack.isEmpty())return null
        val id = stack.pop()
        menuStack[p] = stack
        return id
    }

    /**
     * Stackの先頭を取り出すが、中身は残す
     */
    protected fun peekStack(): Menu? {
        val stack = menuStack[p] ?: return null
        if (stack.isEmpty()) return null
        return stack.peek()
    }

    /**
     * ボタンアイテムに識別子をつける
     */
    protected fun setID(meta: ItemMeta, value:String){
        meta.persistentDataContainer.set(NamespacedKey(Man10Commerce.plugin,"id"), PersistentDataType.STRING,value)
    }

    /**
     * ボタンアイテムの識別子を取得
     */
    protected fun getID(itemStack: ItemStack):String{
        return itemStack.itemMeta?.persistentDataContainer?.get(NamespacedKey(Man10Commerce.plugin,"id"), PersistentDataType.STRING)
            ?:""
    }

    /**
     * メニューを開く処理
     */
    abstract fun open()

}

//class MenuData{
//    var name = ""
//    var page = 0
//    var category : String? = ""
//    var search : String? = null
//    var material : Material? = null
//    var seller : String? = null
//    var enchantment : Pair<Enchantment,Int>? = null
//    lateinit var menu : CommerceMenu.Menu
//}