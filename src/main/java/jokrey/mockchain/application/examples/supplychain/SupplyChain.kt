package jokrey.mockchain.application.examples.supplychain

import jokrey.mockchain.Mockchain
import jokrey.mockchain.squash.SequenceSquashHandler
import jokrey.mockchain.squash.SquashRejectedException
import jokrey.mockchain.storage_classes.*
import jokrey.mockchain.transaction_transformation.ManyTransactionHandler
import jokrey.mockchain.transaction_transformation.SerializedTransactionHandler
import jokrey.mockchain.visualization.VisualizableApp
import jokrey.mockchain.visualization.util.UserAuthHelper
import jokrey.utilities.encoder.as_union.li.bytes.LIbae
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.serialization.LIObjectEncoderFull
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer
import java.util.*
import kotlin.collections.ArrayList

/**
 * Basic idea:
 * Say you have a company that does business with other companies.
 * Most specifically it ships a product, or part of a product or is interested in seeing where a product it gets is.
 * Every company that is part of that supply chain then sends their public key over a trusted connection to the supplier - the supplier then creates a new route for that product
 *     with every intermediate company that handles the product in that route.
 * Afterwards whenever they receive, delay or ship the product they push that information to the chain.
 *
 * Anybody can now track where a product in a supply chain is and what it's current status is.
 *     At this point it is desirable to keep all the latest product information.
 *     Which is why Sequence are used here
 *
 * After a product has reached the consumer of it's supply chain, a new product can be shipped from the supplier.
 * At this point the previous, exact product locations become less interesting. Interesting to be placed in a special history container, which eliminates some information.
 * Using sequences this state of diminished interest is reflected with a squash, that may be executed from now on.
 *
 * @author jokrey
 */

class SupplyChain(private val totalNumberOfWayPointsToGenerate:Int = 10, private val wayPointsPerRouteLimit:Int = 5) : VisualizableApp {
    private val tracks = HashMap<String, SupplyRoute>()
    private val wpeListeners = ArrayList<WPEListener>()
    private val routeListeners = ArrayList<Pair<String, (SupplyRoute, Transaction) -> Unit>>()

    override fun verify(instance: Mockchain, blockCreatorIdentity:ByteArray, vararg txs: Transaction): List<Pair<Transaction, RejectionReason.APP_VERIFY>> {
        val denied = ArrayList<Pair<Transaction, RejectionReason.APP_VERIFY>>()

        //does NOT do virtual changes yet, so only one tx in any sequence will ever be accepted - this is a todo
        SupplyChainTxHandler(
                { swpe, tx ->
                    val wpe = swpe.wpe
                    val trackAtRoute = tracks[wpe.route]
                    if (tx.bDependencies.size != 1)
                        denied.add(Pair(tx, RejectionReason.APP_VERIFY("is swpe and dependencies.size != 1")))
                    else if(trackAtRoute == null || !trackAtRoute.authenticateWayPointEvent(swpe, tx.bDependencies[0].type == DependencyType.SEQUENCE_END))
                        denied.add(Pair(tx, RejectionReason.APP_VERIFY("way point authentication failed")))
                },
                { sr, tx ->
                    if (tx.bDependencies.isNotEmpty())
                        denied.add(Pair(tx, RejectionReason.APP_VERIFY("sr has dependency")))
                    else if(tracks.containsKey(sr.name))
                        denied.add(Pair(tx, RejectionReason.APP_VERIFY("sr track name already taken")))
                }).handleAll(txs.toList()) {LOG.finest("could not be parsed");denied.add(Pair(it, RejectionReason.APP_VERIFY("Parse error")))}

        return denied
    }

    override fun newBlock(instance: Mockchain, block: Block, newTransactions: List<Transaction>) {
        SupplyChainTxHandler(
                { swpe, tx ->
                    val wpe = swpe.wpe
                    wpeListeners.filter { it.routeName == wpe.route && it.type == wpe.type && (it.wayPoint == -1 || it.wayPoint == wpe.wayPoint) }.forEach { it.call(it.routeName, it.wayPoint, it.type, tx) }
                    tracks.getValue(wpe.route).commitWayPointEvent(swpe.wpe)
                },
                { sr, tx ->
                    tracks[sr.name] = sr
                    routeListeners.filter { it.first == sr.name }.forEach { it.second(sr, tx) }
                }).handleAll(newTransactions)
    }

