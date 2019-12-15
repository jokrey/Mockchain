package jokrey.mockchain.application.examples.supplychain

import jokrey.mockchain.storage_classes.Dependency
import jokrey.mockchain.storage_classes.DependencyType
import jokrey.mockchain.storage_classes.Transaction
import java.util.*


/**
 * First Way-Point in a route. Has the special behavior of creating the shipment.
 */
class Supplier(routeName: String, totalWayPoints:Int, companyName: String, val quantityToSupply: Long, app: SupplyChain) : SupplyRouteMember(routeName, 0, totalWayPoints, companyName, app) {
    override fun init(app: SupplyChain) {
        val call = { _: String, wp: Int, wpt: WPType, tx: Transaction ->
            if (wpt == WPType.CANCELED) {
                currentState = MemberState.HAVING
                previous = tx
            } else if (totalWayPoints == wp+1) {
                currentState = MemberState.EXPECTING
                previous = tx
            }
        }

        app.registerWPEListener(WPEListener(routeName, totalWayPoints - 1, WPType.ENTERED, call)) //when shipment enters a consumer, then send the next shipment - todo this is kinda weird
        app.registerWPEListener(WPEListener(routeName, -1, WPType.CANCELED, call)) //on a canceled shipment, send a new one - todo this is kinda weird (should be done in next, i.e. ONLY in simulation mode)
        app.registerRouteCreationListener(routeName) { _, tx ->
            previous = tx
            currentState = MemberState.HAVING
        }
        currentState = MemberState.HAVING
    }

    fun createNewRoute(vararg otherRouteMembers: SupplyRouteMember): Transaction {
        if (otherRouteMembers.any { routeName != it.routeName }) throw IllegalStateException("can only create route with members of said route")
        val newTx = SupplyChainTxHandler().makeDistinct(SupplyRoute(routeName, arrayOf(authenticationKeys.public.encoded) + otherRouteMembers.map { it.authenticationKeys.public.encoded }))
        currentState = MemberState.HAVING
        previous = newTx
        return newTx
    }

    override fun publishShipmentReceived(quantity: Long, timestamp: Long) : Transaction {
        throw IllegalStateException("publishShipmentReceived illegal for supplier")
    }

    override fun publishShipmentShipped(quantity: Long, timestamp: Long) : Transaction {
        val wpe = WayPointEvent(routeName, atWayPoint, companyName, quantity, "$companyName just shipped $quantity at $routeName to next waypoint", timestamp, WPType.LEFT)
        val signed = SignedWayPointEvent(wpe, authenticationKeys.private.encoded)

        val newTx = Transaction(SupplyChainTxHandler().serialize(signed), Dependency(previous!!.hash, DependencyType.SEQUENCE_PART))

        previous = null
        currentState = MemberState.WAITING

        return newTx
    }

    override fun next(timestamp: Long) : Transaction? =
        when(currentState) {
            MemberState.WAITING -> {null}
            MemberState.EXPECTING -> {
                currentState = MemberState.HAVING
                null
            }
            MemberState.HAVING -> {
                val quantity =
                        if (SupplyChainTxHandler().isOfType(previous!!, SupplyRoute::class.java))
                            quantityToSupply
                         else
                            (SupplyChainTxHandler().deserialize(previous!!) as SignedWayPointEvent).wpe.quantity

                if(Random().nextInt(3) == 0)
                    publishShipmentShipped(quantity, timestamp)
                else
                    publishShipmentDelayed(quantity, timestamp)
            }
        }
}