package jokrey.mockchain.application.examples.supplychain

import jokrey.mockchain.storage_classes.Dependency
import jokrey.mockchain.storage_classes.DependencyType
import jokrey.mockchain.storage_classes.Transaction

/**
 * Latest Way-Point in a route. Has the special behavior of consuming the shipment.
 */
class Consumer(routeName: String, totalWayPoints:Int, companyName: String, app: SupplyChain) : SupplyRouteMember(routeName, totalWayPoints-1, totalWayPoints, companyName, app) {
    override fun publishShipmentShipped(quantity: Long, timestamp: Long) : Transaction {
        throw IllegalStateException("publishShipmentShipped illegal for consumer")
    }
    override fun publishShipmentDelayed(quantity: Long, timestamp: Long) : Transaction {
        throw IllegalStateException("publishShipmentDelayed illegal for consumer")
    }

    override fun publishShipmentReceived(quantity: Long, timestamp: Long) : Transaction {
        val wpe = WayPointEvent(routeName, atWayPoint, companyName, quantity, "$companyName received $quantity at $routeName", timestamp, WPType.ENTERED)
        val signed = SignedWayPointEvent(wpe, authenticationKeys.private.encoded)

        val newTx = Transaction(SupplyChainTxHandler().serialize(signed), Dependency(previous!!.hash, DependencyType.SEQUENCE_END))

        //Do nothing, consume the item
        currentState = MemberState.WAITING
        previous = null

        return newTx
    }



    override fun next(timestamp: Long) : Transaction? =
        when(currentState) {
            MemberState.WAITING -> {null}
            MemberState.EXPECTING -> {
                val swpe: SignedWayPointEvent = SupplyChainTxHandler().deserialize(previous!!) as SignedWayPointEvent
                publishShipmentReceived(swpe.wpe.quantity, timestamp)
            }
            MemberState.HAVING -> {null}
        }
}