    override fun txRemoved(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, txWasPersisted: Boolean) {}
    override fun txAltered(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, newHash: TransactionHash, newTx: Transaction, txWasPersisted: Boolean) {
        SupplyChainTxHandler(
                { swpe, tx ->
                    val wpe = swpe.wpe
                    wpeListeners.filter { it.routeName == wpe.route && it.type == wpe.type && (it.wayPoint == -1 || it.wayPoint == wpe.wayPoint) }.forEach { it.call(it.routeName, it.wayPoint, it.type, tx) }
                },
                { sr, tx ->
                    routeListeners.filter { it.first == sr.name }.forEach { it.second(sr, tx) }
                })
                .handle(newTx)

    }
    override fun txRejected(instance: Mockchain, oldHash: TransactionHash, oldTx: Transaction, reason: RejectionReason) {
        //todo handle - i.e. inform routes to roll back
        LOG.finest("SupplyChain.txRejected")
        LOG.finest("oldHash = [${oldHash}], oldTx = [${oldTx}], reason = [${reason}]")
    }

    override fun getSequenceSquashHandler(): SequenceSquashHandler = {list ->
        if(list.size < 2) throw SquashRejectedException("this should never happen, this list is constrained to have more than 1 element")

        var virtual: SupplyRoute? = null
        LOG.finest("SequenceSquashHandler: list: ${list.map { it.toList() }}")

        val handler = SupplyChainTxHandler(
                { swpe, _: Transaction? -> LOG.finest(swpe.wpe.toString());virtual!!.commitWayPointEvent(swpe.wpe) }, //loosing signatures here.. But that is fine, because after they have been written into the chain we don't need them (and previously we have checked them)
                //    so why even persists them at all? Well.. A Transaction goes directly into the blockchain, this is required for replay
                //    now we loose that exact replay functionality and the ability to after check, so maybe (todo) we should keep signatures
                { sr, _: Transaction? -> LOG.finest(sr.toString());virtual = sr }) //first one is always a supply route(either creation, or previous minimization, it does not matter - all following ones are swpe's

        handler.handleAllTxs(list)

        LOG.finest("sequence squash result: $virtual")

        if(virtual!!.current.currentWayPoint != 0)
            throw SquashRejectedException("inconsistent state reached - illegal/early sequence end dependency allowed")

        handler.serialize(virtual)
    }


    override fun shortDescriptor(tx: Transaction): String {
        val builder = StringBuilder()
        SupplyChainTxHandler(
                { swpe, _ -> builder.append("${swpe.wpe.route}-${swpe.wpe.companyName}(${swpe.wpe.stateStr()}) - ${swpe.wpe.quantity}") },
                { sr, _ -> builder.append("+${sr.name}(${sr.wayPointAuthenticatorsPublicKeys.size})(${sr.history.size}, ${sr.current.currentWayPoint})") })
                .handle(tx)
        return builder.toString()
    }
    override fun longDescriptor(tx: Transaction): String {
        val builder = StringBuilder()
        SupplyChainTxHandler(
                { swpe, _ -> builder.append("Reached company(${swpe.wpe.companyName}) at way point of route(${swpe.wpe.route}) - (${swpe.wpe.stateStr()}) - ${swpe.wpe.quantity} - notes: ${swpe.wpe.notes}") },
                { sr, _ -> builder.append("[ROUTE ${sr.name}(${sr.history.size}): ${sr.wayPointAuthenticatorsPublicKeys.size} - ${sr.wayPointAuthenticatorsPublicKeys.joinToString { Arrays.toString(it) }}]") })
                .handle(tx)
        return builder.toString()
    }
    override fun shortStateDescriptor() =
            tracks.entries.sortedBy { it.key }.joinToString { "${it.key}(${it.value.history.size}, ${it.value.current.currentWayPoint})" }
    override fun exhaustiveStateDescriptor() = tracks.entries.sortedBy { it.key }.joinToString {it.toString()}



