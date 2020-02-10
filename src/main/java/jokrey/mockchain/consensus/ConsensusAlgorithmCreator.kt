package jokrey.mockchain.consensus

import jokrey.mockchain.application.Application
import jokrey.mockchain.storage_classes.Chain
import jokrey.mockchain.storage_classes.MemPool

/**
 * Workaround to allow users to infuse their own consensus without themselves creating chain and mempool
 * @author jokrey
 */
@FunctionalInterface
interface ConsensusAlgorithmCreator {
    fun create(app: Application, chain: Chain, memPool: MemPool) : ConsensusAlgorithm
}