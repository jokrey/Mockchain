package jokrey.mockchain.consensus

import jokrey.mockchain.Mockchain

/**
 * Workaround to allow users to infuse their own consensus without themselves creating chain, mempool, etc. (i.e. their own instance).
 * @author jokrey
 */
@FunctionalInterface
interface ConsensusAlgorithmCreator {
    fun create(instance: Mockchain) : ConsensusAlgorithm
}