    private val wayPointsToSimulate = ArrayList<SupplyRouteMember>(totalNumberOfWayPointsToGenerate)
    private var lastStep = 0L
    override fun next(instance: Mockchain, step: Long, random: Random): Optional<Transaction> {
        //this bypasses the traditional return what should be added to the chain, by directly adding to the chain
        //    this is legal.

        if(wayPointsToSimulate.size < totalNumberOfWayPointsToGenerate) {
            val generateSize = (random.nextInt((wayPointsPerRouteLimit+1) - 2) + 2).
                    coerceAtMost(totalNumberOfWayPointsToGenerate - wayPointsToSimulate.size)
            val routeName = "route-" + wayPointsToSimulate.size
            instance.commitToMemPool(generateRoute(routeName, generateSize, random.nextInt(10000) + 20L))
        }

        wayPointsToSimulate.forEach {
            val new = it.next(step)
            if(new != null)
                instance.commitToMemPool(new)
        }

        lastStep = step
        return Optional.empty()
    }

    private fun generateRoute(routeName: String, generateSize: Int, quantityToSupply: Long): Transaction {
        //rather slow, because it generates secure rsa keys
        val generated = Array(generateSize) {
            when (it) {
                0 -> Supplier(routeName, generateSize, "company-$it", quantityToSupply, this)
                generateSize-1 -> Consumer(routeName, generateSize, "company-$it", this)
                else -> SupplyRouteMember(routeName, it, generateSize, "company-$it", this)
            }
        }

        val newTx = (generated[0] as Supplier).createNewRoute(*generated.drop(1).toTypedArray())

        wayPointsToSimulate.addAll(generated)

        return newTx
    }


    fun registerRouteCreationListener(routeName: String, callback: (SupplyRoute, Transaction) -> Unit) =routeListeners.add(Pair(routeName, callback))
    fun registerWPEListener(listener: WPEListener) = wpeListeners.add(listener)


    override fun getEqualFreshCreator(): () -> VisualizableApp = { SupplyChain(totalNumberOfWayPointsToGenerate, wayPointsPerRouteLimit) }
    override fun getCreatorParamNames() = arrayOf("totalNumberOfWayPointsToGenerate (int)", "wayPointsPerRouteLimit (int)")
    override fun getCurrentParamContentForEqualCreation() = arrayOf(totalNumberOfWayPointsToGenerate.toString(), wayPointsPerRouteLimit.toString())
    override fun createNewInstance(vararg params: String) = SupplyChain(params[0].toInt(), params[1].toInt())
    override fun createTxFrom(input: String): Transaction {
        //this is fine, because neither company nor route can contain spaces
        if (" received " in input || " delayed " in input || " shipped " in input) {
            val (company, op, quantity, route) = input.replaceFirst(" ", "^").replaceFirst(" ", "^").replace(" ", "").split("^", "at")
            val srm = wayPointsToSimulate.filter { it.routeName == route && it.companyName == company }[0]
            return when (op) {
                "received" -> srm.publishShipmentReceived(quantity.toLong(), lastStep)
                "delayed" -> srm.publishShipmentDelayed(quantity.toLong(), lastStep)
                "shipped" -> srm.publishShipmentShipped(quantity.toLong(), lastStep)
                else -> throw UnsupportedOperationException("op($op) not known - try shipped, delayed, received")
            }
        } else if(input.startsWith("new ")) {
            val (routeName, numberOfWaypointsS) = input.substring(4).split(" with ")
            val numberOfWayPoints = numberOfWaypointsS.replace(" stops", "").toInt()
            val quantityToSupply = 1111L//Random().nextInt(10000) + 20L
            return generateRoute(routeName, numberOfWayPoints, quantityToSupply)
        } else {
            throw UnsupportedOperationException("try either: \"new routeName with 3 stops\"\nor: \"company <shipped/delayed/received> 1236 at route\"")
        }
    }
}
data class WPEListener(val routeName:String, val wayPoint: Int, val type: WPType, val call: (String, Int, WPType, Transaction)->Unit)

