package application.examples.supplychain

import storage_classes.Dependency
import storage_classes.DependencyType
import storage_classes.Transaction
import visualization.util.UserAuthHelper
import java.security.KeyPair
import java.util.*

/**
 * Between first and last way-point of a route. Can delay a shipment but will otherwise just receive it and send it to the next way-point.
 */
open class SupplyRouteMember(val routeName: String, internal val atWayPoint: Int, internal val totalWayPoints:Int, internal val companyName: String, app: SupplyChain) {
    internal val authenticationKeys: KeyPair = UserAuthHelper.generateKeyPair()

    internal var currentState = MemberState.WAITING
    internal var previous:Transaction? = null

    init {
        init(app)
    }

    open fun init(app: SupplyChain) {
        if(atWayPoint <= 0) throw IllegalStateException("route member cannot be first way point, switch to supplier")
        if(atWayPoint >= totalWayPoints) throw IllegalStateException("route member cannot be last way point - switch to consumer")

        val call = { _:String, wp: Int, wpt: WPType, tx: Transaction ->
            if(wpt == WPType.CANCELED)
                currentState = MemberState.WAITING
            else if(atWayPoint == wp+1) {
                currentState = MemberState.EXPECTING
                previous = tx
            } else if(atWayPoint == wp) {
                if(wpt == WPType.ENTERED || wpt == WPType.DELAYED) {
                    currentState = MemberState.HAVING
                    previous = tx
                }
            }
        }

        app.registerWPEListener(WPEListener(routeName, atWayPoint - 1, WPType.LEFT, call))
        app.registerWPEListener(WPEListener(routeName, atWayPoint, WPType.ENTERED, call))
        app.registerWPEListener(WPEListener(routeName, atWayPoint, WPType.DELAYED, call))
        currentState = MemberState.WAITING
    }


    open fun publishShipmentReceived(quantity: Long, timestamp: Long) : Transaction {
        val wpe = WayPointEvent(routeName, atWayPoint, companyName, quantity, "$companyName received $quantity at $routeName", timestamp, WPType.ENTERED)
        val signed = SignedWayPointEvent(wpe, authenticationKeys.private.encoded)

        val newTx = Transaction(SupplyChainTxHandler().serialize(signed), Dependency(previous!!.hash, DependencyType.SEQUENCE_PART))

        previous = newTx
        currentState = MemberState.HAVING
        return newTx
    }

    open fun publishShipmentDelayed(quantity: Long, timestamp: Long) : Transaction {
        val wpe = WayPointEvent(routeName, atWayPoint, companyName, quantity, "$companyName still cannot ship $quantity at $routeName yet", timestamp, WPType.DELAYED)
        val signed = SignedWayPointEvent(wpe, authenticationKeys.private.encoded)

        val newTx = Transaction(SupplyChainTxHandler().serialize(signed), Dependency(previous!!.hash, DependencyType.SEQUENCE_PART))

        previous = newTx
        currentState = MemberState.HAVING
        return newTx
    }

    open fun publishShipmentShipped(quantity: Long, timestamp: Long) : Transaction {
        val wpe = WayPointEvent(routeName, atWayPoint, companyName, quantity, "$companyName just shipped $quantity at $routeName to next waypoint", timestamp, WPType.LEFT)
        val signed = SignedWayPointEvent(wpe, authenticationKeys.private.encoded)

        val newTx = Transaction(SupplyChainTxHandler().serialize(signed), Dependency(previous!!.hash, DependencyType.SEQUENCE_PART))

        previous = null
        currentState = MemberState.WAITING
        return newTx
    }


    open fun next(timestamp: Long) : Transaction? =
        when(currentState) {
            MemberState.WAITING -> {null}
            MemberState.EXPECTING -> {
                val swpe: SignedWayPointEvent = SupplyChainTxHandler().deserialize(previous!!) as SignedWayPointEvent
                publishShipmentReceived(swpe.wpe.quantity, timestamp)
            }
            MemberState.HAVING -> {
                val quantity = (SupplyChainTxHandler().deserialize(previous!!) as SignedWayPointEvent).wpe.quantity

                if(Random().nextInt(3) == 0)
                    publishShipmentShipped(quantity, timestamp)
                else
                    publishShipmentDelayed(quantity, timestamp)
            }
        }
}

enum class MemberState {
    WAITING,
    EXPECTING,
    HAVING
}