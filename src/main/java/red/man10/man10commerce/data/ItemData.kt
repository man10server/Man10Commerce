package red.man10.man10commerce.data

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class ItemData(val seller : UUID) {

    var id = 0
    var sellItem : ItemStack? = null
    var price : Double = 0.0
    var date : Date? = null


    //商品を出品する
    fun push():Boolean{


        return false
    }

    //商品を購入する
    fun buy(buyer:Player):Boolean{


        return false
    }

    //出品期間が過ぎたかどうか
    fun isFinishTime():Boolean{


        return false
    }

    //出品を取り下げる
    fun close():Boolean{

        return false
    }

    companion object{

        fun getItemList(page:Int):MutableList<ItemData>{
            val list = mutableListOf<ItemData>()

            return list
        }


    }

}