class SupplyChainTxHandler(wpeHandler: (SignedWayPointEvent, Transaction) -> Unit = { _, _->}, routeHandler: (SupplyRoute, Transaction) -> Unit = { _, _->})
    : ManyTransactionHandler(SerializedTransactionHandler(wpeHandler, SignedWayPointEvent::class.java,
        { s ->
            SignedWayPointEvent(fromWpBytes(s), null, LITagBytesEncoder(s).getEntry("signature"))
        },
        {d ->
            val encoder = LITagBytesEncoder(d.wpe.toBytes())
            encoder.addEntry("signature", d.signature)
            encoder.encoded
        }
        ), SerializedTransactionHandler(routeHandler,
        SupplyRoute::class.java,
        {s ->
            val decoder = LITagBytesEncoder(s)
            val route = decoder.getEntryT("route", String::class.java)
            val authenticators = decoder.getEntryT("authenticators", Array<ByteArray>::class.java)
            val rebuildSupplyRoute = SupplyRoute(route, authenticators)
            rebuildSupplyRoute.history.addAll(decodeHistory(decoder.getEntry("history")))
            rebuildSupplyRoute.current = rebuildCurrentShipment(authenticators.size, decoder.getEntry("current"))
            rebuildSupplyRoute
        },
        {d ->
            LITagBytesEncoder().addEntryT_nocheck("type", "minimized")
                .addEntryT_nocheck("route", d.name)
                .addEntryT_nocheck("authenticators", d.wayPointAuthenticatorsPublicKeys)
                .addEntryT_nocheck("history", encodeHistory(d.history))
                .addEntryT_nocheck("current", d.current.eventsPerWayPoint.flatMap { it }.map { it.toBytes() }.fold(byteArrayOf()) { acc, bytes -> acc + LIbae().encode(bytes).encoded }).encodedBytes
        }))

fun rebuildCurrentShipment(numberOfWayPoints: Int, bytes: ByteArray) : Shipment {
    val decoded = Shipment(numberOfWayPoints)

    val topDecoder = LIbae(bytes)
    for(event in topDecoder.decodeAll())
        decoded.commitWayPointEventReached(LIObjectEncoderFull.deserialize(event, WayPointEvent::class.java))

    return decoded
}





class SupplyRoute(val name: String, internal val wayPointAuthenticatorsPublicKeys: Array<ByteArray>) {
    internal val history: MutableList<HistoricShipment> = ArrayList()
    var current = Shipment(wayPointAuthenticatorsPublicKeys.size)
        internal set

    fun authenticateWayPointEvent(swpe: SignedWayPointEvent, assertEnd: Boolean): Boolean {
        if(swpe.wpe.route != name) return false

        val wayPointAuthenticatorPubKey = wayPointAuthenticatorsPublicKeys[current.currentWayPoint]

        return current.authenticateWayPointEvent(swpe.wpe, assertEnd) && UserAuthHelper.verify(swpe.wpe.toBytes(), swpe.signature, wayPointAuthenticatorPubKey)
    }

    fun commitWayPointEvent(wpe: WayPointEvent) {
        val wasLast = current.commitWayPointEventReached(wpe)

        LOG.finest("committed wpe = ${wpe}")
        if(wasLast) {
            history.add(current.history())
            current = Shipment(wayPointAuthenticatorsPublicKeys.size)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SupplyRoute
        return name == other.name && wayPointAuthenticatorsPublicKeys.contentDeepEquals(other.wayPointAuthenticatorsPublicKeys) && history == other.history && current == other.current
    }
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + wayPointAuthenticatorsPublicKeys.contentDeepHashCode()
        result = 31 * result + history.hashCode()
        result = 31 * result + current.hashCode()
        return result
    }
    override fun toString(): String {
        return "SupplyRoute(name='$name', wayPointAuthenticatorsPublicKeys=${wayPointAuthenticatorsPublicKeys.joinToString { Arrays.toString(it) }}, history=$history, current=$current)"
    }
}

data class HistoricShipment(val totalTime: Long, val quantity: Long)

class Shipment(private val numberOfWayPoints: Int) {
    internal val eventsPerWayPoint = Array<ArrayList<WayPointEvent>>(numberOfWayPoints) {ArrayList()}
    var currentWayPoint = 0
        private set

    fun authenticateWayPointEvent(wpe: WayPointEvent, assertEnd: Boolean): Boolean {
        if(assertEnd && currentWayPoint+1 != numberOfWayPoints)
            return false
        if(currentWayPoint >= numberOfWayPoints)
            return false
        if(wpe.wayPoint != currentWayPoint) //order is correct, no event from an old WayPoint or a bypass
            return false
        else {
            if(currentWayPoint==0) {
                if (wpe.type == WPType.ENTERED)
                    return false
            } else {
                //require entered to come first and only first
                // this implies that entered+delayed*+left is the only legal way
                // delayed cannot come after left, because left automatically shifts the way point index
                if (eventsPerWayPoint[currentWayPoint].isNotEmpty() && wpe.type == WPType.ENTERED) {
                    return false
                } else if (eventsPerWayPoint[currentWayPoint].isEmpty() && wpe.type != WPType.ENTERED)
                    return false
            }
        }
        return true
    }
    fun commitWayPointEventReached(wpe: WayPointEvent): Boolean {
        eventsPerWayPoint[currentWayPoint].add(wpe)
        return when {
            wpe.type == WPType.LEFT -> {
                currentWayPoint++
                false
            }
            wpe.type == WPType.ENTERED -> {
                currentWayPoint+1 >= numberOfWayPoints //last wp is a consumer, and entering it marks the end of a route
            }
            wpe.type == WPType.CANCELED -> {
                currentWayPoint = eventsPerWayPoint.size
                true
            }
            else -> false
        }
    }

