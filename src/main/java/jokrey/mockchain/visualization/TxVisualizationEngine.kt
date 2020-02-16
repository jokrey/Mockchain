package jokrey.mockchain.visualization

import jokrey.mockchain.Mockchain
import jokrey.mockchain.application.EmptyTransactionGenerator
import jokrey.mockchain.application.TransactionGenerator
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.utilities.animation.engine.TickEngine
import jokrey.utilities.animation.pipeline.AnimationObject
import jokrey.utilities.animation.pipeline.AnimationObjectDrawer
import jokrey.utilities.animation.pipeline.AnimationPipeline
import jokrey.utilities.animation.util.AEColor
import jokrey.utilities.animation.util.AEPoint
import jokrey.utilities.animation.util.AERect
import jokrey.utilities.animation.util.AESize
import jokrey.utilities.debug_analysis_helper.TimeDiffMarker
import jokrey.mockchain.squash.fillLevelsFor
import jokrey.mockchain.storage_classes.*
import java.util.*
import kotlin.collections.HashMap

/**
 * The engine to visualize a blockchain.
 * Will not work for blockchains of any size for obvious RAM capability issues.
 *
 * Can be used to procedurally generate transaction.
 *
 * Examples of how to use the engine can be found in {@link VisualizationStarter}
 */
open class TxVisualizationEngine(private val instance: Mockchain, private val txGen:TransactionGenerator = EmptyTransactionGenerator(), private val appDisplay:ApplicationDisplay, var consensusEveryNTick:Int) : TickEngine() {
    override fun initiate() {}
    override fun calculateTickImpl() {
        println("tick($currentTick)")
        val generatedTransaction = txGen.next(instance, currentTick, Random())
        if(generatedTransaction.isPresent) {
            try {
                instance.commitToMemPool(generatedTransaction.get())
            } catch (ex: java.lang.IllegalArgumentException) {
                ex.printStackTrace()
            }
        }

        if(instance.consensus is ManualConsensusAlgorithm) {
            if (consensusEveryNTick < 0) {
                if (Random().nextInt(-consensusEveryNTick) == 0)
                    instance.consensus.performConsensusRound(false)
            } else if (currentTick % consensusEveryNTick == 0L)
                instance.consensus.performConsensusRound(false)
        }
    }
    override fun getTicksPerSecond(): Int {
        return 1
    }

    private var gridWidth = 1.0
    private var gridHeight = 1.0
    private fun setBlocksToDisplay(chainBlocks: Array<Block>, memPoolHashes: Array<TransactionHash>) {
        val blocks = (chainBlocks + Block(null, Proof(ByteArray(0)), memPoolHashes))
        val resolver = instance.memPool.combineWith(instance.chain)

        clearObjects()
        gridHeight = 1.0
        gridWidth = 0.0
        if(blocks.isNotEmpty()) {
            try {
                val alreadyPainted = HashMap<Transaction, TransactionDisplayObject>()

                for((i, block) in blocks.withIndex()) {
                    val last = i == blocks.lastIndex
                    val blockStartX = gridWidth
                    if(block.isEmpty) {
                        if(last)
                            gridWidth+=1
                    } else {
                        try {
                            //levels will NOT contain previous - and previously calculated - data!!! NOT a to do
                            var levels = fillLevelsFor(block.map { resolver[it] })
                            levels = levels.toList().sortedBy { (_, value) -> value }.toMap()

                            var yCounter = 0.75

//                            alreadyPainted.clear()   find another way to still show transactions that have the same contents - HASH EQUALITY PROBLEM (NOW NO LONGER REQUIRED; BECAUSE MEMPOOL DOES HASH CHECK)
                            for (entry in levels.entries.reversed()) {
                                //first element has highest level - i.e should be the earliest transaction (or one of them) || i.e the most left drawn element

                                val tx = entry.key
//                                alreadyPainted.remove(tx)
                                if (!alreadyPainted.contains(tx)) {
                                    yCounter = 1.0 + paintSequence(tx, levels, alreadyPainted, gridWidth, yCounter, last)
                                }
                            }

                            if (yCounter > gridHeight)
                                gridHeight = yCounter
                            gridWidth += levels.values.max()!!
                        } catch (ex: IllegalStateException) {
                            if(last) { //i.e. error occurred in mempool && i.e. error transaction in mempool
                                gridWidth+=1
                            } else {
                                throw ex
                            }
                        }

                        allObjects.add(0, AnimationObject(blockStartX + (gridWidth-blockStartX) / 2 - 0.25, 0.0, 0.5, 0.5, AnimationObject.RECT, object : AnimationObjectDrawer() {
                            override fun canDraw(o: AnimationObject?, param: Any?) = false
                            override fun draw(o: AnimationObject?, pipeline: AnimationPipeline?, param: Any?) {
                                if(last)
                                    pipeline!!.drawer.drawString(AEColor.RED, "M", pipeline.getDrawBoundsFor(o))
                                else
                                    pipeline!!.drawer.drawString(AEColor.BLUE, i.toString(), pipeline.getDrawBoundsFor(o))
                            }
                        }))
                        if(!last)
                            initiateNewObject(AnimationObject(gridWidth, 0.0, gridWidth, Double.MAX_VALUE, AnimationObject.LINE, object : AnimationObjectDrawer() {
                                override fun canDraw(o: AnimationObject?, param: Any?) = false
                                override fun draw(o: AnimationObject, pipeline: AnimationPipeline, param: Any?) {
                                    pipeline.drawer.drawLine(AEColor.BLUE, pipeline.convertToPixelPoint(AEPoint(o.x, o.y)), pipeline.convertToPixelPoint(AEPoint(o.w, gridHeight)))
                                }
                            }))
                    }
                }

                TimeDiffMarker.println_setMark("untangling")
                //todo the following most basic attempt at untangling looks like it has a terrible algorithmic complexity( n^2 In the best case)
                for(ao in allObjects) {
                    if(ao is TransactionDisplayObject) {
                        while (allObjects.any {
                                    when (it) {
                                        is TransactionDisplayObject -> ao !== it && AnimationObject.distance(ao.mid, it.mid) < ao.w/2
                                        is EdgeDisplayObject -> ao !== it.from && ao !== it.to && AnimationObject.distance(ao.mid, it) < ao.w/2
                                        else -> false
                                    }
                                } ) {
                            ao.y++
                            if (ao.y+ao.w > gridHeight)
                                gridHeight = ao.y+ao.w
                        }
                    }
                }
                TimeDiffMarker.println("untangling")

            } catch (ex: IllegalArgumentException) {
                clearObjects()
                throw ex
            }
        }
    }