    fun history(): HistoricShipment {
        return if(currentWayPoint+1 < numberOfWayPoints)
            throw IllegalStateException("not yet finished, cannot create history")
        else {
            val first = eventsPerWayPoint.first().first()
            val last = eventsPerWayPoint.last().last()
            val totalTime = last.timestamp - first.timestamp
            val quantity = if(last.type == WPType.CANCELED) -1 else last.quantity
            HistoricShipment(totalTime, quantity)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Shipment
        return numberOfWayPoints == other.numberOfWayPoints && eventsPerWayPoint.contentEquals(other.eventsPerWayPoint) && currentWayPoint == other.currentWayPoint
    }
    override fun hashCode(): Int {
        var result = numberOfWayPoints
        result = 31 * result + eventsPerWayPoint.hashCode()
        result = 31 * result + currentWayPoint
        return result
    }
    override fun toString(): String {
        return "Shipment(numberOfWayPoints=$numberOfWayPoints, eventsPerWayPoint=${eventsPerWayPoint.flatMap { it }}, currentWayPoint=$currentWayPoint)"
    }
}

class SignedWayPointEvent(val wpe: WayPointEvent, authenticatorPrivateKey: ByteArray? = null, val signature: ByteArray = UserAuthHelper.sign(wpe.toBytes(), authenticatorPrivateKey)) {
    override fun hashCode(): Int {
        var result = wpe.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SignedWayPointEvent
        return wpe == other.wpe && signature.contentEquals(other.signature)
    }
}

data class WayPointEvent(val route:String, val wayPoint: Int, val companyName: String, val quantity: Long, val notes:String, val timestamp: Long, val type: WPType) {
    fun toBytes():ByteArray = LITagBytesEncoder().addEntryT_nocheck("route", route).addEntryT_nocheck("company", companyName).addEntryT_nocheck("timestamp", timestamp).addEntryT_nocheck("quantity", quantity).addEntryT_nocheck("wayPoint", wayPoint).addEntryT_nocheck("notes", notes).addEntryT_nocheck("type", type.name).encodedBytes
    fun stateStr() = when(type) {
        WPType.ENTERED -> "->$wayPoint"
        WPType.DELAYED -> "-$wayPoint-"
        WPType.LEFT -> "$wayPoint->"
        WPType.CANCELED -> "$wayPoint-XX"
    }
}

fun fromWpBytes(bytes: ByteArray) : WayPointEvent {
    val decoder = LITagBytesEncoder(bytes)
    return WayPointEvent(
            decoder.getEntryT("route", String::class.java),
            decoder.getEntryT("wayPoint", Int::class.java),
            decoder.getEntryT("company", String::class.java),
            decoder.getEntryT("quantity", Long::class.java),
            decoder.getEntryT("notes", String::class.java),
            decoder.getEntryT("timestamp", Long::class.java),
            WPType.valueOf(decoder.getEntryT("type", String::class.java)))
}

enum class WPType {
    ENTERED,
    DELAYED,
    LEFT,
    CANCELED
}

private fun encodeHistory(history: MutableList<HistoricShipment>) =
        history.map { LITypeToBytesTransformer().transform(longArrayOf(it.totalTime, it.quantity)) }.fold(byteArrayOf()) { acc, bytes -> acc + LIbae().encode(bytes).encoded }
private fun decodeHistory(bytes: ByteArray) : List<HistoricShipment> {
    val decoded = ArrayList<HistoricShipment>()

    val topDecoder = LIbae(bytes)
    for(item in topDecoder.decodeAll()) {
        val decodedRawItem = LITypeToBytesTransformer().detransform_longs(item)
        decoded.add(HistoricShipment(decodedRawItem[0], decodedRawItem[1]))
    }

    return decoded
}