    fun recalculateTransactionDisplay() {
        setBlocksToDisplay(instance.chain.getBlocks(), instance.memPool.getTransactionHashes().toTypedArray())
    }
    private fun paintSequence(tx:Transaction, levels:Map<Transaction, Int>, alreadyPainted:MutableMap<Transaction, TransactionDisplayObject>, xOffset: Double, yCounterAtStart:Double, txIsMemPool: Boolean) : Double {
        val resolver = instance.memPool.combineWith(instance.chain)

        var yCounter = yCounterAtStart
        if(!alreadyPainted.contains(tx)) {

            val txLevel = levels[tx]
            if (txLevel != null) {
                val x = txLevel.toDouble() - 0.75
                val y = yCounter + 0.25
                val created = TransactionDisplayObject(x + xOffset, y, 0.5, 0.5, tx, txIsMemPool)
                initiateNewObject(created)
                alreadyPainted[tx] = created

                for ((dep_txp, deps) in tx.bDependencies.groupBy { it.txp }) {
                    val resolvedDepTx = resolver.getUnsure(dep_txp)
                    if (resolvedDepTx != null) {
                        yCounter = paintSequence(resolvedDepTx, levels, alreadyPainted, xOffset, yCounter, txIsMemPool)

                        initiateNewObject(EdgeDisplayObject(created, alreadyPainted[resolvedDepTx]!!, *deps.map { it.type }.toTypedArray()))
                    } else {
                        initiateNewObject(EdgeDisplayObject(created, TransactionDisplayObject(xOffset, y + 0.25,0.0,0.0, null, txIsMemPool)))
                    }
                }
            }

        }
        return yCounter
    }

    private fun getColorForEdgeType(type: DependencyType?):AEColor = when(type) {
        DependencyType.REPLACES -> AEColor.WHITE
        DependencyType.REPLACES_PARTIAL -> AEColor.GRAY
        DependencyType.BUILDS_UPON -> AEColor.CYAN
        DependencyType.ONE_OFF_MUTATION -> AEColor.CRIMSON
        DependencyType.SEQUENCE_PART -> AEColor.LIGHT_BLUE
        DependencyType.SEQUENCE_END -> AEColor.BLUE
        null -> AEColor.YELLOW
    }


    override fun getVirtualBoundaries(): AESize {
        return AESize(gridWidth, gridHeight)
    }


    private var mousePosition = AEPoint(-1.0, -1.0)
    override fun locationInputChanged(mouseP: AEPoint?, mousePressed: Boolean) {
        mousePosition = mouseP!!

        for(ao in allObjects)
            if(ao is EdgeDisplayObject) {
                ao.hover = false
            } else if(ao is TransactionDisplayObject) {
                ao.hover = false
            }

        for(ao in allObjects)
            if(ao is EdgeDisplayObject) {
                if(AnimationObject.distance(mousePosition, ao) < 0.1) {
                    ao.hover = true
                    ao.from.hover = true
                    ao.to.hover = true
                }
            } else if(ao is TransactionDisplayObject) {
                if(AnimationObject.distance(mousePosition, ao.mid) < ao.w/2) {
                    ao.hover = true
                    allObjects.forEach {
                        if(it is EdgeDisplayObject && (it.to===ao || it.from ===ao))
                            it.hover=true
                    }
                }
            }
    }

    override fun mouseClicked(mouseCode: Int) {
        println("mouseCode: $mouseCode")
        when(mouseCode) {
            3 -> {} //right   click
            2 -> {} //middle click
            1 -> {  //left  click
                for(ao in allObjects) {
                    if(AnimationObject.distance(mousePosition, ao.mid) < ao.w/2 && ao is TransactionDisplayObject) {
                        val tx = ao.tx
                        if(tx!=null) {
                            println("Clicked on: $tx")
                            println("With raw data: " + Arrays.toString(tx.content))
                            println("With parsed data: " + appDisplay.longDescriptor(tx))
                        }
                    }
                }
            }
        }
    }


    inner class TransactionDisplayObject(x: Double, y: Double, w: Double, h: Double, val tx:Transaction?, val isInMemPool: Boolean) : AnimationObject(x, y, w, h, OVAL) {
        var hover = false
        init {
            drawParam = object: AnimationObjectDrawer() {
                override fun canDraw(o: AnimationObject?, param: Any?): Boolean = false
                override fun draw(o: AnimationObject, pipeline: AnimationPipeline, param: Any?) {
                    if(tx!=null) {
                        val at = pipeline.getDrawBoundsFor(o)
                        pipeline.drawer.fillOval(if(hover) AEColor.CRIMSON else if (isInMemPool) AEColor.RED else AEColor.BLUE, at)

                        pipeline.drawer.drawString(AEColor.CYAN, "@" + Integer.toHexString(tx.hashCode()), AERect(at.x, at.y, at.w, at.h / 4))
                        pipeline.drawer.drawString(AEColor.CYAN, appDisplay.shortDescriptor(tx), AERect(at.x, at.y + at.h * 0.75, at.w, at.h / 4))
                    }
                }
            }
        }
    }

    inner class EdgeDisplayObject(val from: TransactionDisplayObject, val to: TransactionDisplayObject, vararg edgeTypes:DependencyType?) : AnimationObject(LINE) {
        var hover = false
        init {
            drawParam = object: AnimationObjectDrawer() {
                override fun canDraw(o: AnimationObject?, param: Any?): Boolean = false
                override fun draw(o: AnimationObject, pipeline: AnimationPipeline, param: Any?) {
                    if (edgeTypes.isEmpty()) {
                        pipeline.drawer.drawLine(if (hover) AEColor.CRIMSON else getColorForEdgeType(null),
                                pipeline.convertToPixelPoint(from.mid), pipeline.convertToPixelPoint(to.mid), 5f)
                    } else {
                        //actually draw different lines, correctly
//                        val totalDistance = (from.w / 16) * edgeTypes.size
//                        val fromToAngle = AE_UTIL.getAngle(from.mid, to.mid)
//                        val toFromAngle = AE_UTIL.getAngle(to.mid, from.mid)
//                        for((index, edgeType) in edgeTypes.withIndex()) {
//                            if(index < edgeTypes.size/2) {
//                                val newFrom = AE_UTIL.getPointAtDistanceFrom(from.mid, fromToAngle + 90, (totalDistance/2) - (totalDistance/2) * index)
//                                val newTo = AE_UTIL.getPointAtDistanceFrom(to.mid, toFromAngle - 90, (totalDistance/2) - (totalDistance/2) * index)
//                                pipeline.drawer.drawLine(if (hover) AEColor.RED else getColorForEdgeType(edgeType),
//                                        pipeline.convertToPixelPoint(newFrom), pipeline.convertToPixelPoint(newTo), 2f)
//                            } else if(edgeTypes.size%2 == 1 && edgeTypes.size/2 == index) {
//                                pipeline.drawer.drawLine(if (hover) AEColor.RED else getColorForEdgeType(edgeType),
//                                        pipeline.convertToPixelPoint(from.mid), pipeline.convertToPixelPoint(to.mid), 2f)
//                            } else {
//                                val newFrom = AE_UTIL.getPointAtDistanceFrom(from.mid, fromToAngle - 90, (totalDistance/2) - (totalDistance/2) * (index - edgeTypes.size/2))
//                                val newTo = AE_UTIL.getPointAtDistanceFrom(to.mid, toFromAngle + 90, (totalDistance/2) - (totalDistance/2) * (index - edgeTypes.size/2))
//                                pipeline.drawer.drawLine(if (hover) AEColor.RED else getColorForEdgeType(edgeType),
//                                        pipeline.convertToPixelPoint(newFrom), pipeline.convertToPixelPoint(newTo), 2f)
//                            }
//                        }
                        //fake it:
                        for((index, et) in edgeTypes.withIndex()) {
                            val p1 = pipeline.convertToPixelPoint(from.mid)
                            val p2 = pipeline.convertToPixelPoint(to.mid)
                            pipeline.drawer.drawLine(if (hover) AEColor.CRIMSON else getColorForEdgeType(et),
                                    AEPoint(p1.x, p1.y+(index - edgeTypes.size/2)*2), AEPoint(p2.x, p2.y+(index - edgeTypes.size/2)*2), 2f)
                        }
                    }
                }
            }
        }

        override fun getX() = from.mid.x
        override fun getY() = from.mid.y
        override fun getW() = to.mid.x
        override fun getH() = to.mid.y
    }
}